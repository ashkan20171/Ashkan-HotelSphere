package com.ashkan.hotel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/** Optional independently hosted HTTP microservice adapter for the customer-experience bounded context. */
public final class CustomerExperienceHttpService implements AutoCloseable {
    private final CustomerExperienceService service; private final HttpServer server;
    public CustomerExperienceHttpService(int port, CustomerExperienceService service) throws IOException {
        this.service=Objects.requireNonNull(service);server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.createContext("/health",x->respond(x,200,"{\"service\":\"customer-experience\",\"status\":\"UP\"}"));
        server.createContext("/api/v1/metrics",x->{if(!x.getRequestMethod().equals("GET")){respond(x,405,"{\"error\":\"method_not_allowed\"}");return;}var m=service.metrics();respond(x,200,String.format(Locale.ROOT,"{\"responses\":%d,\"csat\":%.2f,\"nps\":%.2f,\"averageOverall\":%.2f,\"openComplaints\":%d}",m.responses(),m.csat(),m.nps(),m.averageOverall(),m.openComplaints()));});
        server.createContext("/api/v1/feedback",x->{if(!x.getRequestMethod().equals("POST")){respond(x,405,"{\"error\":\"method_not_allowed\"}");return;}try{Map<String,String> p=params(x);var f=service.submitFeedback(p.get("bookingId"),p.get("guestName"),Integer.parseInt(p.get("cleanliness")),Integer.parseInt(p.get("reception")),Integer.parseInt(p.get("restaurant")),Integer.parseInt(p.get("facilities")),Integer.parseInt(p.get("overall")),Integer.parseInt(p.get("nps")),p.getOrDefault("comment",""),LocalDateTime.now());respond(x,201,"{\"id\":\""+f.id()+"\",\"status\":\"created\"}");}catch(Exception e){respond(x,400,"{\"error\":\""+json(e.getMessage())+"\"}");}});
        server.createContext("/api/v1/complaints",x->{if(!x.getRequestMethod().equals("POST")){respond(x,405,"{\"error\":\"method_not_allowed\"}");return;}try{Map<String,String> p=params(x);var c=service.createComplaint(p.get("bookingId"),p.get("guestName"),p.get("category"),p.get("subject"),p.getOrDefault("description",""),p.getOrDefault("assignedTo",""),LocalDateTime.now());respond(x,201,"{\"id\":\""+c.id()+"\",\"status\":\"OPEN\"}");}catch(Exception e){respond(x,400,"{\"error\":\""+json(e.getMessage())+"\"}");}});
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"customer-experience-http");t.setDaemon(true);return t;}));
    }
    public void start(){server.start();} public int port(){return server.getAddress().getPort();} @Override public void close(){server.stop(0);}
    private static Map<String,String> params(HttpExchange x)throws IOException{String body=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);Map<String,String> p=new HashMap<>();for(String pair:body.split("&")){int i=pair.indexOf('=');if(i>=0)p.put(URLDecoder.decode(pair.substring(0,i),StandardCharsets.UTF_8),URLDecoder.decode(pair.substring(i+1),StandardCharsets.UTF_8));}return p;}
    private static void respond(HttpExchange x,int code,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Access-Control-Allow-Origin","*");x.sendResponseHeaders(code,b.length);try(OutputStream o=x.getResponseBody()){o.write(b);}}
    private static String json(String s){return Objects.toString(s,"error").replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}
    public static void main(String[] args)throws Exception{int port=args.length==0?8088:Integer.parseInt(args[0]);CustomerExperienceHttpService api=new CustomerExperienceHttpService(port,new CustomerExperienceService());api.start();System.out.println("Customer Experience microservice listening on http://127.0.0.1:"+api.port());}
}
