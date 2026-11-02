package com.ashkan.hotel;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Lightweight dependency-free Prometheus metrics and request correlation. */
public final class HotelTelemetry {
    private static final HotelTelemetry GLOBAL = new HotelTelemetry();
    private final ConcurrentHashMap<String, LongAdder> requests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> failures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> durationMillis = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> inFlight = new ConcurrentHashMap<>();
    private HotelTelemetry() {}
    public static HotelTelemetry global() { return GLOBAL; }

    public Filter requestFilter(String service) {
        return new Filter() {
            @Override public String description() { return "HotelSphere request telemetry"; }
            @Override public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
                long start = System.nanoTime();
                String method = safe(exchange.getRequestMethod());
                String path = normalizePath(exchange.getRequestURI().getPath());
                String key = service + "|" + method + "|" + path;
                HotelTracing.Span span = HotelTracing.start(service, method + " " + path, exchange.getRequestHeaders().getFirst("traceparent"));
                String traceId = span.context().traceId();
                exchange.getResponseHeaders().set("X-Trace-Id", traceId);
                exchange.getResponseHeaders().set("X-Trace-Parent", span.context().traceparent());
                exchange.getResponseHeaders().set("traceparent", span.context().traceparent());
                inFlight.computeIfAbsent(service, k -> new LongAdder()).increment();
                int status = 500;
                try {
                    chain.doFilter(exchange);
                    // HttpServer does not expose the final status; handlers set this attribute header.
                    String supplied = exchange.getResponseHeaders().getFirst("X-Hotel-Status");
                    if (supplied != null) try { status = Integer.parseInt(supplied); } catch (NumberFormatException ignored) {}
                    else status = 200;
                } catch (IOException | RuntimeException e) {
                    StructuredHotelLogger.error(service, "http_request_failed", traceId, e);
                    status = 500;
                    throw e;
                } finally {
                    long elapsed = Math.max(0, (System.nanoTime() - start) / 1_000_000);
                    final int finalStatus = status;
                    if (finalStatus >= 500) failures.computeIfAbsent(key, k -> new LongAdder()).increment();
                    requests.computeIfAbsent(key + "|" + finalStatus, k -> new LongAdder()).increment();
                    durationMillis.computeIfAbsent(key, k -> new LongAdder()).add(elapsed);
                    inFlight.computeIfAbsent(service, k -> new LongAdder()).decrement();
                    StructuredHotelLogger.info(service, "http_request_completed", traceId,
                            Map.of("method", method, "path", path, "status", finalStatus, "duration_ms", elapsed));
                    HotelTracing.end(span, finalStatus, finalStatus >= 500 ? new IllegalStateException("HTTP " + finalStatus) : null);
                }
            }
        };
    }

    public String prometheus(String service) {
        StringBuilder out = new StringBuilder();
        out.append("# HELP hotelsphere_http_requests_total Total HTTP requests.\n# TYPE hotelsphere_http_requests_total counter\n");
        requests.forEach((key, value) -> { String[] p=key.split("\\|",-1); if(p[0].equals(service)) out.append("hotelsphere_http_requests_total{service=\"").append(label(p[0])).append("\",method=\"").append(label(p[1])).append("\",path=\"").append(label(p[2])).append("\",status=\"").append(label(p[3])).append("\"} ").append(value.sum()).append('\n'); });
        out.append("# HELP hotelsphere_http_request_failures_total Failed HTTP handlers.\n# TYPE hotelsphere_http_request_failures_total counter\n");
        failures.forEach((key,value)->{String[] p=key.split("\\|",-1);if(p[0].equals(service))out.append("hotelsphere_http_request_failures_total{service=\"").append(label(p[0])).append("\",method=\"").append(label(p[1])).append("\",path=\"").append(label(p[2])).append("\"} ").append(value.sum()).append('\n');});
        out.append("# HELP hotelsphere_http_request_duration_milliseconds_sum Total request duration.\n# TYPE hotelsphere_http_request_duration_milliseconds_sum counter\n");
        durationMillis.forEach((key,value)->{String[] p=key.split("\\|",-1);if(p[0].equals(service))out.append("hotelsphere_http_request_duration_milliseconds_sum{service=\"").append(label(p[0])).append("\",method=\"").append(label(p[1])).append("\",path=\"").append(label(p[2])).append("\"} ").append(value.sum()).append('\n');});
        out.append("# HELP hotelsphere_http_in_flight_requests Current in-flight requests.\n# TYPE hotelsphere_http_in_flight_requests gauge\nhotelsphere_http_in_flight_requests{service=\"").append(label(service)).append("\"} ").append(inFlight.getOrDefault(service,new LongAdder()).sum()).append('\n');
        out.append("# HELP hotelsphere_cache_hits_total Total TTL cache hits.\n# TYPE hotelsphere_cache_hits_total counter\n").append("hotelsphere_cache_hits_total{service=\"").append(label(service)).append("\"} ").append(TtlCache.hits()).append('\n');
        out.append("# HELP hotelsphere_cache_misses_total Total TTL cache misses.\n# TYPE hotelsphere_cache_misses_total counter\n").append("hotelsphere_cache_misses_total{service=\"").append(label(service)).append("\"} ").append(TtlCache.misses()).append('\n');
        out.append("# HELP hotelsphere_cache_evictions_total Total TTL cache evictions.\n# TYPE hotelsphere_cache_evictions_total counter\n").append("hotelsphere_cache_evictions_total{service=\"").append(label(service)).append("\"} ").append(TtlCache.evictions()).append('\n');
        out.append("# HELP hotelsphere_cache_hit_ratio Cache hit ratio.\n# TYPE hotelsphere_cache_hit_ratio gauge\n").append("hotelsphere_cache_hit_ratio{service=\"").append(label(service)).append("\"} ").append(String.format(java.util.Locale.ROOT,"%.4f",TtlCache.hitRatio())).append('\n');
        out.append("# HELP hotelsphere_redis_cache_hits_total Redis cache hits.\n# TYPE hotelsphere_redis_cache_hits_total counter\nhotelsphere_redis_cache_hits_total{service=\"").append(label(service)).append("\"} ").append(RedisCacheClient.hits()).append('\n');
        out.append("# HELP hotelsphere_redis_cache_misses_total Redis cache misses.\n# TYPE hotelsphere_redis_cache_misses_total counter\nhotelsphere_redis_cache_misses_total{service=\"").append(label(service)).append("\"} ").append(RedisCacheClient.misses()).append('\n');
        out.append("# HELP hotelsphere_redis_cache_errors_total Redis cache errors (fail-open).\n# TYPE hotelsphere_redis_cache_errors_total counter\nhotelsphere_redis_cache_errors_total{service=\"").append(label(service)).append("\"} ").append(RedisCacheClient.errors()).append('\n');
        out.append(ConcurrencyTelemetry.prometheus());
        return out.toString();
    }
    private static String normalizePath(String p){if(p==null||p.isBlank())return "/";return p.replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}/", "/{id}/").replaceAll("/[0-9]+(?=/|$)","/{id}");}
    private static String safe(String s){return s==null?"UNKNOWN":s.replaceAll("[^A-Z]","");}
    private static String label(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ");}
}
