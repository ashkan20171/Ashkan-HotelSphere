package com.ashkan.hotel;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Dependency-free W3C trace-context propagation with asynchronous OTLP/HTTP JSON export. */
public final class HotelTracing {
    public record Context(String traceId, String spanId, String traceFlags) {
        public String traceparent() { return "00-" + traceId + "-" + spanId + "-" + traceFlags; }
    }
    public record Span(Context context, String parentSpanId, String service, String operation,
                       long startEpochNanos, long startNanos) {}
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).build();
    private HotelTracing() {}

    public static Optional<Context> parse(String value) {
        if (value == null || !value.matches("(?i)00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) return Optional.empty();
        String[] p=value.toLowerCase(Locale.ROOT).split("-");
        if (p[1].chars().allMatch(c->c=='0') || p[2].chars().allMatch(c->c=='0')) return Optional.empty();
        return Optional.of(new Context(p[1],p[2],p[3]));
    }
    public static Context root() { return new Context(randomHex(16), randomHex(8), "01"); }
    public static Context child(Context parent) { return new Context(parent.traceId(), randomHex(8), parent.traceFlags()); }
    public static Span start(String service, String operation, String incomingTraceparent) {
        Optional<Context> parsed=parse(incomingTraceparent);
        Context parent=parsed.orElse(null); Context current=parent==null?root():child(parent);
        return new Span(current,parent==null?"":parent.spanId(),service,operation,System.currentTimeMillis()*1_000_000L,System.nanoTime());
    }
    public static void end(Span span, int status, Throwable error) {
        String endpoint=System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "");
        if(endpoint.isBlank()) return;
        long endEpoch=System.currentTimeMillis()*1_000_000L;
        long duration=Math.max(0,System.nanoTime()-span.startNanos());
        String body=otlpJson(span,status,error,endEpoch);
        try {
            HttpRequest req=HttpRequest.newBuilder(URI.create(endpoint)).timeout(java.time.Duration.ofSeconds(3))
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            CLIENT.sendAsync(req,HttpResponse.BodyHandlers.discarding()).exceptionally(ex->{StructuredHotelLogger.error(span.service(),"otlp_export_failed",span.context().traceId(),ex);return null;});
        } catch(Exception ex) { StructuredHotelLogger.error(span.service(),"otlp_export_failed",span.context().traceId(),ex); }
    }
    static String otlpJson(Span s,int status,Throwable error,long endEpoch) {
        String attrs="[{\"key\":\"http.request.method\",\"value\":{\"stringValue\":"+q(s.operation().split(" ",2)[0])+"}},{\"key\":\"url.path\",\"value\":{\"stringValue\":"+q(s.operation().contains(" ")?s.operation().substring(s.operation().indexOf(' ')+1):"/")+"}},{\"key\":\"http.response.status_code\",\"value\":{\"intValue\":"+status+"}}";
        if(error!=null) attrs+=",{\"key\":\"exception.type\",\"value\":{\"stringValue\":"+q(error.getClass().getName())+"}}";
        attrs+="]";
        return "{\"resourceSpans\":[{\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":"+q(s.service())+"}}]},\"scopeSpans\":[{\"scope\":{\"name\":\"ashkan-hotelsphere\",\"version\":\"1.0\"},\"spans\":[{\"traceId\":\""+s.context().traceId()+"\",\"spanId\":\""+s.context().spanId()+"\",\"parentSpanId\":"+(s.parentSpanId().isEmpty()?"\"\"":"\""+s.parentSpanId()+"\"")+",\"name\":"+q(s.operation())+",\"kind\":\"SPAN_KIND_SERVER\",\"startTimeUnixNano\":\""+s.startEpochNanos()+"\",\"endTimeUnixNano\":\""+endEpoch+"\",\"attributes\":"+attrs+",\"status\":{\"code\":"+(error==null?"STATUS_CODE_UNSET":"STATUS_CODE_ERROR")+"}}]}]}]}";
    }
    private static String randomHex(int bytes){byte[] b=new byte[bytes];new java.security.SecureRandom().nextBytes(b);StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x));return s.toString();}
    private static String q(String s){return "\""+Objects.toString(s,"").replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
}
