package com.ashkan.hotel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PeriodReportServiceSmokeTest {
    public static void main(String[] args) {
        LocalDate from = LocalDate.of(2026, 10, 1);
        var rooms = List.of(new HotelApp.Room("101", "Standard", 100), new HotelApp.Room("102", "Suite", 200));
        var bookings = List.of(
                new HotelApp.Booking("b1", "101", "g1", from, from.plusDays(2), "CHECKED_IN"),
                new HotelApp.Booking("b2", "102", "g2", from.plusDays(1), from.plusDays(3), "CONFIRMED"),
                new HotelApp.Booking("b3", "101", "g3", from, from.plusDays(1), "CANCELLED"));
        var payments = List.of(
                new HotelApp.Payment("p1", "b1", new BigDecimal("50.00"), "Cash", LocalDateTime.of(2026,10,1,10,0)),
                new HotelApp.Payment("p2", "b1", new BigDecimal("25.00"), "Card", LocalDateTime.of(2026,10,2,10,0)),
                new HotelApp.Payment("p3", "b2", new BigDecimal("999.00"), "Cash", LocalDateTime.of(2026,9,30,10,0)));
        var report = PeriodReportService.summarize(rooms, bookings, payments, from, from.plusDays(1));
        if (report.daily().size() != 2) throw new AssertionError("Inclusive date range");
        if (report.daily().get(0).occupiedRooms() != 1 || report.daily().get(1).occupiedRooms() != 2) throw new AssertionError("Occupancy per day");
        if (report.bookingCount() != 2) throw new AssertionError("Overlapping non-cancelled bookings");
        if (report.roomNights() != 3) throw new AssertionError("Room nights");
        if (report.paymentsReceived().compareTo(new BigDecimal("75.00")) != 0) throw new AssertionError("Payments date filter");
        if (!PeriodReportService.toCsv(report).contains("2026-10-02,2,100,\"25.00\"")) throw new AssertionError("CSV rows");
        if (report.methodTotals().size() != 2) throw new AssertionError("Payment methods grouped");
        if (!report.methodTotals().get(0).method().equals("Cash") || report.methodTotals().get(0).amount().compareTo(new BigDecimal("50.00")) != 0)
            throw new AssertionError("Payment method totals sorted by amount");
        if (!PeriodReportService.toCsv(report).contains("Payment method,Amount,Transaction count")) throw new AssertionError("CSV includes method breakdown");
        if (PeriodReportService.paymentsByMethod(payments, from, from).size() != 1) throw new AssertionError("Payment method date filter");
        var cashOnly = PeriodReportService.summarize(rooms, bookings, payments, from, from.plusDays(1), "cash");
        if (cashOnly.paymentsReceived().compareTo(new BigDecimal("50.00")) != 0) throw new AssertionError("Case-insensitive method filter");
        if (cashOnly.methodTotals().size() != 1 || !cashOnly.methodTotals().get(0).method().equals("Cash")) throw new AssertionError("Filtered method breakdown");
        if (cashOnly.bookingCount() != report.bookingCount() || cashOnly.roomNights() != report.roomNights()) throw new AssertionError("Payment filter must not change booking/occupancy metrics");
        if (!PeriodReportService.summarize(rooms, bookings, payments, from, from.plusDays(1), "Transfer").paymentsReceived().equals(BigDecimal.ZERO)) throw new AssertionError("Unknown method should produce zero received");
        expectFailure(() -> PeriodReportService.summarize(rooms, bookings, payments, from.plusDays(1), from));
        expectFailure(() -> PeriodReportService.summarize(rooms, bookings, payments, from, from.plusDays(366)));
        System.out.println("PeriodReportServiceSmokeTest PASSED");
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("Expected validation failure"); }
        catch (IllegalArgumentException expected) { /* expected */ }
    }
}
