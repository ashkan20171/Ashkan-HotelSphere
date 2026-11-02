package com.ashkan.hotel;

import java.time.LocalDate;

/** Shared domain-level concurrency policies; database constraints enforce them across instances. */
public final class ConcurrencyPolicy {
    private ConcurrencyPolicy() { }
    /** Hotel stays use a half-open interval [checkIn, checkOut), so same-day turnover is allowed. */
    public static boolean overlaps(LocalDate firstIn, LocalDate firstOut, LocalDate secondIn, LocalDate secondOut) {
        if(firstIn==null||firstOut==null||secondIn==null||secondOut==null) throw new IllegalArgumentException("Booking dates are required");
        if(!firstOut.isAfter(firstIn)||!secondOut.isAfter(secondIn)) throw new IllegalArgumentException("Check-out must be after check-in");
        return firstIn.isBefore(secondOut)&&secondIn.isBefore(firstOut);
    }
}
