package com.ashkan.hotel;

public final class SessionTimeoutServiceSmokeTest {
    public static void main(String[] args) {
        SessionTimeoutService session = new SessionTimeoutService(900_000, 60_000, 1_000);
        check(!session.isExpired(900_999), "not expired before timeout");
        check(session.remainingMillis(840_999) == 60_001, "remaining time");
        check(!session.shouldWarn(840_999), "warning not early");
        check(session.shouldWarn(841_000), "warning threshold");
        check(!session.shouldWarn(842_000), "warning once per idle period");
        session.recordActivity(842_500);
        check(!session.isExpired(1_700_000), "activity resets timeout");
        check(!session.shouldWarn(1_682_499), "activity resets warning threshold");
        check(session.shouldWarn(1_682_500), "warning after new idle period");
        check(session.isExpired(1_742_500), "expires at timeout boundary");
        boolean invalid = false;
        try { new SessionTimeoutService(10, 10, 0); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "invalid warning configuration rejected");
        System.out.println("SessionTimeoutServiceSmokeTest PASSED");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
