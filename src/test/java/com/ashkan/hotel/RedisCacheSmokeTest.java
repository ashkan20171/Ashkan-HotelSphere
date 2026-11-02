package com.ashkan.hotel;

/** Dependency-free checks for cache payload safety and Redis fail-open behavior. */
public final class RedisCacheSmokeTest {
    public static void main(String[] args) {
        String packed=CacheCodec.pack("guest|1","Ashkan",null,"name.with.dots","فارسی");
        String[] decoded=CacheCodec.unpack(packed,5);
        if(!"guest|1".equals(decoded[0])||!"Ashkan".equals(decoded[1])||decoded[2]!=null||!"name.with.dots".equals(decoded[3])||!"فارسی".equals(decoded[4]))throw new AssertionError("Cache codec round-trip failed");
        boolean rejected=false;try{CacheCodec.unpack(packed,4);}catch(IllegalArgumentException expected){rejected=true;}if(!rejected)throw new AssertionError("Invalid cache payload must be rejected");
        RedisCacheClient unavailable=new RedisCacheClient("127.0.0.1",1,"",0,50);
        if(unavailable.get("smoke:key")!=null)throw new AssertionError("Unavailable Redis must fail open as a cache miss");
        if(RedisCacheClient.errors()<1)throw new AssertionError("Redis failure metric was not incremented");
        if(unavailable.set("smoke:key","value",5))throw new AssertionError("Unavailable Redis SET must fail safely");
        System.out.println("RedisCacheSmokeTest: PASS");
    }
}
