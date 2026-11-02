package com.ashkan.hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only operational KPIs; no database or Swing dependencies. */
public final class AnalyticsService {
    private AnalyticsService() {}

    public static long occupiedRooms(List<HotelApp.Room> rooms, List<HotelApp.Booking> bookings, LocalDate date) {
        if (rooms == null || bookings == null || date == null) throw new IllegalArgumentException("Null input");
        Set<String> existing = rooms.stream().map(HotelApp.Room::id).collect(Collectors.toSet());
        return bookings.stream()
            .filter(b -> ("CONFIRMED".equals(b.status) || "CHECKED_IN".equals(b.status))
                && !date.isBefore(b.start) && date.isBefore(b.end)
                && existing.contains(b.roomId))
            .map(b -> b.roomId).distinct().count();
    }

    public static int occupancyPercent(List<HotelApp.Room> rooms, List<HotelApp.Booking> bookings, LocalDate date) {
        if (rooms == null || rooms.isEmpty()) return 0;
        return (int) Math.round(100.0 * occupiedRooms(rooms, bookings, date) / rooms.size());
    }
}
