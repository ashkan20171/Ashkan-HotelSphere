package com.ashkan.hotel;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class HotelTelemetrySmokeTest {
    public static void main(String[] args) throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/probe",x->{x.getResponseHeaders().set("X-Hotel-Status","202");byte[] b="ok".getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(202,b.length);try(var o=x.getResponseBody()){o.write(b);}}).getFilters().add(HotelTelemetry.global().requestFilter("telemetry-test"));
        server.start();
        try {
            HttpResponse<String> response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/probe")).header("traceparent","00-0123456789abcdef0123456789abcdef-0123456789abcdef-01").GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=202)throw new AssertionError("status");
            if(!"0123456789abcdef0123456789abcdef".equals(response.headers().firstValue("X-Trace-Id").orElse("")))throw new AssertionError("trace id propagation");
            String metrics=HotelTelemetry.global().prometheus("telemetry-test");
            if(!metrics.contains("hotelsphere_http_requests_total")||!metrics.contains("status=\"202\"}")||!metrics.contains("hotelsphere_http_request_duration_milliseconds_sum"))throw new AssertionError("prometheus metrics");
        } finally {server.stop(0);}
        System.out.println("HotelTelemetrySmokeTest: PASS");
    }
}
