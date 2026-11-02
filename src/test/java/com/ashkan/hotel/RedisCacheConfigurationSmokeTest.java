package com.ashkan.hotel;

import java.nio.file.*;

public final class RedisCacheConfigurationSmokeTest {
    public static void main(String[] args)throws Exception{
        String compose=Files.readString(Path.of("infra/docker-compose.yml"));
        for(String token:new String[]{"image: redis:7.4-alpine","redis_data:/data","REDIS_HOST: redis","REDIS_CACHE_ENABLED","condition: service_healthy"})if(!compose.contains(token))throw new AssertionError("Missing Redis compose configuration: "+token);
        String env=Files.readString(Path.of(".env.example"));if(!env.contains("REDIS_PASSWORD=")||!env.contains("REDIS_TIMEOUT_MS="))throw new AssertionError("Missing Redis environment examples");
        System.out.println("RedisCacheConfigurationSmokeTest: PASS");
    }
}
