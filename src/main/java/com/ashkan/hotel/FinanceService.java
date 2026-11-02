package com.ashkan.hotel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Read-only financial projection. Cancelled bookings are excluded from receivables. */
public final class FinanceService {
    private FinanceService() {}
    public record Balance(String bookingId, String status, BigDecimal total, BigDecimal paid, BigDecimal outstanding) {}
    public static List<Balance> balances(List<HotelApp.Booking> bookings, List<HotelApp.Room> rooms, List<HotelApp.Payment> payments) {
        List<Balance> result = new ArrayList<>();
        for (HotelApp.Booking booking : bookings) {
            if ("CANCELLED".equals(booking.status)) continue;
            HotelApp.Room room = rooms.stream().filter(r -> r.id().equals(booking.roomId)).findFirst().orElse(null);
            if (room == null) continue;
            BigDecimal total = InvoiceService.total(booking, room);
            BigDecimal paid = payments.stream().filter(p -> p.bookingId().equals(booking.id))
                    .map(HotelApp.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new Balance(booking.id, booking.status, total, paid, total.subtract(paid)));
        }
        return List.copyOf(result);
    }
}
