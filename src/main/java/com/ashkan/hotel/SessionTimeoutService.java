package com.ashkan.hotel;

/** Tracks inactivity for a single authenticated desktop session. Time is supplied by the caller for deterministic tests. */
public final class SessionTimeoutService {
    private final long timeoutMillis;
    private final long warningMillis;
    private long lastActivityMillis;
    private boolean warningIssued;

    public SessionTimeoutService(long timeoutMillis, long warningMillis, long nowMillis) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout must be positive");
        if (warningMillis < 0 || warningMillis >= timeoutMillis) throw new IllegalArgumentException("Warning must be non-negative and shorter than timeout");
        this.timeoutMillis = timeoutMillis;
        this.warningMillis = warningMillis;
        this.lastActivityMillis = nowMillis;
    }

    public synchronized void recordActivity(long nowMillis) {
        lastActivityMillis = nowMillis;
        warningIssued = false;
    }

    public synchronized boolean isExpired(long nowMillis) {
        return elapsed(nowMillis) >= timeoutMillis;
    }

    /** Returns true once per idle period when the warning threshold is reached. */
    public synchronized boolean shouldWarn(long nowMillis) {
        long warningThreshold = timeoutMillis - warningMillis;
        if (!warningIssued && elapsed(nowMillis) >= warningThreshold && elapsed(nowMillis) < timeoutMillis) {
            warningIssued = true;
            return true;
        }
        return false;
    }

    public synchronized long remainingMillis(long nowMillis) {
        return Math.max(0L, timeoutMillis - elapsed(nowMillis));
    }

    private long elapsed(long nowMillis) {
        return Math.max(0L, nowMillis - lastActivityMillis);
    }
}
