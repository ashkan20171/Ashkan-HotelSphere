package com.ashkan.hotel;

public final class TtlCacheSmokeTest {
 public static void main(String[] args) throws Exception {
  TtlCache<String,String> cache=new TtlCache<>(40,2);
  if(cache.get("missing")!=null)throw new AssertionError("Missing key must return null");
  cache.put("a","one"); if(!"one".equals(cache.get("a")))throw new AssertionError("Cache hit failed");
  cache.put("b","two"); cache.put("c","three"); if(cache.size()>2)throw new AssertionError("Cache exceeded capacity");
  cache.invalidate("c"); if(cache.get("c")!=null)throw new AssertionError("Invalidation failed");
  cache.put("ttl","value"); Thread.sleep(70); if(cache.get("ttl")!=null)throw new AssertionError("TTL expiration failed");
  if(TtlCache.hits()<1||TtlCache.misses()<2||TtlCache.evictions()<1)throw new AssertionError("Cache metrics not updated");
  System.out.println("TtlCacheSmokeTest: PASS");
 }
}
