package com.ashkan.hotel;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Small bounded, thread-safe TTL cache. Values must be non-null; Optional can represent misses. */
public final class TtlCache<K,V> {
    private record Entry<V>(V value,long expiresAtNanos) {}
    private static final LongAdder HITS=new LongAdder(), MISSES=new LongAdder(), EVICTIONS=new LongAdder();
    private final ConcurrentHashMap<K,Entry<V>> entries=new ConcurrentHashMap<>();
    private final long ttlNanos; private final int maxEntries;
    public TtlCache(long ttlMillis,int maxEntries){if(ttlMillis<1||maxEntries<1)throw new IllegalArgumentException("TTL and maxEntries must be positive");this.ttlNanos=Math.multiplyExact(ttlMillis,1_000_000L);this.maxEntries=maxEntries;}
    public V get(K key){Objects.requireNonNull(key);Entry<V> e=entries.get(key);if(e==null){MISSES.increment();return null;}if(System.nanoTime()-e.expiresAtNanos()>=0){if(entries.remove(key,e))EVICTIONS.increment();MISSES.increment();return null;}HITS.increment();return e.value();}
    public void put(K key,V value){Objects.requireNonNull(key);Objects.requireNonNull(value);if(entries.size()>=maxEntries&&!entries.containsKey(key))evictExpiredOrOldest();entries.put(key,new Entry<>(value,System.nanoTime()+ttlNanos));}
    public void invalidate(K key){if(key!=null)entries.remove(key);}
    public void clear(){entries.clear();}
    public int size(){purgeExpired();return entries.size();}
    private void purgeExpired(){long now=System.nanoTime();for(Map.Entry<K,Entry<V>> e:entries.entrySet())if(now-e.getValue().expiresAtNanos()>=0&&entries.remove(e.getKey(),e.getValue()))EVICTIONS.increment();}
    private void evictExpiredOrOldest(){purgeExpired();if(entries.size()<maxEntries)return;K oldest=null;long expiry=Long.MAX_VALUE;for(var e:entries.entrySet())if(e.getValue().expiresAtNanos()<expiry){expiry=e.getValue().expiresAtNanos();oldest=e.getKey();}if(oldest!=null&&entries.remove(oldest)!=null)EVICTIONS.increment();}
    public static long hits(){return HITS.sum();} public static long misses(){return MISSES.sum();} public static long evictions(){return EVICTIONS.sum();}
    public static double hitRatio(){long h=hits(),m=misses();return h+m==0?0.0:(double)h/(h+m);}
}
