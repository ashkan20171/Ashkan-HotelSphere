package com.ashkan.hotel;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/** Small in-memory fixed-window limiter. Use a shared edge/WAF limiter for multi-replica deployments. */
final class GatewayRateLimiter {
    private record Window(long startedAt, int count) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit; private final long windowMillis; private final Clock clock;
    GatewayRateLimiter(int limit, long windowMillis) { this(limit, windowMillis, Clock.systemUTC()); }
    GatewayRateLimiter(int limit, long windowMillis, Clock clock) {
        if (limit < 1 || windowMillis < 1) throw new IllegalArgumentException("Invalid rate limit");
        this.limit=limit; this.windowMillis=windowMillis; this.clock=clock;
    }
    boolean allow(String key) {
        long now=clock.millis(); final boolean[] allowed={false};
        windows.compute(key,(k,w)->{
            if(w==null || now-w.startedAt()>=windowMillis){allowed[0]=true;return new Window(now,1);}
            if(w.count()<limit){allowed[0]=true;return new Window(w.startedAt(),w.count()+1);}
            return w;
        });
        if(windows.size()>10_000) windows.entrySet().removeIf(e->now-e.getValue().startedAt()>=windowMillis*2);
        return allowed[0];
    }
}
