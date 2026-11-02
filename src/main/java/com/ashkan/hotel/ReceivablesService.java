package com.ashkan.hotel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure business logic for outstanding booking balances and aging buckets. */
public final class ReceivablesService {
    private ReceivablesService() {}
    public record Entry(String bookingId, String roomId, String guestId, LocalDate dueDate,
                        BigDecimal balance, String bucket) {}

    public static List<Entry> calculate(List<HotelApp.Booking> bookings, List<HotelApp.Room> rooms,
                                        List<HotelApp.Payment> payments, LocalDate asOf) {
        Objects.requireNonNull(asOf);
        List<Entry> result = new ArrayList<>();
        for (HotelApp.Booking booking : bookings) {
            if ("CANCELLED".equals(booking.status)) continue;
            HotelApp.Room room = rooms.stream().filter(r -> r.id().equals(booking.roomId)).findFirst().orElse(null);
            if (room == null) continue;
            long nights = ChronoUnit.DAYS.between(booking.start, booking.end);
            if (nights <= 0) continue;
            BigDecimal total = BigDecimal.valueOf(room.rate()).multiply(BigDecimal.valueOf(nights));
            BigDecimal paid = payments.stream().filter(p -> p.bookingId().equals(booking.id))
                    .map(HotelApp.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal balance = total.subtract(paid);
            if (balance.signum() <= 0) continue;
            long days = ChronoUnit.DAYS.between(booking.end, asOf);
            String bucket = days <= 0 ? "Not due" : days <= 30 ? "1-30 days" : days <= 60 ? "31-60 days" : "61+ days";
            result.add(new Entry(booking.id, booking.roomId, booking.guestId, booking.end, balance, bucket));
        }
        result.sort(Comparator.comparing(Entry::dueDate).thenComparing(Entry::bookingId));
        return List.copyOf(result);
    }
}
