package com.ashkan.hotel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;

/** Pure invoice calculations. Currency is intentionally unspecified until hotel settings exist. */
public final class InvoiceService {
    private InvoiceService() { }

    public static BigDecimal total(HotelApp.Booking booking, HotelApp.Room room) {
        long nights = ChronoUnit.DAYS.between(booking.start, booking.end);
        if (nights <= 0 || !Double.isFinite(room.rate()) || room.rate() < 0) {
            throw new IllegalArgumentException("Invalid stay duration or room rate");
        }
        return BigDecimal.valueOf(room.rate()).multiply(BigDecimal.valueOf(nights))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static String render(HotelApp.Booking booking, HotelApp.Room room, HotelApp.Guest guest) {
        long nights = ChronoUnit.DAYS.between(booking.start, booking.end);
        return "ASHKAN HOTELSPHERE - PRO FORMA INVOICE\n"
            + "Booking: " + booking.id + "\nGuest: " + guest.name() + "\n"
            + "Room: " + room.id() + " (" + room.type() + ")\n"
            + "Arrival: " + booking.start + "\nDeparture: " + booking.end + "\n"
            + "Nights: " + nights + "\nRate per night: " + room.rate() + "\n"
            + "Estimated accommodation total: " + total(booking, room) + "\n"
            + "Currency, taxes, discounts and payment status are NOT configured.\n"
            + "This is an estimate, not a tax invoice or payment receipt.\n";
    }
}
