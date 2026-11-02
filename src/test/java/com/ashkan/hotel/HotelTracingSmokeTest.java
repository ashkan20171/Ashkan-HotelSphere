package com.ashkan.hotel;

public final class HotelTracingSmokeTest {
    public static void main(String[] args) {
        String incoming="00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        HotelTracing.Context parent=HotelTracing.parse(incoming).orElseThrow();
        HotelTracing.Context child=HotelTracing.child(parent);
        if(!parent.traceId().equals(child.traceId())) throw new AssertionError("trace id must propagate");
        if(parent.spanId().equals(child.spanId())) throw new AssertionError("child span must be unique");
        if(HotelTracing.parse("00-00000000000000000000000000000000-00f067aa0ba902b7-01").isPresent()) throw new AssertionError("zero trace id accepted");
        if(HotelTracing.parse("bad-context").isPresent()) throw new AssertionError("invalid context accepted");
        HotelTracing.Span span=HotelTracing.start("booking","GET /api/v2/bookings",incoming);
        String json=HotelTracing.otlpJson(span,200,null,span.startEpochNanos()+1_000_000);
        if(!json.contains("resourceSpans")||!json.contains(parent.traceId())||!json.contains("SPAN_KIND_SERVER")) throw new AssertionError("invalid OTLP JSON payload");
        if(!span.parentSpanId().equals(parent.spanId())) throw new AssertionError("parent span missing");
        System.out.println("HotelTracingSmokeTest: PASS");
    }
}
