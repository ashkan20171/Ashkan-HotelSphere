package com.ashkan.hotel;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Authenticated API edge for the independently deployed hotel domain services. */
public final class HotelEdgeGateway implements AutoCloseable {
    private record Route(String service, String prefix) {}
    private final HttpServer server; private final ServiceRegistry registry; private final JwtTokenService tokens; private final AuthService auth;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String,Route> routes=new LinkedHashMap<>(); private final Map<String,AtomicInteger> failures=new ConcurrentHashMap<>(); private final Map<String,Long> openUntil=new ConcurrentHashMap<>();
    private final GatewayRateLimiter loginLimiter=new GatewayRateLimiter(5,60_000), apiLimiter=new GatewayRateLimiter(120,60_000);
    public HotelEdgeGateway(int port, ServiceRegistry registry, JwtTokenService tokens, AuthService auth)throws IOException{
        this.registry=Objects.requireNonNull(registry);this.tokens=Objects.requireNonNull(tokens);this.auth=Objects.requireNonNull(auth);
        routes.put("/api/bookings",new Route("booking","/api/v1/bookings"));routes.put("/api/payments",new Route("payment","/api/v1/payments"));routes.put("/api/guests",new Route("guest","/api/v1/guests"));routes.put("/api/inventory",new Route("inventory","/api/v1/inventory-items"));
        routes.put("/api/v2/bookings",new Route("booking","/api/v2/bookings"));routes.put("/api/v2/rooms",new Route("booking","/api/v2/rooms"));routes.put("/api/v2/rate-plans",new Route("booking","/api/v2/rate-plans"));routes.put("/api/v2/availability",new Route("booking","/api/v2/availability"));routes.put("/api/v2/quotes",new Route("booking","/api/v2/quotes"));routes.put("/api/v2/booking-operations",new Route("booking","/api/v2/booking-operations"));routes.put("/api/v2/payments",new Route("payment","/api/v2/payments"));routes.put("/api/v2/guests",new Route("guest","/api/v2/guests"));routes.put("/api/v2/inventory",new Route("inventory","/api/v2/inventory"));
        server=HttpServer.create(new InetSocketAddress("0.0.0.0",port),0);
        instrument(server.createContext("/health",x->respond(x,200,"{\"service\":\"hotel-edge-gateway\",\"status\":\"UP\"}")));
        instrument(server.createContext("/metrics",x->{if(!"GET".equals(x.getRequestMethod())){respond(x,405,"{\"error\":\"method_not_allowed\"}");return;}byte[] b=HotelTelemetry.global().prometheus("api-gateway").getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","text/plain; version=0.0.4; charset=utf-8");x.sendResponseHeaders(200,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}));
        instrument(server.createContext("/auth/login",this::login));instrument(server.createContext("/admin/services",this::serviceStatus));instrument(server.createContext("/openapi.json",this::openApi));instrument(server.createContext("/",this::proxy));
        server.setExecutor(Executors.newFixedThreadPool(12,r->{Thread t=new Thread(r,"hotel-edge-gateway");t.setDaemon(true);return t;}));
    }
    public void start(){server.start();} public int port(){return server.getAddress().getPort();}
    private void login(HttpExchange x)throws IOException{
        securityHeaders(x); if(!loginLimiter.allow(clientKey(x))){x.getResponseHeaders().set("Retry-After","60");respond(x,429,"{\"error\":\"login_rate_limit_exceeded\"}");return;}
        if(!"POST".equals(x.getRequestMethod())){respond(x,405,"{\"error\":\"method_not_allowed\"}");return;}
        String body=readBody(x,8192);String user=jsonField(body,"username"),pass=jsonField(body,"password");
        if(user==null||pass==null){respond(x,400,"{\"error\":\"username_and_password_required\"}");return;}
        AuthService.LoginResult result=auth.authenticate(user,pass.toCharArray());if(result!=AuthService.LoginResult.SUCCESS){respond(x,401,"{\"error\":\"authentication_failed\",\"reason\":"+q(result.name())+"}");return;}
        String token=tokens.issue(user,auth.roleOf(user));respond(x,200,"{\"access_token\":"+q(token)+",\"token_type\":\"Bearer\",\"expires_in\":3600,\"role\":"+q(auth.roleOf(user))+"}");
    }
    private void openApi(HttpExchange x)throws IOException{if(!"GET".equals(x.getRequestMethod())){respond(x,405,"{\"error\":\"method_not_allowed\"}");return;}respond(x,200,"{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"Ashkan HotelSphere API\",\"version\":\"2.0.0\"},\"components\":{\"securitySchemes\":{\"BearerAuth\":{\"type\":\"http\",\"scheme\":\"bearer\",\"bearerFormat\":\"JWT\"}}},\"security\":[{\"BearerAuth\":[]}],\"paths\":{\"/api/v2/bookings\":{\"get\":{},\"post\":{}},\"/api/v2/booking-operations\":{\"get\":{}},\"/api/v2/booking-operations/{id}/check-in\":{\"post\":{}},\"/api/v2/booking-operations/{id}/check-out\":{\"post\":{}},\"/api/v2/booking-operations/{id}/cancel\":{\"post\":{}},\"/api/v2/booking-operations/{id}/room\":{\"patch\":{}},\"/api/v2/rooms\":{\"get\":{},\"post\":{}},\"/api/v2/rate-plans\":{\"get\":{},\"post\":{}},\"/api/v2/availability\":{\"get\":{}},\"/api/v2/quotes\":{\"post\":{}},\"/api/v2/guests\":{\"get\":{},\"post\":{}},\"/api/v2/payments\":{\"get\":{},\"post\":{}},\"/api/v2/inventory\":{\"get\":{},\"post\":{}}}}");}
    private void serviceStatus(HttpExchange x)throws IOException{JwtTokenService.Claims c=authorize(x);if(c==null)return;if(!"Admin".equals(c.role())){respond(x,403,"{\"error\":\"admin_required\"}");return;}StringJoiner j=new StringJoiner(",","{","}");registry.snapshot().forEach((k,v)->j.add(q(k)+":"+q(v)));respond(x,200,j.toString());}
    private void proxy(HttpExchange x)throws IOException{
        securityHeaders(x); if(!apiLimiter.allow(clientKey(x))){x.getResponseHeaders().set("Retry-After","60");respond(x,429,"{\"error\":\"rate_limit_exceeded\"}");return;}
        String path=x.getRequestURI().getPath();String prefix=routes.keySet().stream().filter(k->path.equals(k)||path.startsWith(k+"/")).max(Comparator.comparingInt(String::length)).orElse(null);
        if(prefix==null){respond(x,404,"{\"error\":\"route_not_found\"}");return;}
        JwtTokenService.Claims claims=authorize(x);if(claims==null)return;
        Route route=routes.get(prefix);
        String resource=route.prefix().substring(route.prefix().lastIndexOf('/')+1);
        if(!GatewayAccessPolicy.allowed(claims.role(),resource,x.getRequestMethod())){respond(x,403,"{\"error\":\"forbidden_by_role_policy\"}");return;}if(System.currentTimeMillis()<openUntil.getOrDefault(route.service(),0L)){respond(x,503,"{\"error\":\"circuit_open\",\"service\":"+q(route.service())+"}");return;}
        ServiceRegistry.Service service=registry.healthy(route.service()).orElse(null);if(service==null){trip(route.service());respond(x,503,"{\"error\":\"service_unavailable\",\"service\":"+q(route.service())+"}");return;}
        String suffix=path.substring(prefix.length()).replaceFirst("^/","");String query=x.getRequestURI().getRawQuery();URI target=service.baseUri().resolve(route.prefix()+(suffix.isEmpty()?"":"/"+suffix)+(query==null?"":"?"+query));
        try{byte[] body=readBody(x,1_000_000).getBytes(StandardCharsets.UTF_8);HttpRequest.Builder b=HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(8));x.getRequestHeaders().forEach((k,v)->{if(!Set.of("host","content-length","connection","expect","upgrade","transfer-encoding","authorization","traceparent","x-trace-id","x-trace-parent").contains(k.toLowerCase(Locale.ROOT)))b.header(k,String.join(",",v));});b.header("X-Authenticated-User",claims.subject()).header("X-Authenticated-Role",claims.role()).header("X-Trace-Id",Optional.ofNullable(x.getResponseHeaders().getFirst("X-Trace-Id")).orElseGet(()->UUID.randomUUID().toString().replace("-",""))).header("traceparent",Optional.ofNullable(x.getResponseHeaders().getFirst("X-Trace-Parent")).orElseGet(()->HotelTracing.root().traceparent()));b.method(x.getRequestMethod(),body.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));HttpResponse<byte[]> response=client.send(b.build(),HttpResponse.BodyHandlers.ofByteArray());if(response.statusCode()>=500)trip(route.service());else reset(route.service());response.headers().firstValue("Content-Type").ifPresent(v->x.getResponseHeaders().set("Content-Type",v));byte[] out=response.body();x.sendResponseHeaders(response.statusCode(),out.length);try(OutputStream os=x.getResponseBody()){os.write(out);}}
        catch(InterruptedException e){Thread.currentThread().interrupt();trip(route.service());respond(x,503,"{\"error\":\"upstream_interrupted\"}");}catch(Exception e){trip(route.service());respond(x,502,"{\"error\":\"upstream_error\"}");}
    }
    private JwtTokenService.Claims authorize(HttpExchange x)throws IOException{String h=x.getRequestHeaders().getFirst("Authorization");if(h==null||!h.startsWith("Bearer ")){respond(x,401,"{\"error\":\"bearer_token_required\"}");return null;}JwtTokenService.Claims c=tokens.verify(h.substring(7));if(c==null){respond(x,401,"{\"error\":\"invalid_or_expired_token\"}");return null;}return c;}
    private void trip(String service){int n=failures.computeIfAbsent(service,k->new AtomicInteger()).incrementAndGet();if(n>=3){openUntil.put(service,System.currentTimeMillis()+15_000);failures.get(service).set(0);}}
    private void reset(String service){failures.computeIfAbsent(service,k->new AtomicInteger()).set(0);openUntil.remove(service);}
    private static String readBody(HttpExchange x,int max)throws IOException{byte[] b=x.getRequestBody().readNBytes(max+1);if(b.length>max)throw new IOException("Request too large");return new String(b,StandardCharsets.UTF_8);}
    private static String jsonField(String json,String key){var m=java.util.regex.Pattern.compile("\\\""+key+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(json);if(!m.find())return null;return m.group(1).replace("\\\"","\"").replace("\\\\","\\");}
    private static String q(String s){return "\""+Objects.toString(s,"").replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    private static void respond(HttpExchange x,int status,String body)throws IOException{securityHeaders(x);byte[] b=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.getResponseHeaders().set("X-Hotel-Status",Integer.toString(status));x.sendResponseHeaders(status,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}
    private static String clientKey(HttpExchange x){InetSocketAddress a=x.getRemoteAddress();return a==null?"unknown":a.getAddress().getHostAddress();}
    private static void securityHeaders(HttpExchange x){var h=x.getResponseHeaders();h.set("X-Content-Type-Options","nosniff");h.set("X-Frame-Options","DENY");h.set("Referrer-Policy","no-referrer");h.set("Cache-Control","no-store");h.set("Content-Security-Policy","default-src 'none'; frame-ancestors 'none'");String id=Optional.ofNullable(x.getRequestHeaders().getFirst("X-Request-Id")).filter(v->v.matches("[A-Za-z0-9._-]{1,80}")).orElseGet(()->UUID.randomUUID().toString());h.set("X-Request-Id",id);}
    private static void instrument(HttpContext context){context.getFilters().add(HotelTelemetry.global().requestFilter("api-gateway"));}
    @Override public void close(){server.stop(0);}
    public static void main(String[] args)throws Exception{
        int port=Integer.parseInt(System.getenv().getOrDefault("GATEWAY_PORT",args.length>0?args[0]:"8080"));String secret=System.getenv("JWT_SECRET");if(secret==null||secret.length()<32)throw new IllegalStateException("Set JWT_SECRET to a random value of at least 32 characters");
        boolean docker=Boolean.parseBoolean(System.getenv().getOrDefault("SERVICE_DOCKER_NETWORK","false"));String booking=docker?"booking-service":"localhost",payment=docker?"payment-service":"localhost",guest=docker?"guest-service":"localhost",inventory=docker?"inventory-service":"localhost";ServiceRegistry r=new ServiceRegistry().register("booking",URI.create("http://"+booking+":8091/")).register("payment",URI.create("http://"+payment+":8092/")).register("guest",URI.create("http://"+guest+":8093/")).register("inventory",URI.create("http://"+inventory+":8094/"));
        Path data=Path.of(System.getenv().getOrDefault("AUTH_DATA_DIR",System.getProperty("user.home")+"/.ashkan-hotelsphere"));HotelEdgeGateway g=new HotelEdgeGateway(port,r,new JwtTokenService(secret,3600),new AuthService(data));Runtime.getRuntime().addShutdownHook(new Thread(g::close));g.start();System.out.println("Authenticated Hotel Edge Gateway listening on "+g.port());
    }
}
