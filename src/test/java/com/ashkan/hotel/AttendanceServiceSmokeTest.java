package com.ashkan.hotel;

import java.nio.file.*;
import java.time.*;

public final class AttendanceServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("attendance-test"); Path file = dir.resolve("attendance.tsv");
        MutableClock clock = new MutableClock(Instant.parse("2026-10-30T08:12:00Z"), ZoneOffset.UTC);
        AttendanceService service = new AttendanceService(file, clock);
        var shift = new AttendanceService.Shift("Morning", LocalTime.of(8,0), LocalTime.of(16,0));
        var started = service.clockIn("ashkan", shift);
        check(started.lateMinutes() == 12, "late minutes");
        expect(IllegalStateException.class, () -> service.clockIn("ASHKAN", shift));
        clock.set(Instant.parse("2026-10-30T16:25:00Z"));
        var ended = service.clockOut("ashkan");
        check(ended.workedMinutes() == 493, "worked minutes");
        check(ended.overtimeMinutes() == 25, "overtime minutes");
        check(service.forMonth(YearMonth.of(2026,10), "ASHKAN").size() == 1, "monthly filter");
        check(AttendanceService.csv(service.all()).contains("OvertimeMinutes"), "CSV header");
        AttendanceService reloaded = new AttendanceService(file, clock);
        check(reloaded.all().size() == 1 && reloaded.today("ashkan").orElseThrow().checkOut() != null, "persistence");
        System.out.println("AttendanceServiceSmokeTest: PASS");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void expect(Class<? extends Throwable> type, Throwing action) throws Exception { try { action.run(); throw new AssertionError("Expected " + type.getSimpleName()); } catch (Throwable ex) { if (!type.isInstance(ex)) throw ex; } }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }
    private static final class MutableClock extends Clock {
        private Instant instant; private final ZoneId zone;
        MutableClock(Instant instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
        void set(Instant value) { instant = value; }
        public ZoneId getZone() { return zone; }
        public Clock withZone(ZoneId z) { return new MutableClock(instant, z); }
        public Instant instant() { return instant; }
    }
}
