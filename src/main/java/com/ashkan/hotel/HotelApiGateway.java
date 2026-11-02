package com.ashkan.hotel;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Lightweight edge gateway routing requests to independently hosted hotel services. */
public final class HotelApiGateway implements AutoCloseable {
    private final HttpServer server;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, URI> routes;
    public HotelApiGateway(int port, Map<String, URI> routes) throws IOException {
        this.routes = Map.copyOf(routes);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", x -> { if (!method(x,"GET")) return; respond(x,200,"{\"service\":\"hotel-api-gateway\",\"status\":\"UP\"}"); });
        server.createContext("/", this::proxy);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"hotel-api-gateway");t.setDaemon(true);return t;}));
    }
    public void start(){server.start();} public int port(){return server.getAddress().getPort();}
    private void proxy(HttpExchange x) throws IOException {
        String path=x.getRequestURI().getRawPath(); String prefix=routes.keySet().stream().filter(k->path.equals(k)||path.startsWith(k+"/")).max(Comparator.comparingInt(String::length)).orElse(null);
        if(prefix==null){respond(x,404,"{\"error\":\"route_not_found\"}");return;}
        URI target=routes.get(prefix).resolve(path.substring(prefix.length()).replaceFirst("^/", "") + (x.getRequestURI().getRawQuery()==null?"":"?"+x.getRequestURI().getRawQuery()));
        try {
            HttpRequest.Builder b=HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(15));
            x.getRequestHeaders().forEach((k,v)->{if(!Set.of("host","content-length","connection","expect","upgrade","transfer-encoding").contains(k.toLowerCase(Locale.ROOT)))b.header(k,String.join(",",v));});
            byte[] body=x.getRequestBody().readAllBytes(); b.method(x.getRequestMethod(), body.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
            HttpResponse<byte[]> response=client.send(b.build(),HttpResponse.BodyHandlers.ofByteArray());
            response.headers().firstValue("Content-Type").ifPresent(v->x.getResponseHeaders().set("Content-Type",v));
            byte[] bytes=response.body();x.sendResponseHeaders(response.statusCode(),bytes.length);try(OutputStream out=x.getResponseBody()){out.write(bytes);}
        } catch(InterruptedException e){Thread.currentThread().interrupt();respond(x,503,"{\"error\":\"upstream_interrupted\"}");}
          catch(Exception e){respond(x,502,"{\"error\":\"upstream_unavailable\",\"detail\":\""+json(e.getMessage())+"\"}");}
    }
    private static boolean method(HttpExchange x,String m)throws IOException{if(x.getRequestMethod().equals(m))return true;respond(x,405,"{\"error\":\"method_not_allowed\"}");return false;}
    private static void respond(HttpExchange x,int status,String value)throws IOException{byte[] b=value.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.sendResponseHeaders(status,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}
    private static String json(String s){return Objects.toString(s,"error").replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}
    @Override public void close(){server.stop(0);}
    public static void main(String[] args)throws Exception{
        int port=args.length==0?8080:Integer.parseInt(args[0]);Map<String,URI> routes=new LinkedHashMap<>();
        routes.put("/notifications",URI.create("http://127.0.0.1:8089/"));routes.put("/customer-experience",URI.create("http://127.0.0.1:8088/"));
        HotelApiGateway gateway=new HotelApiGateway(port,routes);gateway.start();System.out.println("Hotel API Gateway listening on http://127.0.0.1:"+gateway.port());
    }
}
