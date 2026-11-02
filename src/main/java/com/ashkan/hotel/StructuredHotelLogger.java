package com.ashkan.hotel;

import java.time.Instant;
import java.util.Map;

/** Small JSON-line logger; suitable for Docker stdout collection. */
public final class StructuredHotelLogger {
    private StructuredHotelLogger() {}
    public static void info(String service,String event,String traceId,Map<String,?> fields){write("INFO",service,event,traceId,fields,null);}
    public static void error(String service,String event,String traceId,Throwable error){write("ERROR",service,event,traceId,Map.of(),error);}
    private static synchronized void write(String level,String service,String event,String traceId,Map<String,?> fields,Throwable error){
        StringBuilder b=new StringBuilder("{\"timestamp\":").append(q(Instant.now().toString())).append(",\"level\":").append(q(level)).append(",\"service\":").append(q(service)).append(",\"event\":").append(q(event)).append(",\"trace_id\":").append(q(traceId));
        fields.forEach((k,v)->b.append(',').append(q(k)).append(':').append(v instanceof Number||v instanceof Boolean?v:q(String.valueOf(v))));
        if(error!=null)b.append(",\"error_type\":").append(q(error.getClass().getSimpleName())).append(",\"error_message\":").append(q(String.valueOf(error.getMessage())));
        System.out.println(b.append('}'));
    }
    private static String q(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
}
