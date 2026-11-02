package com.ashkan.hotel;

/** Environment-backed cache settings with safe defaults. */
public final class CacheSettings {
 private CacheSettings() {}
 public static long ttlMillis(){try{long seconds=Long.parseLong(System.getenv().getOrDefault("HOTEL_CACHE_TTL_SECONDS","30"));return Math.max(1_000L,Math.min(3_600_000L,Math.multiplyExact(seconds,1_000L)));}catch(RuntimeException ex){return 30_000L;}}
 public static int maxEntries(){try{return Math.max(100,Math.min(10_000,Integer.parseInt(System.getenv().getOrDefault("HOTEL_CACHE_MAX_ENTRIES","1000"))));}catch(RuntimeException ex){return 1000;}}
}
