package com.ashkan.hotel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
public class FinanceServiceSmokeTest {
    public static void main(String[] args) {
        var room = new HotelApp.Room("101", "Standard", 120);
        var booking = new HotelApp.Booking("B1", "101", "G1", LocalDate.of(2026,10,1), LocalDate.of(2026,10,3), "CONFIRMED");
        var payment = new HotelApp.Payment("P1", "B1", new BigDecimal("100.00"), "CASH", LocalDateTime.now());
        var result = FinanceService.balances(List.of(booking), List.of(room), List.of(payment));
        if (result.size() != 1 || result.get(0).outstanding().compareTo(new BigDecimal("140.00")) != 0) throw new AssertionError("Wrong balance");
        booking.status = "CANCELLED";
        if (!FinanceService.balances(List.of(booking), List.of(room), List.of(payment)).isEmpty()) throw new AssertionError("Cancelled booking counted");
        System.out.println("FinanceService smoke tests passed");
    }
}
