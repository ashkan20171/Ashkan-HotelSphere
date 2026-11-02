package com.ashkan.hotel;

import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/** Front-desk calendar and transactional booking lifecycle endpoints. */
final class BookingOperationsApiV2 implements AutoCloseable {
    private final BookingOperationsRepository repository;
    BookingOperationsApiV2(String url,String user,String password,int pool){repository=new BookingOperationsRepository(url,user,password,pool);}
    boolean supports(String path){return path.equals("/api/v2/booking-operations")||path.startsWith("/api/v2/booking-operations/");}
    void handle(HttpExchange x)throws IOException {
        try {
            String path=x.getRequestURI().getPath(),base="/api/v2/booking-operations",method=x.getRequestMethod();
            if(path.equals(base)){
                if(!"GET".equals(method)){send(x,405,Map.of("error","method_not_allowed"));return;}
                LocalDate from=LocalDate.parse(required(query(x,"from"),"from"));LocalDate to=LocalDate.parse(required(query(x,"to"),"to"));
                List<Map<String,Object>> items=repository.calendar(from,to,query(x,"roomType")).stream().map(e->{Map<String,Object> m=new LinkedHashMap<>();m.put("bookingId",e.bookingId());m.put("roomId",e.roomId());m.put("roomNumber",e.roomNumber());m.put("guestId",e.guestId());m.put("checkIn",e.checkIn().toString());m.put("checkOut",e.checkOut().toString());m.put("status",e.status());return m;}).toList();
                send(x,200,Map.of("from",from.toString(),"to",to.toString(),"items",items));return;
            }
            String tail=URLDecoder.decode(path.substring((base+"/").length()),StandardCharsets.UTF_8);String[] parts=tail.split("/");if(parts.length!=2){send(x,404,Map.of("error","not_found"));return;}
            String id=parts[0],action=parts[1];
            switch(action){
                case "check-in" -> {if(!"POST".equals(method)){send(x,405,Map.of("error","method_not_allowed"));return;}String status=repository.transition(id,"CHECKED_IN",LocalDate.now());send(x,200,Map.of("bookingId",id,"status",status));}
                case "check-out" -> {if(!"POST".equals(method)){send(x,405,Map.of("error","method_not_allowed"));return;}String status=repository.transition(id,"CHECKED_OUT",LocalDate.now());send(x,200,Map.of("bookingId",id,"status",status));}
                case "cancel" -> {if(!"POST".equals(method)){send(x,405,Map.of("error","method_not_allowed"));return;}var result=repository.cancel(id,LocalDate.now());send(x,200,Map.of("bookingId",result.bookingId(),"status",result.status(),"cancellationFee",result.fee(),"currency",result.currency(),"reason",result.reason()));}
                case "room" -> {if(!"PATCH".equals(method)){send(x,405,Map.of("error","method_not_allowed"));return;}Map<String,Object>b=ApiJson.object(new String(x.getRequestBody().readNBytes(65537),StandardCharsets.UTF_8));String roomId=ApiJson.str(b,"roomId",null);repository.reassignRoom(id,required(roomId,"roomId"));send(x,200,Map.of("bookingId",id,"roomId",roomId,"status","reassigned"));}
                default -> send(x,404,Map.of("error","not_found"));
            }
        }catch(IllegalArgumentException e){send(x,400,Map.of("error","validation_failed","message",Objects.toString(e.getMessage(),"Invalid request")));}
         catch(IllegalStateException e){send(x,409,Map.of("error","invalid_booking_transition","message",Objects.toString(e.getMessage(),"Conflict")));}
         catch(SQLException e){int status="23P01".equals(e.getSQLState())?409:500;send(x,status,Map.of("error",status==409?"room_unavailable":"booking_operation_failed","message",Objects.toString(e.getMessage(),"Database operation failed")));}
         catch(Exception e){send(x,500,Map.of("error","booking_operation_failed","message",Objects.toString(e.getMessage(),"Unexpected error")));}
    }
    private static String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" is required");return value;}
    private static String query(HttpExchange x,String key){String q=x.getRequestURI().getRawQuery();if(q==null)return null;for(String pair:q.split("&")){String[]p=pair.split("=",2);if(URLDecoder.decode(p[0],StandardCharsets.UTF_8).equals(key))return p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"";}return null;}
    private static void send(HttpExchange x,int status,Object body)throws IOException{byte[] bytes=ApiJson.write(body).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(status,bytes.length);try(OutputStream out=x.getResponseBody()){out.write(bytes);}}
    @Override public void close(){repository.close();}
}
