package com.ashkan.hotel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
public final class ReceivablesServiceSmokeTest {
    public static void main(String[] args) {
        var b = new HotelApp.Booking("B1", "101", "G1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), "CHECKED_OUT");
        var r = new HotelApp.Room("101", "Standard", 100);
        var p = new HotelApp.Payment("P1", "B1", new BigDecimal("50"), "CASH", LocalDateTime.now());
        var entries = ReceivablesService.calculate(List.of(b), List.of(r), List.of(p), LocalDate.of(2026, 2, 5));
        if (entries.size() != 1 || entries.get(0).balance().compareTo(new BigDecimal("150")) != 0 || !entries.get(0).bucket().equals("31-60 days")) throw new AssertionError(entries);
        b.status = "CANCELLED";
        if (!ReceivablesService.calculate(List.of(b), List.of(r), List.of(p), LocalDate.of(2026, 2, 5)).isEmpty()) throw new AssertionError("Cancelled booking counted");
        System.out.println("Receivables smoke tests passed");
    }
}
