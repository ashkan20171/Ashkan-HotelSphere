package com.ashkan.hotel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** Public, same-origin guest booking portal. It talks directly to the booking and guest bounded contexts. */
public final class GuestPortalHttpService implements AutoCloseable {
    private final HttpServer server;
    private final BookingRepository bookings;
    private final GuestRepository guests;
    private final CommercialRepository commercial;
    private final BookingOperationsRepository operations;
    private final DynamicPricingEngine pricing = new DynamicPricingEngine();
    private final PostgresIdempotencyStore idempotency;
    private final GuestAuthService auth;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentWebhookRepository webhookEvents;
    private final FinancialAuditRepository financialAudit;
    private final PaymentWorkflowService paymentWorkflow;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> attempts = new ConcurrentHashMap<>();

    public GuestPortalHttpService(int port, String bookingUrl, String guestUrl, String user, String password, int pool) throws IOException {
        idempotency = new PostgresIdempotencyStore(bookingUrl, user, password);
        String otpSecret=System.getenv().getOrDefault("GUEST_OTP_SECRET",System.getProperty("GUEST_OTP_SECRET"));
        if(otpSecret==null||otpSecret.length()<32) throw new IllegalStateException("Set GUEST_OTP_SECRET to a random value of at least 32 characters");
        auth=new GuestAuthService(otpSecret, otpDelivery());
        bookings = new BookingRepository(bookingUrl, user, password, pool);
        guests = new GuestRepository(guestUrl, user, password, pool);
        commercial = new CommercialRepository(bookingUrl, user, password, pool);
        operations = new BookingOperationsRepository(bookingUrl, user, password, pool);
        String paymentUrl=System.getenv().getOrDefault("PAYMENT_JDBC_URL",bookingUrl.replace("ashkan_booking","ashkan_payment"));
        payments=new PaymentRepository(paymentUrl,user,password,pool);
        refunds=new RefundRepository(paymentUrl,user,password,pool);
        webhookEvents=new PaymentWebhookRepository(paymentUrl,user,password,pool);
        financialAudit=new FinancialAuditRepository(paymentUrl,user,password,pool);
        paymentWorkflow=new PaymentWorkflowService(bookings,payments,refunds,new DemoPaymentGateway());
        String bind = System.getenv().getOrDefault("PORTAL_BIND_HOST", "127.0.0.1");
        server = HttpServer.create(new InetSocketAddress(bind, port), 64);
        server.createContext("/", this::handle).getFilters().add(HotelTelemetry.global().requestFilter("guest-portal"));
        server.setExecutor(Executors.newFixedThreadPool(8, r -> { Thread t = new Thread(r, "guest-portal-http"); t.setDaemon(true); return t; }));
    }

    private void handle(HttpExchange x) throws IOException {
        security(x);
        if (!allow(x.getRemoteAddress().getAddress().getHostAddress())) { send(x,429,Map.of("error","rate_limit_exceeded","retryAfterSeconds",60)); return; }
        String path=x.getRequestURI().getPath(), method=x.getRequestMethod();
        try {
            if (path.equals("/") || path.equals("/index.html")) { if(!method.equals("GET")){send(x,405,Map.of("error","method_not_allowed"));return;} html(x); return; }
            if (path.equals("/health")) { send(x,200,Map.of("service","guest-portal","status","UP")); return; }
            if (path.equals("/api/portal/auth/request-otp") && method.equals("POST")) { requestOtp(x); return; }
            if (path.equals("/api/portal/auth/verify-otp") && method.equals("POST")) { verifyOtp(x); return; }
            if (path.equals("/api/portal/auth/logout") && method.equals("POST")) { logout(x); return; }
            if (path.equals("/api/portal/me") && method.equals("GET")) { me(x); return; }
            if (path.equals("/api/portal/webhooks/payment") && method.equals("POST")) { paymentWebhook(x); return; }
            if (path.equals("/api/portal/payments/demo") && method.equals("POST")) { demoPayment(x); return; }
            if (path.startsWith("/api/portal/payments/") && path.endsWith("/refund-demo") && method.equals("POST")) { demoRefund(x,path); return; }
            if (path.startsWith("/api/portal/bookings/") && path.endsWith("/confirmation.pdf") && method.equals("GET")) { confirmationPdf(x,path); return; }
            if (path.equals("/api/portal/catalog") && method.equals("GET")) { catalog(x); return; }
            if (path.equals("/api/portal/availability") && method.equals("GET")) { availability(x); return; }
            if (path.equals("/api/portal/quote") && method.equals("POST")) { quote(x); return; }
            if (path.equals("/api/portal/bookings") && method.equals("POST")) { createBooking(x); return; }
            if (path.equals("/api/portal/manage") && method.equals("POST")) { manage(x); return; }
            if (path.equals("/api/portal/cancel") && method.equals("POST")) { cancel(x); return; }
            if (path.equals("/api/portal/modify") && method.equals("POST")) { modify(x); return; }
            send(x,404,Map.of("error","not_found"));
        } catch (IllegalArgumentException e) { send(x,400,Map.of("error","validation_failed","message",Objects.toString(e.getMessage(),"Invalid request"))); }
          catch (IllegalStateException e) { send(x,409,Map.of("error","booking_conflict","message",Objects.toString(e.getMessage(),"Conflict"))); }
          catch (java.sql.SQLException e) { int status="23P01".equals(e.getSQLState())?409:500; send(x,status,Map.of("error",status==409?"room_unavailable":"portal_operation_failed","message",status==409?"The selected room is no longer available": "The request could not be completed")); }
          catch (Exception e) { send(x,500,Map.of("error","portal_operation_failed","message","The request could not be completed")); }
    }

    private void requestOtp(HttpExchange x)throws Exception{
        String email=validEmail(req(body(x),"email"));
        List<GuestRepository.Guest> matches=guests.list(2,0,email);
        GuestRepository.Guest guest=matches.stream().filter(g->g.email()!=null&&g.email().equalsIgnoreCase(email)).findFirst().orElse(null);
        // A generic response avoids exposing whether an email belongs to a hotel guest.
        if(guest!=null)auth.request(email,guest.id());
        send(x,202,Map.of("message","If this email belongs to a guest account, a verification code has been sent."));
    }
    private void verifyOtp(HttpExchange x)throws Exception{
        Map<String,Object>b=body(x);String email=validEmail(req(b,"email"));String token=auth.verify(email,req(b,"code"));
        boolean secure=Boolean.parseBoolean(System.getenv().getOrDefault("PORTAL_COOKIE_SECURE","true"));
        x.getResponseHeaders().add("Set-Cookie","ASHKAN_GUEST_SESSION="+token+"; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800"+(secure?"; Secure":""));
        send(x,200,Map.of("authenticated",true,"expiresInSeconds",28800));
    }
    private void logout(HttpExchange x)throws IOException{String token=sessionToken(x);auth.logout(token);x.getResponseHeaders().add("Set-Cookie","ASHKAN_GUEST_SESSION=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0"+(Boolean.parseBoolean(System.getenv().getOrDefault("PORTAL_COOKIE_SECURE","true"))?"; Secure":""));send(x,200,Map.of("authenticated",false));}
    private void me(HttpExchange x)throws Exception{
        String guestId=auth.guestId(sessionToken(x)).orElseThrow(()->new IllegalArgumentException("Guest authentication required"));
        GuestRepository.Guest g=guests.find(guestId).orElseThrow(()->new IllegalArgumentException("Guest account not found"));
        List<Map<String,Object>> history=bookings.listForGuest(guestId,100).stream().map(b->Map.<String,Object>of("bookingId",b.id(),"roomId",b.roomId(),"checkIn",b.checkIn().toString(),"checkOut",b.checkOut().toString(),"status",b.status(),"total",b.total(),"currency",b.currency())).toList();
        send(x,200,Map.of("guest",Map.of("id",g.id(),"firstName",g.firstName(),"lastName",g.lastName(),"email",Objects.toString(g.email(),"")),"bookings",history));
    }
    private static String sessionToken(HttpExchange x){String cookie=x.getRequestHeaders().getFirst("Cookie");if(cookie==null)return null;for(String part:cookie.split(";")){String[] kv=part.trim().split("=",2);if(kv.length==2&&kv[0].equals("ASHKAN_GUEST_SESSION"))return kv[1];}return null;}
    private static GuestAuthService.OtpDelivery otpDelivery(){String endpoint=System.getenv("OTP_DELIVERY_WEBHOOK_URL");if(endpoint==null||endpoint.isBlank())return GuestAuthService.consoleDelivery();return (email,code)->{java.net.http.HttpRequest request=java.net.http.HttpRequest.newBuilder(URI.create(endpoint)).header("Content-Type","application/json").header("Authorization","Bearer "+System.getenv().getOrDefault("OTP_DELIVERY_WEBHOOK_TOKEN","")).POST(java.net.http.HttpRequest.BodyPublishers.ofString(ApiJson.write(Map.of("to",email,"template","guest-login-otp","code",code,"expiresInMinutes",5)))).build();java.net.http.HttpResponse<String> response=java.net.http.HttpClient.newHttpClient().send(request,java.net.http.HttpResponse.BodyHandlers.ofString());if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("OTP delivery provider rejected the request");};}


    private void demoPayment(HttpExchange x)throws Exception{
        Map<String,Object>b=body(x);BookingRepository.Booking booking=verifiedBooking(x,req(b,"bookingId"),null);
        String key=x.getRequestHeaders().getFirst("Idempotency-Key");PaymentWorkflowService.requireKey(key);
        PaymentRepository.Payment p=paymentWorkflow.authorizeDemo(booking.id(),key);
        financialAudit.append("guest:"+p.bookingId(),"DEMO_PAYMENT_AUTHORIZED",p.id(),"amount="+p.amount()+";currency="+p.currency());
        send(x,200,Map.of("paymentId",p.id(),"bookingId",p.bookingId(),"amount",p.amount(),"currency",p.currency(),"status",p.status(),"simulated",true,"message","Demo authorization only. No money was charged."));
    }
    private void demoRefund(HttpExchange x,String path)throws Exception{
        String paymentId=path.substring("/api/portal/payments/".length(),path.length()-"/refund-demo".length());
        PaymentRepository.Payment p=payments.find(paymentId).orElseThrow(()->new IllegalArgumentException("Payment not found"));
        verifiedBooking(x,p.bookingId(),null);Map<String,Object>b=body(x);java.math.BigDecimal amount=new java.math.BigDecimal(req(b,"amount"));
        String key=x.getRequestHeaders().getFirst("Idempotency-Key");var r=paymentWorkflow.refundDemo(paymentId,amount,key);
        financialAudit.append("guest:"+r.bookingId(),"DEMO_REFUND_"+r.status(),r.id(),"paymentId="+r.paymentId()+";amount="+r.amount());
        send(x,200,Map.of("refundId",r.id(),"paymentId",r.paymentId(),"amount",r.amount(),"currency",r.currency(),"status",r.status(),"simulated",true,"message","Demo refund only. No funds were transferred."));
    }
    private void paymentWebhook(HttpExchange x)throws Exception{
        String secret=System.getenv("PAYMENT_WEBHOOK_SECRET");
        if(secret==null||secret.length()<32){send(x,503,Map.of("error","webhook_not_configured"));return;}
        byte[] bytes=x.getRequestBody().readNBytes(65537);if(bytes.length>65536)throw new IllegalArgumentException("Webhook payload too large");
        String raw=new String(bytes,StandardCharsets.UTF_8);String ts=x.getRequestHeaders().getFirst("X-Payment-Timestamp");String signature=x.getRequestHeaders().getFirst("X-Payment-Signature");
        long timestamp;try{timestamp=Long.parseLong(Objects.toString(ts,""));}catch(NumberFormatException e){send(x,401,Map.of("error","invalid_webhook_signature"));return;}
        if(!PaymentWebhookSecurity.verify(secret,timestamp,raw,signature,java.time.Instant.now(),300)){send(x,401,Map.of("error","invalid_webhook_signature"));return;}
        Map<String,Object> event=ApiJson.object(raw);String eventId=req(event,"eventId"),paymentId=req(event,"paymentId"),status=req(event,"status").toUpperCase(Locale.ROOT);
        if(!Set.of("AUTHORIZED","CAPTURED","FAILED","REFUNDED").contains(status))throw new IllegalArgumentException("Unsupported payment event status");
        PaymentRepository.Payment current=payments.find(paymentId).orElseThrow(()->new IllegalArgumentException("Payment not found"));
        java.math.BigDecimal amount=ApiJson.decimal(event,"amount","0");String currency=req(event,"currency").toUpperCase(Locale.ROOT);
        if(current.amount().compareTo(amount)!=0||!current.currency().equals(currency))throw new IllegalArgumentException("Webhook amount or currency does not match the payment");
        String provider=ApiJson.str(event,"provider","external");String providerReference=ApiJson.str(event,"providerReference",null);
        String hash=sha256(raw);
        String priorHash=webhookEvents.payloadHash(provider,eventId);
        if(priorHash!=null){if(!priorHash.equals(hash))throw new IllegalArgumentException("Provider event ID was reused with a different payload");send(x,200,Map.of("accepted",true,"duplicate",true));return;}
        boolean allowed=switch(current.status()){
            case "INITIATED" -> Set.of("AUTHORIZED","FAILED").contains(status);
            case "AUTHORIZED" -> Set.of("AUTHORIZED","CAPTURED","FAILED","REFUNDED").contains(status);
            case "CAPTURED" -> Set.of("CAPTURED","REFUNDED").contains(status);
            case "FAILED","REFUNDED" -> current.status().equals(status);
            default -> false;
        };
        if(!allowed)throw new IllegalStateException("Invalid payment state transition");
        if(!webhookEvents.claim(provider,eventId,paymentId,status,hash)){String concurrentHash=webhookEvents.payloadHash(provider,eventId);if(!hash.equals(concurrentHash))throw new IllegalArgumentException("Provider event ID was reused with a different payload");send(x,200,Map.of("accepted",true,"duplicate",true));return;}
        if(!current.status().equals(status))payments.updateStatus(paymentId,status,providerReference);
        financialAudit.append("provider:"+provider,"PAYMENT_WEBHOOK_"+status,paymentId,"eventId="+eventId+";payloadSha256="+hash);
        send(x,200,Map.of("accepted",true,"duplicate",false,"paymentId",paymentId,"status",status));
    }

    private void confirmationPdf(HttpExchange x,String path)throws Exception{
        String id=path.substring("/api/portal/bookings/".length(),path.length()-"/confirmation.pdf".length());
        BookingRepository.Booking b=verifiedBooking(x,id,null);GuestRepository.Guest g=guests.find(b.guestId()).orElseThrow(()->new IllegalArgumentException("Guest not found"));
        byte[] pdf=BookingConfirmationPdf.create(b,g.firstName()+" "+g.lastName(),g.email());
        x.getResponseHeaders().set("Content-Type","application/pdf");x.getResponseHeaders().set("Content-Disposition","attachment; filename=booking-"+b.id()+".pdf");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(200,pdf.length);try(OutputStream out=x.getResponseBody()){out.write(pdf);}
    }

    private void catalog(HttpExchange x) throws Exception {
        List<Map<String,Object>> rooms=commercial.rooms(null).stream().filter(r->"ACTIVE".equals(r.status())).map(r->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.id());m.put("roomType",r.roomType());m.put("roomNumber",r.roomNumber());m.put("capacity",r.capacity());m.put("amenities",r.amenities());return m;}).toList();
        List<Map<String,Object>> rates=commercial.ratePlans().stream().filter(CommercialRepository.RatePlan::active).map(r->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.id());m.put("name",r.name());m.put("roomType",r.roomType());m.put("currency",r.currency());m.put("baseRate",r.baseRate());m.put("maxOccupancy",r.maxOccupancy());return m;}).toList();
        send(x,200,Map.of("rooms",rooms,"ratePlans",rates));
    }
    private void availability(HttpExchange x) throws Exception {
        LocalDate from=LocalDate.parse(required(query(x,"checkIn"),"checkIn")),to=LocalDate.parse(required(query(x,"checkOut"),"checkOut"));
        validateStayDates(from,to,LocalDate.now());
        int count=integer(query(x,"guests"),1);if(count<1||count>20)throw new IllegalArgumentException("guests must be between 1 and 20");
        send(x,200,commercial.availability(required(query(x,"roomType"),"roomType"),from,to,count));
    }
    private void quote(HttpExchange x) throws Exception {
        Map<String,Object>b=body(x); LocalDate from=LocalDate.parse(req(b,"checkIn")),to=LocalDate.parse(req(b,"checkOut"));int count=integer(b,"guests",1);
        CommercialRepository.RatePlan plan=plan(req(b,"ratePlanId"),from,to,count);
        Map<String,Object> av=commercial.availability(plan.roomType(),from,to,count);if(!Boolean.TRUE.equals(av.get("available")))throw new IllegalStateException("No rooms available for the selected dates");
        DynamicPricingEngine.Quote q=pricing.quote(new DynamicPricingEngine.Rules(plan.baseRate(),plan.weekendMultiplier(),plan.highDemandMultiplier(),plan.currency(),plan.maxOccupancy()),from,to,count,(BigDecimal)av.get("occupancyRatio"));
        send(x,200,quoteMap(plan,q,av));
    }
    private void createBooking(HttpExchange x) throws Exception {
        Map<String,Object>b=body(x);String first=req(b,"firstName"),last=req(b,"lastName"),email=validEmail(req(b,"email"));
        String phone=ApiJson.str(b,"phone",null),nationality=ApiJson.str(b,"nationality",null);LocalDate from=LocalDate.parse(req(b,"checkIn")),to=LocalDate.parse(req(b,"checkOut"));
        validateStayDates(from,to,LocalDate.now());int count=integer(b,"guests",1);
        CommercialRepository.RatePlan plan=plan(req(b,"ratePlanId"),from,to,count);Map<String,Object> av=commercial.availability(plan.roomType(),from,to,count);if(!Boolean.TRUE.equals(av.get("available")))throw new IllegalStateException("No rooms available for the selected dates");
        DynamicPricingEngine.Quote q=pricing.quote(new DynamicPricingEngine.Rules(plan.baseRate(),plan.weekendMultiplier(),plan.highDemandMultiplier(),plan.currency(),plan.maxOccupancy()),from,to,count,(BigDecimal)av.get("occupancyRatio"));
        String proposedBookingId="BK-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        String idemKey=x.getRequestHeaders().getFirst("Idempotency-Key");
        if(idemKey!=null&&!idemKey.isBlank()){
            PostgresIdempotencyStore.Reservation reservation=idempotency.reserve("guest-portal-booking",idemKey,sha256(ApiJson.write(b)),proposedBookingId);
            if(reservation.replay()){
                BookingRepository.Booking existing=bookings.find(reservation.resourceId()).orElseThrow(()->new IllegalStateException("Previous booking request is still being processed; contact the hotel"));
                send(x,200,Map.of("bookingId",existing.id(),"status",existing.status(),"total",existing.total(),"currency",existing.currency(),"idempotentReplay",true));return;
            }
            proposedBookingId=reservation.resourceId();
        }
        GuestLookup lookup=findOrCreateGuest(first,last,email,phone,nationality);GuestRepository.Guest guest=lookup.guest();
        boolean bookingSaved=false;
        try { // Choose an eligible room and let the transactional booking repository arbitrate races.
            List<CommercialRepository.Room> candidates=commercial.rooms(plan.roomType()).stream().filter(r->"ACTIVE".equals(r.status())&&r.capacity()>=count).toList();
            if(candidates.isEmpty())throw new IllegalStateException("No suitable room is configured");
            String bookingId=proposedBookingId;boolean saved=false;
            for(CommercialRepository.Room room:candidates){try{bookings.save(new BookingRepository.Booking(bookingId,guest.id(),room.id(),from,to,"PENDING",q.total(),q.currency()));saved=true;break;}catch(java.sql.SQLException e){if(!"23P01".equals(e.getSQLState()))throw e;}}
            if(!saved)throw new IllegalStateException("The selected room type is no longer available");
            bookingSaved=true;
            guests.addHistory(guest.id(),"ONLINE_BOOKING_CREATED","Booking reference "+bookingId);
            send(x,201,Map.of("bookingId",bookingId,"status","PENDING","total",q.total(),"currency",q.currency(),"numberOfNights",q.numberOfNights(),"message","Your reservation request has been received. Payment is not collected in this portal."));
        } catch(Exception e){
            if(bookingSaved)try{bookings.softDelete(proposedBookingId);}catch(Exception ignored){}
            if(lookup.created())try{guests.softDelete(guest.id());}catch(Exception ignored){}
            throw e;
        }
    }
    private record GuestLookup(GuestRepository.Guest guest,boolean created){}
    private GuestLookup findOrCreateGuest(String first,String last,String email,String phone,String nationality)throws Exception{
        for(GuestRepository.Guest g:guests.list(100,0,email))if(g.email()!=null&&g.email().equalsIgnoreCase(email))return new GuestLookup(g,false);
        GuestRepository.Guest g=new GuestRepository.Guest("G-"+UUID.randomUUID().toString().substring(0,12),first,last,email,phone,nationality);guests.save(g);return new GuestLookup(g,true);
    }
    private void manage(HttpExchange x)throws Exception{Map<String,Object>b=body(x);BookingRepository.Booking booking=verifiedBooking(x,req(b,"bookingId"),ApiJson.str(b,"email",null));send(x,200,bookingMap(booking));}
    private void cancel(HttpExchange x)throws Exception{Map<String,Object>b=body(x);BookingRepository.Booking booking=verifiedBooking(x,req(b,"bookingId"),ApiJson.str(b,"email",null));if(Set.of("CANCELLED","CHECKED_OUT","CHECKED_IN").contains(booking.status()))throw new IllegalStateException("This booking cannot be cancelled online");var result=operations.cancel(booking.id(),LocalDate.now());guests.addHistory(booking.guestId(),"ONLINE_BOOKING_CANCELLED","Booking reference "+booking.id());send(x,200,Map.of("bookingId",result.bookingId(),"status",result.status(),"cancellationFee",result.fee(),"currency",result.currency(),"reason",result.reason()));}
    private void modify(HttpExchange x)throws Exception{Map<String,Object>b=body(x);BookingRepository.Booking old=verifiedBooking(x,req(b,"bookingId"),ApiJson.str(b,"email",null));if(!Set.of("PENDING","CONFIRMED").contains(old.status()))throw new IllegalStateException("Only pending or confirmed bookings can be modified online");LocalDate from=LocalDate.parse(req(b,"checkIn")),to=LocalDate.parse(req(b,"checkOut"));validateStayDates(from,to,LocalDate.now());CommercialRepository.RatePlan plan=plan(req(b,"ratePlanId"),from,to,1);CommercialRepository.Room assigned=commercial.rooms(null).stream().filter(r->r.id().equals(old.roomId())).findFirst().orElseThrow(()->new IllegalStateException("Assigned room is no longer configured"));if(!assigned.roomType().equals(plan.roomType())||assigned.capacity()<1)throw new IllegalArgumentException("The selected rate plan does not match the assigned room");Map<String,Object> av=commercial.availability(plan.roomType(),from,to,1);DynamicPricingEngine.Quote q=pricing.quote(new DynamicPricingEngine.Rules(plan.baseRate(),plan.weekendMultiplier(),plan.highDemandMultiplier(),plan.currency(),plan.maxOccupancy()),from,to,1,(BigDecimal)av.get("occupancyRatio"));bookings.save(new BookingRepository.Booking(old.id(),old.guestId(),old.roomId(),from,to,old.status(),q.total(),q.currency()));guests.addHistory(old.guestId(),"ONLINE_BOOKING_MODIFIED","Booking reference "+old.id());send(x,200,Map.of("bookingId",old.id(),"status","updated","total",q.total(),"currency",q.currency()));}
    private BookingRepository.Booking verifiedBooking(HttpExchange x,String id,String email)throws Exception{BookingRepository.Booking b=bookings.find(id).orElseThrow(()->new IllegalArgumentException("Booking not found"));Optional<String> authenticated=auth.guestId(sessionToken(x));if(authenticated.isPresent()){if(!authenticated.get().equals(b.guestId()))throw new IllegalArgumentException("Booking not found");return b;}if(email==null||email.isBlank())throw new IllegalArgumentException("Guest authentication required");GuestRepository.Guest g=guests.find(b.guestId()).orElseThrow(()->new IllegalArgumentException("Booking not found"));String normalized=validEmail(email);if(g.email()==null||!java.security.MessageDigest.isEqual(g.email().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),normalized.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException("Booking not found");return b;}
    private CommercialRepository.RatePlan plan(String id,LocalDate from,LocalDate to,int guestsCount)throws Exception{CommercialRepository.RatePlan p=commercial.ratePlan(id,from,to).orElseThrow(()->new IllegalArgumentException("Rate plan is not available for these dates"));if(guestsCount>p.maxOccupancy())throw new IllegalArgumentException("Guest count exceeds this rate plan occupancy");return p;}
    private static Map<String,Object> quoteMap(CommercialRepository.RatePlan p,DynamicPricingEngine.Quote q,Map<String,Object> av){Map<String,Object> m=new LinkedHashMap<>();m.put("ratePlanId",p.id());m.put("roomType",p.roomType());m.put("available",av.get("available"));m.put("currency",q.currency());m.put("numberOfNights",q.numberOfNights());m.put("total",q.total());m.put("subtotal",q.subtotal());m.put("nights",q.nights().stream().map(n->Map.of("date",n.date().toString(),"price",n.finalRate())).toList());return m;}
    private static Map<String,Object> bookingMap(BookingRepository.Booking b){return Map.of("bookingId",b.id(),"roomId",b.roomId(),"checkIn",b.checkIn().toString(),"checkOut",b.checkOut().toString(),"status",b.status(),"total",b.total(),"currency",b.currency());}
    private boolean allow(String ip){long now=System.currentTimeMillis();ArrayDeque<Long> q=attempts.computeIfAbsent(ip,k->new ArrayDeque<>());synchronized(q){while(!q.isEmpty()&&now-q.peekFirst()>60_000)q.removeFirst();if(q.size()>=30)return false;q.addLast(now);return true;}}
    private static Map<String,Object> body(HttpExchange x)throws IOException{byte[] raw=x.getRequestBody().readNBytes(65537);if(raw.length>65536)throw new IllegalArgumentException("Request body too large");return ApiJson.object(new String(raw,StandardCharsets.UTF_8));}
    private static String req(Map<String,Object>b,String k){String v=ApiJson.str(b,k,null);return required(v,k);}private static String required(String v,String k){if(v==null||v.isBlank())throw new IllegalArgumentException(k+" is required");return v.trim();}
    static String validEmail(String v){String e=v.trim().toLowerCase(Locale.ROOT);if(e.length()>254||!e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new IllegalArgumentException("A valid email address is required");return e;}
    static void validateStayDates(LocalDate from,LocalDate to,LocalDate today){if(from==null||to==null||today==null||from.isBefore(today)||!to.isAfter(from))throw new IllegalArgumentException("Choose a future check-in and a later check-out date");}
    private static String sha256(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static int integer(String v,int d){try{return v==null?d:Integer.parseInt(v);}catch(Exception e){throw new IllegalArgumentException("Invalid integer parameter");}}private static int integer(Map<String,Object>b,String k,int d){return integer(ApiJson.str(b,k,null),d);}
    private static String query(HttpExchange x,String k){String raw=x.getRequestURI().getRawQuery();if(raw==null)return null;for(String pair:raw.split("&")){String[]p=pair.split("=",2);if(URLDecoder.decode(p[0],StandardCharsets.UTF_8).equals(k))return p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"";}return null;}
    private static void send(HttpExchange x,int code,Object value)throws IOException{byte[] bytes=ApiJson.write(value).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(code,bytes.length);try(OutputStream out=x.getResponseBody()){out.write(bytes);}}
    private static void security(HttpExchange x){var h=x.getResponseHeaders();h.set("X-Content-Type-Options","nosniff");h.set("X-Frame-Options","DENY");h.set("Referrer-Policy","strict-origin-when-cross-origin");h.set("Content-Security-Policy","default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'");}
    private static void html(HttpExchange x)throws IOException{byte[] b=PAGE.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(200,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}
    public void start(){server.start();}public int port(){return server.getAddress().getPort();}
    @Override public void close(){server.stop(0);bookings.close();guests.close();commercial.close();operations.close();payments.close();refunds.close();}
    public static void main(String[]args)throws Exception{int port=Integer.parseInt(System.getenv().getOrDefault("PORTAL_PORT",args.length>0?args[0]:"8085"));String user=System.getenv().getOrDefault("DB_USER","hotel"),pass=System.getenv().getOrDefault("DB_PASSWORD","change-this-database-password");String booking=System.getenv().getOrDefault("BOOKING_JDBC_URL","jdbc:postgresql://localhost:5432/ashkan_booking"),guest=System.getenv().getOrDefault("GUEST_JDBC_URL","jdbc:postgresql://localhost:5432/ashkan_guest");GuestPortalHttpService p=new GuestPortalHttpService(port,booking,guest,user,pass,Integer.parseInt(System.getenv().getOrDefault("DB_POOL_SIZE","6")));Runtime.getRuntime().addShutdownHook(new Thread(p::close));p.start();System.out.println("Guest portal listening on port "+p.port());}

    private static final String PAGE="""
<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#102b46"><title>Ashkan HotelSphere | Guest Portal</title><style>
:root{--navy:#102b46;--teal:#0e8178;--gold:#d7ad62;--bg:#f3f6f8;--ink:#172b3a;--muted:#657786;--line:#e3eaf0}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font-family:Tahoma,Arial,sans-serif}.top{background:linear-gradient(120deg,#102b46,#1d5366);color:white;padding:22px max(5vw,20px);display:flex;justify-content:space-between;align-items:center}.brand{font-size:22px;font-weight:800}.brand small{display:block;font-size:11px;opacity:.7;font-weight:400;margin-top:5px}.lang{border:1px solid #ffffff66;background:transparent;color:white;padding:9px 14px;border-radius:20px;cursor:pointer}.hero{padding:52px 20px 68px;background:linear-gradient(120deg,#102b46,#0e8178);color:#fff}.hero div{max-width:1100px;margin:auto}.hero h1{font-size:clamp(28px,4vw,46px);margin:0 0 14px}.hero p{max-width:620px;line-height:2;color:#e7f0f3}.wrap{max-width:1100px;margin:-30px auto 60px;padding:0 18px;position:relative}.grid{display:grid;grid-template-columns:1.2fr .8fr;gap:20px}.card{background:#fff;border:1px solid var(--line);border-radius:18px;padding:24px;box-shadow:0 14px 36px #102b4610}.card h2{margin:0 0 18px;font-size:20px}.fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.field{display:flex;flex-direction:column;gap:7px}.field.full{grid-column:1/-1}label{font-size:13px;color:var(--muted)}input,select{width:100%;padding:12px 13px;border:1px solid #dce5eb;border-radius:10px;font:inherit;background:white;color:var(--ink)}input:focus,select:focus{outline:2px solid #0e817833;border-color:var(--teal)}button.primary{background:var(--teal);color:#fff;border:0;border-radius:10px;padding:13px 18px;font:inherit;font-weight:700;cursor:pointer}button.secondary{background:#edf4f4;color:#17645f;border:0;border-radius:10px;padding:11px 15px;font:inherit;cursor:pointer}.actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:18px}.result{margin-top:16px;padding:14px;border-radius:12px;background:#f2f8f7;line-height:1.9;display:none;white-space:pre-wrap}.muted{color:var(--muted);font-size:13px;line-height:1.8}.rooms{display:grid;gap:10px}.room{padding:14px;border:1px solid var(--line);border-radius:12px}.room strong{display:block;margin-bottom:5px}.status{font-size:12px;color:var(--teal)}footer{text-align:center;color:var(--muted);padding:25px;font-size:12px}@media(max-width:760px){.grid{grid-template-columns:1fr}.fields{grid-template-columns:1fr}.field.full{grid-column:auto}.hero{padding:38px 20px 60px}}
</style></head><body><header class="top"><div class="brand">Ashkan HotelSphere<small data-t="sub">مهمان‌نوازی، با تجربه‌ای ساده‌تر</small></div><button class="lang" id="lang">English</button></header><section class="hero"><div><h1 data-t="hero">اقامت بعدی‌تان را هوشمندانه رزرو کنید</h1><p data-t="intro">اتاق‌ها را بررسی کنید، قیمت شفاف دریافت کنید و رزرو خود را به‌صورت آنلاین ثبت یا مدیریت کنید.</p></div></section><main class="wrap"><div class="grid"><section class="card"><h2 data-t="search">جست‌وجوی اقامت</h2><div class="fields"><div class="field"><label data-t="room">نوع اتاق</label><select id="roomType"></select></div><div class="field"><label data-t="plan">طرح قیمت</label><select id="ratePlan"></select></div><div class="field"><label data-t="in">تاریخ ورود</label><input type="date" id="checkIn"></div><div class="field"><label data-t="out">تاریخ خروج</label><input type="date" id="checkOut"></div><div class="field"><label data-t="guests">تعداد مهمان</label><input type="number" id="count" min="1" max="20" value="2"></div></div><div class="actions"><button class="primary" id="availability" data-t="check">بررسی ظرفیت و قیمت</button></div><div id="searchResult" class="result"></div><div id="roomList" class="rooms"></div><h2 style="margin-top:26px" data-t="book">ثبت درخواست رزرو</h2><div class="fields"><div class="field"><label data-t="first">نام</label><input id="firstName" autocomplete="given-name"></div><div class="field"><label data-t="last">نام خانوادگی</label><input id="lastName" autocomplete="family-name"></div><div class="field"><label>Email</label><input id="email" type="email" autocomplete="email"></div><div class="field"><label data-t="phone">تلفن</label><input id="phone" autocomplete="tel"></div><div class="field full"><label data-t="nation">ملیت (اختیاری)</label><input id="nationality"></div></div><div class="actions"><button class="primary" id="reserve" data-t="reserve">ثبت رزرو</button></div><div id="bookingResult" class="result"></div><p class="muted" data-t="paymentNote">در این نسخه اطلاعات کارت بانکی دریافت نمی‌شود؛ ثبت رزرو به معنی دریافت درخواست است و پرداخت آنلاین درگاه هنوز فعال نیست.</p></section><aside class="card"><h2 data-t="manage">مدیریت رزرو</h2><p class="muted" data-t="manageInfo">برای مشاهده یا لغو رزرو، شماره پیگیری و ایمیلی را وارد کنید که هنگام رزرو ثبت کرده‌اید.</p><div class="fields"><div class="field full"><label data-t="ref">شماره پیگیری</label><input id="bookingId" placeholder="BK-XXXXXXXXXXXX"></div><div class="field full"><label>Email</label><input id="manageEmail" type="email"></div></div><div class="actions"><button class="primary" id="lookup" data-t="lookup">مشاهده رزرو</button><button class="secondary" id="cancel" data-t="cancel">درخواست لغو</button><button class="secondary" id="modify" data-t="modify">تغییر تاریخ</button></div><div id="manageResult" class="result"></div><hr style="border:0;border-top:1px solid var(--line);margin:26px 0"><h2 data-t="roomsTitle">اتاق‌های هتل</h2><p class="muted" data-t="roomsInfo">نوع اتاق، ظرفیت و امکانات پس از اتصال به سامانهٔ هتل نمایش داده می‌شوند.</p><div id="catalogRooms" class="rooms"></div></aside></div></main><footer>© Ashkan HotelSphere · Guest self-service portal</footer><script>
const $=id=>document.getElementById(id);let catalog={rooms:[],ratePlans:[]},lang='fa';const t={fa:{sub:'مهمان‌نوازی، با تجربه‌ای ساده‌تر',hero:'اقامت بعدی‌تان را هوشمندانه رزرو کنید',intro:'اتاق‌ها را بررسی کنید، قیمت شفاف دریافت کنید و رزرو خود را به‌صورت آنلاین ثبت یا مدیریت کنید.',search:'جست‌وجوی اقامت',room:'نوع اتاق',plan:'طرح قیمت',in:'تاریخ ورود',out:'تاریخ خروج',guests:'تعداد مهمان',check:'بررسی ظرفیت و قیمت',book:'ثبت درخواست رزرو',first:'نام',last:'نام خانوادگی',phone:'تلفن',nation:'ملیت (اختیاری)',reserve:'ثبت رزرو',paymentNote:'در این نسخه اطلاعات کارت بانکی دریافت نمی‌شود؛ ثبت رزرو به معنی دریافت درخواست است و پرداخت آنلاین درگاه هنوز فعال نیست.',manage:'مدیریت رزرو',manageInfo:'برای مشاهده یا لغو رزرو، شماره پیگیری و ایمیلی را وارد کنید که هنگام رزرو ثبت کرده‌اید.',ref:'شماره پیگیری',lookup:'مشاهده رزرو',cancel:'درخواست لغو',modify:'تغییر تاریخ رزرو',roomsTitle:'اتاق‌های هتل',roomsInfo:'نوع اتاق، ظرفیت و امکانات پس از اتصال به سامانهٔ هتل نمایش داده می‌شوند.',available:'ظرفیت موجود',none:'اتاقی برای نمایش ثبت نشده است.',ok:'درخواست با موفقیت انجام شد',err:'انجام درخواست ممکن نشد',quote:'قیمت کل',nights:'شب',booking:'شماره رزرو',status:'وضعیت',total:'مبلغ',confirm:'آیا از لغو رزرو مطمئن هستید؟',selected:'گزینه انتخاب‌شده',noRooms:'ظرفیت موجود نیست'},en:{sub:'Hospitality, made effortless',hero:'Book your next stay with confidence',intro:'Explore rooms, get transparent quotes, and create or manage your reservation online.',search:'Find your stay',room:'Room type',plan:'Rate plan',in:'Check-in',out:'Check-out',guests:'Guests',check:'Check availability & price',book:'Request a reservation',first:'First name',last:'Last name',phone:'Phone',nation:'Nationality (optional)',reserve:'Submit reservation',paymentNote:'This portal does not collect card details. Your request is recorded as a reservation request; online payment is not enabled yet.',manage:'Manage a booking',manageInfo:'Enter your booking reference and the email address used for the reservation.',ref:'Booking reference',lookup:'Find booking',cancel:'Request cancellation',modify:'Change dates',roomsTitle:'Hotel rooms',roomsInfo:'Room types, capacity and amenities are loaded from the hotel system.',available:'Available',none:'No rooms configured yet.',ok:'Request completed',err:'Request could not be completed',quote:'Total',nights:'nights',booking:'Booking reference',status:'Status',total:'Amount',confirm:'Are you sure you want to cancel this booking?',selected:'Selected option',noRooms:'No availability'} };function tr(k){return t[lang][k]||k}function show(id,msg){$(id).style.display='block';$(id).textContent=msg}async function api(path,opts={}){let r=await fetch(path,{headers:{'Content-Type':'application/json'},...opts});let d=await r.json();if(!r.ok)throw Error(d.message||d.error||tr('err'));return d}function dates(){return {checkIn:$('checkIn').value,checkOut:$('checkOut').value,guests:Number($('count').value)}}function selectedPlan(){return catalog.ratePlans.find(x=>x.id===$('ratePlan').value)}function loadCatalog(){return api('/api/portal/catalog').then(d=>{catalog=d;let types=[...new Set(d.rooms.map(r=>r.roomType))];$('roomType').innerHTML=types.map(v=>`<option>${esc(v)}</option>`).join('');$('ratePlan').innerHTML=d.ratePlans.map(p=>`<option value="${esc(p.id)}">${esc(p.name)} · ${esc(p.roomType)} · ${p.baseRate} ${p.currency}</option>`).join('');$('catalogRooms').innerHTML=d.rooms.map(r=>`<div class="room"><strong>${esc(r.roomType)} · ${esc(r.roomNumber)}</strong><span class="muted">${r.capacity} ${tr('guests')} · ${esc((r.amenities||[]).join(', '))}</span></div>`).join('')||`<p class="muted">${tr('none')}</p>`;syncPlans()})}function syncPlans(){let type=$('roomType').value;let plans=catalog.ratePlans.filter(p=>p.roomType===type);$('ratePlan').innerHTML=plans.map(p=>`<option value="${esc(p.id)}">${esc(p.name)} · ${p.baseRate} ${p.currency}</option>`).join('')}function esc(s){return String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}$('roomType').onchange=syncPlans;$('availability').onclick=async()=>{try{let d=dates(),p=selectedPlan();if(!p)throw Error('No rate plan');let a=await api('/api/portal/availability?'+new URLSearchParams({...d,roomType:p.roomType}));let q=await api('/api/portal/quote',{method:'POST',body:JSON.stringify({...d,ratePlanId:p.id})});show('searchResult',`${tr('available')}: ${a.availableRooms} / ${a.totalRooms}\n${tr('quote')}: ${q.total} ${q.currency} · ${q.numberOfNights} ${tr('nights')}`)}catch(e){show('searchResult',e.message)}};$('reserve').onclick=async()=>{try{let p=selectedPlan(),d=dates();if(!p)throw Error('No rate plan');let r=await api('/api/portal/bookings',{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({...d,ratePlanId:p.id,firstName:$('firstName').value,lastName:$('lastName').value,email:$('email').value,phone:$('phone').value,nationality:$('nationality').value})});$('bookingId').value=r.bookingId;$('manageEmail').value=$('email').value;show('bookingResult',`${tr('booking')}: ${r.bookingId}\n${tr('status')}: ${r.status}\n${tr('total')}: ${r.total} ${r.currency}\n${r.message}`)}catch(e){show('bookingResult',e.message)}};$('lookup').onclick=async()=>{try{let r=await api('/api/portal/manage',{method:'POST',body:JSON.stringify({bookingId:$('bookingId').value,email:$('manageEmail').value})});show('manageResult',`${tr('booking')}: ${r.bookingId}\n${r.checkIn} → ${r.checkOut}\n${tr('status')}: ${r.status}\n${tr('total')}: ${r.total} ${r.currency}`)}catch(e){show('manageResult',e.message)}};$('cancel').onclick=async()=>{if(!confirm(tr('confirm')))return;try{let r=await api('/api/portal/cancel',{method:'POST',body:JSON.stringify({bookingId:$('bookingId').value,email:$('manageEmail').value})});show('manageResult',`${tr('status')}: ${r.status}\n${tr('total')}: ${r.cancellationFee} ${r.currency}\n${r.reason}`)}catch(e){show('manageResult',e.message)}};$('modify').onclick=async()=>{try{let p=selectedPlan(),d=dates();if(!p)throw Error('No rate plan');let r=await api('/api/portal/modify',{method:'POST',body:JSON.stringify({...d,ratePlanId:p.id,bookingId:$('bookingId').value,email:$('manageEmail').value})});show('manageResult',`${tr('status')}: ${r.status}\n${tr('total')}: ${r.total} ${r.currency}`)}catch(e){show('manageResult',e.message)}};$('lang').onclick=()=>{lang=lang==='fa'?'en':'fa';document.documentElement.lang=lang;document.documentElement.dir=lang==='fa'?'rtl':'ltr';$('lang').textContent=lang==='fa'?'English':'فارسی';document.querySelectorAll('[data-t]').forEach(el=>el.textContent=tr(el.dataset.t))};let today=new Date().toISOString().slice(0,10);$('checkIn').min=today;$('checkOut').min=today;loadCatalog().catch(e=>show('searchResult',e.message));
</script></body></html>
""";
}
