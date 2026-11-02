package com.ashkan.hotel;

import java.time.LocalDate;
import java.util.List;

public final class AnalyticsServiceSmokeTest {
    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 10, 23);
        var rooms = List.of(new HotelApp.Room("101", "Standard", 120), new HotelApp.Room("102", "Suite", 200));
        var bookings = List.of(
            new HotelApp.Booking("1", "101", "A", today, today.plusDays(2), "CONFIRMED"),
            new HotelApp.Booking("2", "101", "B", today, today.plusDays(1), "CHECKED_IN"),
            new HotelApp.Booking("3", "102", "C", today, today.plusDays(1), "CANCELLED"),
            new HotelApp.Booking("4", "999", "D", today, today.plusDays(1), "CONFIRMED"));
        if (AnalyticsService.occupiedRooms(rooms, bookings, today) != 1) throw new AssertionError("Distinct existing rooms only");
        if (AnalyticsService.occupancyPercent(rooms, bookings, today) != 50) throw new AssertionError("Expected 50%");
        if (AnalyticsService.occupiedRooms(rooms, bookings, today.plusDays(2)) != 0) throw new AssertionError("Checkout is exclusive");
        if (AnalyticsService.occupancyPercent(List.of(), bookings, today) != 0) throw new AssertionError("Empty hotel");
        System.out.println("AnalyticsServiceSmokeTest PASSED");
    }
}
