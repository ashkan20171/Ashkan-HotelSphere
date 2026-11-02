package com.ashkan.hotel;
import java.time.*;
public final class GatewayRateLimiterSmokeTest {
 public static void main(String[] args){Clock c=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);GatewayRateLimiter l=new GatewayRateLimiter(2,1000,c);if(!l.allow("ip")||!l.allow("ip")||l.allow("ip"))throw new AssertionError("limit not enforced");Clock later=Clock.offset(c,Duration.ofSeconds(2));GatewayRateLimiter next=new GatewayRateLimiter(1,1000,later);if(!next.allow("ip"))throw new AssertionError("new window failed");System.out.println("GatewayRateLimiterSmokeTest passed");}
}
