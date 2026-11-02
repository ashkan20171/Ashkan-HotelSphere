package com.ashkan.hotel;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/** Optional independently hosted notification microservice HTTP adapter. */
public final class NotificationHttpService implements AutoCloseable {
    private final NotificationService service; private final HttpServer server;
    public NotificationHttpService(int port, NotificationService service)throws IOException{
        this.service=Objects.requireNonNull(service);server=HttpServer.create(new InetSocketAddress(System.getenv().getOrDefault("HTTP_BIND_HOST","127.0.0.1"),port),0);
        server.createContext("/health",x->{if(!method(x,"GET"))return;respond(x,200,"{\"service\":\"notification\",\"status\":\"UP\"}");});
        server.createContext("/api/v1/metrics",x->{if(!method(x,"GET"))return;var m=service.metrics(LocalDateTime.now());respond(x,200,String.format(Locale.ROOT,"{\"queued\":%d,\"sent\":%d,\"failed\":%d,\"total\":%d,\"dueNow\":%d}",m.queued(),m.sent(),m.failed(),m.total(),m.dueNow()));});
        server.createContext("/api/v1/notifications",x->{if(!method(x,"POST"))return;try{Map<String,String> p=params(x);var n=service.enqueue(NotificationService.Channel.valueOf(p.get("channel").toUpperCase(Locale.ROOT)),p.get("recipient"),p.getOrDefault("subject",""),p.get("body"),LocalDateTime.now());respond(x,202,"{\"id\":\""+n.id()+"\",\"status\":\"QUEUED\"}");}catch(Exception e){respond(x,400,"{\"error\":\""+json(e.getMessage())+"\"}");}});
        server.createContext("/api/v1/notifications/process",x->{if(!method(x,"POST"))return;int count=service.processDue(LocalDateTime.now());respond(x,200,"{\"processed\":"+count+"}");});
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"notification-http");t.setDaemon(true);return t;}));
    }
    public void start(){server.start();} public int port(){return server.getAddress().getPort();} @Override public void close(){server.stop(0);}
    private static boolean method(HttpExchange x,String expected)throws IOException{if(x.getRequestMethod().equals(expected))return true;respond(x,405,"{\"error\":\"method_not_allowed\"}");return false;}
    private static Map<String,String> params(HttpExchange x)throws IOException{String body=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);Map<String,String> p=new HashMap<>();for(String pair:body.split("&")){int i=pair.indexOf('=');if(i>=0)p.put(URLDecoder.decode(pair.substring(0,i),StandardCharsets.UTF_8),URLDecoder.decode(pair.substring(i+1),StandardCharsets.UTF_8));}return p;}
    private static void respond(HttpExchange x,int code,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.sendResponseHeaders(code,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}
    private static String json(String s){return Objects.toString(s,"error").replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}
    public static void main(String[] args)throws Exception{int port=args.length==0?8089:Integer.parseInt(args[0]);NotificationHttpService api=new NotificationHttpService(port,new NotificationService());api.start();System.out.println("Notification microservice listening on http://127.0.0.1:"+api.port());}
}
