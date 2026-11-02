package com.ashkan.hotel;

import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** Domain REST API v2. Domain endpoints are enabled when JDBC_URL points at the service database. */
final class DomainApiV2 implements AutoCloseable {
    private final String service; private final PostgresIdempotencyStore idempotency; private final BookingRepository bookings; private final GuestRepository guests; private final PaymentRepository payments; private final InventoryRepository inventory;
    DomainApiV2(String service,String url,String user,String password,int pool) {
        this.service=service; this.idempotency=new PostgresIdempotencyStore(url,user,password); bookings="booking".equals(service)?new BookingRepository(url,user,password,pool):null;
        guests="guest".equals(service)?new GuestRepository(url,user,password,pool):null;
        payments="payment".equals(service)?new PaymentRepository(url,user,password,pool):null;
        inventory="inventory".equals(service)?new InventoryRepository(url,user,password,pool):null;
    }
    void handle(HttpExchange x) throws IOException {
        if(bookings==null&&guests==null&&payments==null&&inventory==null){send(x,503,Map.of("error","domain_api_requires_postgresql"));return;}
        try {
            String path=x.getRequestURI().getPath(); String base="/api/v2/"+resource(); if(!path.equals(base)&&!path.startsWith(base+"/")){send(x,404,Map.of("error","not_found"));return;}
            String id=path.length()>base.length()?URLDecoder.decode(path.substring(base.length()+1),StandardCharsets.UTF_8):null;
            String method=x.getRequestMethod();
            if(id==null||id.isBlank()) {
                if(!method.equals("POST")&&!method.equals("GET")){send(x,405,Map.of("error","method_not_allowed"));return;}
                if(method.equals("GET")){int page=Math.max(1,ApiJson.integer(query(x,"page"),1));int size=Math.max(1,Math.min(100,ApiJson.integer(query(x,"pageSize"),25)));String filter=query(x,bookings!=null||payments!=null?"status":"search");List<?> rows=list(size,(page-1)*size,filter);List<Map<String,Object>> items=new ArrayList<>();for(Object row:rows)items.add(record(row));send(x,200,Map.of("items",items,"page",page,"pageSize",size));return;}
                String raw=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                Map<String,Object> body=ApiJson.object(raw); String proposedId=ApiJson.str(body,"id",UUID.randomUUID().toString());
                String key=x.getRequestHeaders().getFirst("Idempotency-Key");
                boolean replay=false; String newId=proposedId;
                if(key!=null&&!key.isBlank()) { PostgresIdempotencyStore.Reservation reservation=idempotency.reserve(service, key, sha256(raw), proposedId); newId=reservation.resourceId(); replay=reservation.replay(); }
                save(newId,body); send(x,replay?200:201,Map.of("id",newId,"status",replay?"replayed":"created","idempotent",key!=null&&!key.isBlank()));return;
            }
            if(method.equals("GET")){Optional<?> found=find(id);if(found.isEmpty())send(x,404,Map.of("error","not_found"));else send(x,200,record(found.get()));return;}
            if(method.equals("PUT")){Map<String,Object> body=ApiJson.object(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));body.put("id",id);save(id,body);send(x,200,Map.of("id",id,"status","updated"));return;}
            if(method.equals("DELETE")){delete(id);send(x,200,Map.of("id",id,"deleted",true));return;}
            send(x,405,Map.of("error","method_not_allowed"));
        } catch(PostgresIdempotencyStore.IdempotencyConflictException e){send(x,409,Map.of("error","idempotency_conflict","message",e.getMessage()));}
          catch(IllegalArgumentException e){send(x,400,Map.of("error","validation_failed","message",Objects.toString(e.getMessage(),"Invalid request")));}
          catch(java.sql.SQLException e){int status="23P01".equals(e.getSQLState())?409:500;send(x,status,Map.of("error",status==409?"resource_conflict":"domain_operation_failed","message",Objects.toString(e.getMessage(),"Database operation failed")));}
          catch(Exception e){send(x,500,Map.of("error","domain_operation_failed","message",Objects.toString(e.getMessage(),"Unexpected error")));}
    }
    private static String sha256(String value){try{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(digest);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private String resource(){return switch(service){case "booking"->"bookings";case "guest"->"guests";case "payment"->"payments";default->"inventory";};}
    private void save(String id,Map<String,Object> b)throws Exception {
        if(bookings!=null)bookings.save(new BookingRepository.Booking(id,req(b,"guestId"),req(b,"roomId"),LocalDate.parse(req(b,"checkIn")),LocalDate.parse(req(b,"checkOut")),ApiJson.str(b,"status","PENDING"),ApiJson.decimal(b,"totalAmount","0"),ApiJson.str(b,"currency","EUR")));
        else if(guests!=null)guests.save(new GuestRepository.Guest(id,req(b,"firstName"),req(b,"lastName"),ApiJson.str(b,"email",null),ApiJson.str(b,"phone",null),ApiJson.str(b,"nationality",null)));
        else if(payments!=null)payments.save(new PaymentRepository.Payment(id,req(b,"bookingId"),ApiJson.decimal(b,"amount","0"),ApiJson.str(b,"currency","EUR"),req(b,"method"),ApiJson.str(b,"status","INITIATED"),ApiJson.str(b,"providerReference",null)));
        else inventory.save(new InventoryRepository.Product(id,req(b,"sku"),req(b,"name"),req(b,"unit"),ApiJson.decimal(b,"quantity","0"),ApiJson.decimal(b,"reorderLevel","0")));
    }
    private List<?> list(int limit,int offset,String filter)throws Exception {if(bookings!=null)return bookings.list(limit,offset,filter);if(guests!=null)return guests.list(limit,offset,filter);if(payments!=null)return payments.list(limit,offset,filter);return inventory.list(limit,offset,filter);}
    private Optional<?> find(String id)throws Exception {if(bookings!=null)return bookings.find(id);if(guests!=null)return guests.find(id);if(payments!=null)return payments.find(id);return inventory.find(id);}
    private Map<String,Object> record(Object o){Map<String,Object> m=new LinkedHashMap<>();if(o instanceof BookingRepository.Booking b){m.put("id",b.id());m.put("guestId",b.guestId());m.put("roomId",b.roomId());m.put("checkIn",b.checkIn().toString());m.put("checkOut",b.checkOut().toString());m.put("status",b.status());m.put("totalAmount",b.total());m.put("currency",b.currency());}else if(o instanceof GuestRepository.Guest g){m.put("id",g.id());m.put("firstName",g.firstName());m.put("lastName",g.lastName());m.put("email",g.email());m.put("phone",g.phone());m.put("nationality",g.nationality());}else if(o instanceof PaymentRepository.Payment p){m.put("id",p.id());m.put("bookingId",p.bookingId());m.put("amount",p.amount());m.put("currency",p.currency());m.put("method",p.method());m.put("status",p.status());m.put("providerReference",p.providerReference());}else if(o instanceof InventoryRepository.Product p){m.put("id",p.id());m.put("sku",p.sku());m.put("name",p.name());m.put("unit",p.unit());m.put("quantity",p.quantity());m.put("reorderLevel",p.reorderLevel());}return m;}
    private void delete(String id)throws Exception {if(bookings!=null)bookings.softDelete(id);else if(guests!=null)guests.softDelete(id);else if(payments!=null)payments.delete(id);else inventory.softDelete(id);}
    private static String req(Map<String,Object> b,String k){String v=ApiJson.str(b,k,null);if(v==null||v.isBlank())throw new IllegalArgumentException(k+" is required");return v;}
    private static String query(HttpExchange x,String key){String q=x.getRequestURI().getRawQuery();if(q==null)return null;for(String pair:q.split("&")){String[]p=pair.split("=",2);if(URLDecoder.decode(p[0],StandardCharsets.UTF_8).equals(key))return p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"";}return null;}
    private static void send(HttpExchange x,int code,Object body)throws IOException{byte[] bytes=ApiJson.write(body).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(code,bytes.length);try(OutputStream out=x.getResponseBody()){out.write(bytes);}}
    @Override public void close(){try{if(bookings!=null)bookings.close();if(guests!=null)guests.close();if(payments!=null)payments.close();if(inventory!=null)inventory.close();}catch(Exception ignored){}}
}
