package com.ashkan.hotel;

import java.util.UUID;

/** Best-effort Redis lease. PostgreSQL constraints/transactions remain the correctness boundary. */
public final class RedisDistributedLock implements AutoCloseable {
    private final RedisCacheClient redis;
    private final String key;
    private final String token;
    private final boolean acquired;

    private RedisDistributedLock(RedisCacheClient redis, String key, String token, boolean acquired) {
        this.redis = redis; this.key = key; this.token = token; this.acquired = acquired;
    }

    public static RedisDistributedLock tryAcquire(RedisCacheClient redis, String key, int leaseMillis) {
        String token = UUID.randomUUID().toString();
        boolean acquired=redis.acquireLock(key, token, leaseMillis); ConcurrencyTelemetry.lock(acquired); return new RedisDistributedLock(redis, key, token, acquired);
    }

    public boolean acquired() { return acquired; }
    public String token() { return token; }
    @Override public void close() { if (acquired) redis.releaseLock(key, token); }
}
