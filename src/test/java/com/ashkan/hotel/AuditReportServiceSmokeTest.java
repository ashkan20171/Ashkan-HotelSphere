package com.ashkan.hotel;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;

public final class AuditReportServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        List<AuditService.Event> events = List.of(
                new AuditService.Event(1, "2026-10-01T10:00:00Z", "Bookings", "Created", "B-1", "New booking", "ashkan"),
                new AuditService.Event(2, "2026-10-12T10:00:00Z", "Finance", "Payment", "P-2", "=2+2", "reception"),
                new AuditService.Event(3, "2026-10-20T10:00:00Z", "Bookings", "Updated", "B-3", "Changed", "ASHKAN"));
        var filtered = AuditReportService.filter(events, new AuditReportService.Filter(
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-15"), "recept", "payment"));
        check(filtered.size() == 1 && filtered.get(0).id() == 2, "date, actor and query filters");
        var caseInsensitive = AuditReportService.filter(events, new AuditReportService.Filter(null, null, "ashkan", ""));
        check(caseInsensitive.size() == 2, "case-insensitive actor filter");
        try { new AuditReportService.Filter(LocalDate.parse("2026-10-15"), LocalDate.parse("2026-10-01"), "", ""); throw new AssertionError("invalid range accepted"); }
        catch (IllegalArgumentException expected) { }
        StringWriter writer = new StringWriter(); AuditReportService.writeCsv(filtered, writer);
        check(writer.toString().contains("'=2+2"), "CSV formula injection guard");
        check(writer.toString().contains("\"P-2\""), "CSV serialization");
        System.out.println("AuditReportServiceSmokeTest PASS");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
