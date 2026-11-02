package com.ashkan.hotel;

public final class RedisDistributedLockSmokeTest {
    public static void main(String[] args) {
        RedisCacheClient unavailable=new RedisCacheClient("127.0.0.1",1,"",0,40);
        RedisDistributedLock lock=RedisDistributedLock.tryAcquire(unavailable,"smoke:lock",500);
        if(lock.acquired()) throw new AssertionError("Unavailable Redis cannot grant a lease");
        if(lock.token()==null||lock.token().isBlank()) throw new AssertionError("Lease token must be unique and non-empty");
        lock.close();
        System.out.println("RedisDistributedLockSmokeTest: PASS");
    }
}
