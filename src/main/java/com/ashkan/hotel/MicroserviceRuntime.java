package com.ashkan.hotel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;

/** Small independently deployable service host with isolated append-safe file persistence. */
public final class MicroserviceRuntime implements AutoCloseable {
    private final String serviceName, resourceName, eventType;
    private final Path dataFile;
    private final ServiceDataStore store;
    private final DomainApiV2 domainApi;
    private final CommercialApiV2 commercialApi;
    private final BookingOperationsApiV2 bookingOperationsApi;
    private final HttpServer server;
    private final Map<String, String> records = new LinkedHashMap<>();
    private final Object lock = new Object();

    public MicroserviceRuntime(String serviceName, String resourceName, String eventType, int port, Path dataFile) throws Exception {
        this.serviceName = required(serviceName); this.resourceName = required(resourceName); this.eventType = required(eventType);
        this.dataFile = Objects.requireNonNull(dataFile);
        String jdbcUrl=System.getenv("JDBC_URL");
        if(jdbcUrl!=null && !jdbcUrl.isBlank()) {
            this.store=new PostgresMicroserviceStore(serviceName,jdbcUrl,System.getenv().getOrDefault("DB_USER","hotel"),System.getenv().getOrDefault("DB_PASSWORD","change-me"),Integer.parseInt(System.getenv().getOrDefault("DB_POOL_SIZE","8")));
            this.store.initialize(); loadFromStore();
        } else { this.store=null; load(); }
        this.domainApi = jdbcUrl==null || jdbcUrl.isBlank() ? null : new DomainApiV2(serviceName,jdbcUrl,System.getenv().getOrDefault("DB_USER","hotel"),System.getenv().getOrDefault("DB_PASSWORD","change-me"),Integer.parseInt(System.getenv().getOrDefault("DB_POOL_SIZE","8")));
        this.commercialApi = jdbcUrl==null || jdbcUrl.isBlank() || !"booking".equals(serviceName) ? null : new CommercialApiV2(jdbcUrl,System.getenv().getOrDefault("DB_USER","hotel"),System.getenv().getOrDefault("DB_PASSWORD","change-me"),Integer.parseInt(System.getenv().getOrDefault("DB_POOL_SIZE","8")));
        this.bookingOperationsApi = jdbcUrl==null || jdbcUrl.isBlank() || !"booking".equals(serviceName) ? null : new BookingOperationsApiV2(jdbcUrl,System.getenv().getOrDefault("DB_USER","hotel"),System.getenv().getOrDefault("DB_PASSWORD","change-me"),Integer.parseInt(System.getenv().getOrDefault("DB_POOL_SIZE","8")));
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        instrument(server.createContext("/health", x -> { if (!method(x,"GET")) return; try { if(store!=null)store.ids(); respond(x,200,"{\"service\":"+q(this.serviceName)+",\"status\":\"UP\",\"database\":"+q(store==null?"file":"postgresql")+"}"); } catch(Exception e) { respond(x,503,"{\"service\":"+q(this.serviceName)+",\"status\":\"DEGRADED\",\"database\":\"DOWN\"}"); } }));
        instrument(server.createContext("/metrics", x -> { if (!method(x,"GET")) return; byte[] metrics=HotelTelemetry.global().prometheus(this.serviceName).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","text/plain; version=0.0.4; charset=utf-8");x.sendResponseHeaders(200,metrics.length);try(OutputStream o=x.getResponseBody()){o.write(metrics);} }));
        instrument(server.createContext("/api/v1/"+this.resourceName, this::handleCollection));
        instrument(server.createContext("/api/v2/", x -> { if(domainApi==null) { respond(x,503,"{\"error\":\"domain_api_requires_postgresql\"}"); return; } if(bookingOperationsApi!=null && bookingOperationsApi.supports(x.getRequestURI().getPath())) bookingOperationsApi.handle(x); else if(commercialApi!=null && commercialApi.supports(x.getRequestURI().getPath())) commercialApi.handle(x); else domainApi.handle(x); }));
        server.setExecutor(Executors.newFixedThreadPool(4, r -> { Thread t=new Thread(r,"microservice-"+this.serviceName);t.setDaemon(true);return t; }));
    }
    private void instrument(com.sun.net.httpserver.HttpContext context){context.getFilters().add(HotelTelemetry.global().requestFilter(serviceName));}
    public void start(){server.start();} public int port(){return server.getAddress().getPort();}
    private void handleCollection(HttpExchange x) throws IOException {
        String path=x.getRequestURI().getPath(); String base="/api/v1/"+resourceName;
        if(path.equals(base)||path.equals(base+"/")) {
            if(x.getRequestMethod().equals("GET")){List<String> ids; synchronized(lock){ids=new ArrayList<>(records.keySet());}StringJoiner j=new StringJoiner(",","[","]");ids.forEach(id->j.add(q(id)));respond(x,200,j.toString());return;}
            if(x.getRequestMethod().equals("POST")){String body=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);if(body.isBlank()||body.length()>1_000_000){respond(x,400,"{\"error\":\"empty_or_oversized_body\"}");return;}String id=UUID.randomUUID().toString();try{synchronized(lock){if(store!=null)store.insert(id,body);records.put(id,body);if(store==null)persist();}}catch(Exception e){synchronized(lock){records.remove(id);}respond(x,500,"{\"error\":\"persistence_failed\"}");return;}publishEvent(id);respond(x,201,"{\"id\":"+q(id)+",\"status\":\"created\"}");return;}
            method(x,"GET");return;
        }
        if(path.startsWith(base+"/")){String id;try{id=URLDecoder.decode(path.substring((base+"/").length()),StandardCharsets.UTF_8);}catch(Exception e){respond(x,400,"{\"error\":\"invalid_id\"}");return;}synchronized(lock){String value=records.get(id);if(value==null && store!=null){try{value=store.get(id);if(value!=null)records.put(id,value);}catch(Exception e){respond(x,503,"{\"error\":\"database_unavailable\"}");return;}}if(value==null){respond(x,404,"{\"error\":\"not_found\"}");return;}if(x.getRequestMethod().equals("GET")){respond(x,200,"{\"id\":"+q(id)+",\"data\":"+q(value)+"}");return;}if(x.getRequestMethod().equals("DELETE")){try{if(store!=null)store.delete(id);records.remove(id);if(store==null)persist();}catch(Exception e){respond(x,503,"{\"error\":\"persistence_failed\"}");return;}respond(x,200,"{\"deleted\":true}");return;}}method(x,"GET");return;}
        respond(x,404,"{\"error\":\"not_found\"}");
    }
    private void publishEvent(String id){if(!Boolean.parseBoolean(System.getenv().getOrDefault("EVENTS_ENABLED","false")))return;String payload="service="+urlEnc(serviceName)+"&entityId="+urlEnc(id);try(RabbitMqAmqpTransport transport=new RabbitMqAmqpTransport(RabbitMqAmqpTransport.Config.fromEnvironment())){transport.publish(new RabbitMqAmqpTransport.HotelEvent(UUID.randomUUID().toString(),eventType,id,payload,java.time.LocalDateTime.now()));}catch(Exception e){System.err.println("Event publication failed for "+serviceName+"/"+id+": "+e.getMessage());}}
    private void loadFromStore() throws Exception {
        List<String> existing=store.ids();
        // One-time import from the previous TSV persistence format when the new database is empty.
        if(existing.isEmpty() && Files.exists(dataFile)) {
            for(String line:Files.readAllLines(dataFile,StandardCharsets.UTF_8)) {
                String[] parts=line.split("\\t",2);
                if(parts.length==2) { String id=dec(parts[0]), payload=dec(parts[1]); store.insert(id,payload); }
            }
            existing=store.ids();
        }
        for(String id:existing){String payload=store.get(id);if(payload!=null)records.put(id,payload);}
    }
    private void load() throws IOException {if(!Files.exists(dataFile))return;for(String line:Files.readAllLines(dataFile,StandardCharsets.UTF_8)){String[] p=line.split("\\t",2);if(p.length==2)records.put(dec(p[0]),dec(p[1]));}}
    private void persist() throws IOException {Path parent=dataFile.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);Path tmp=dataFile.resolveSibling(dataFile.getFileName()+".tmp");StringBuilder b=new StringBuilder();records.forEach((k,v)->b.append(enc(k)).append('\t').append(enc(v)).append('\n'));Files.writeString(tmp,b,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(tmp,dataFile,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,dataFile,StandardCopyOption.REPLACE_EXISTING);}}
    private static String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}private static String dec(String s){return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}private static String q(String s){return "\""+Objects.toString(s,"").replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}private static String required(String s){if(s==null||s.isBlank())throw new IllegalArgumentException("Required value missing");return s;}private static boolean method(HttpExchange x,String m)throws IOException{if(x.getRequestMethod().equals(m))return true;respond(x,405,"{\"error\":\"method_not_allowed\"}");return false;}private static void respond(HttpExchange x,int code,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.getResponseHeaders().set("X-Hotel-Status",Integer.toString(code));x.sendResponseHeaders(code,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}private static String urlEnc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    @Override public void close(){server.stop(0);if(store!=null)try{store.close();}catch(Exception e){System.err.println("Store shutdown failed: "+e.getMessage());}if(domainApi!=null)domainApi.close();if(commercialApi!=null)commercialApi.close();if(bookingOperationsApi!=null)bookingOperationsApi.close();}
}
