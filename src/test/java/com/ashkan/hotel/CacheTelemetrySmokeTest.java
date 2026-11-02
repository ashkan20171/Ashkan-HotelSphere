package com.ashkan.hotel;

public final class CacheTelemetrySmokeTest {
 public static void main(String[] args) {
  TtlCache<String,String> c=new TtlCache<>(1000,4);c.put("x","y");c.get("x");c.get("z");
  String metrics=HotelTelemetry.global().prometheus("cache-test");
  if(!metrics.contains("hotelsphere_cache_hits_total")||!metrics.contains("hotelsphere_cache_misses_total")||!metrics.contains("hotelsphere_cache_hit_ratio"))throw new AssertionError("Cache metrics missing from Prometheus output");
  System.out.println("CacheTelemetrySmokeTest: PASS");
 }
}
