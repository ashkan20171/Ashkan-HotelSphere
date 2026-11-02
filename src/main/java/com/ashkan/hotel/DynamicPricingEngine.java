package com.ashkan.hotel;

import java.math.*;
import java.time.*;
import java.util.*;

/** Deterministic, auditable rule-based pricing; no opaque ML or external price assumptions. */
public final class DynamicPricingEngine {
    public record Rules(BigDecimal baseRate, BigDecimal weekendMultiplier, BigDecimal highDemandMultiplier,
                        String currency, int maxOccupancy) {
        public Rules {
            Objects.requireNonNull(baseRate); Objects.requireNonNull(weekendMultiplier);
            Objects.requireNonNull(highDemandMultiplier);
            if (baseRate.signum() <= 0) throw new IllegalArgumentException("baseRate must be positive");
            if (weekendMultiplier.compareTo(new BigDecimal("0.5")) < 0 || weekendMultiplier.compareTo(new BigDecimal("5.0")) > 0) throw new IllegalArgumentException("weekendMultiplier out of range");
            if (highDemandMultiplier.compareTo(new BigDecimal("0.5")) < 0 || highDemandMultiplier.compareTo(new BigDecimal("5.0")) > 0) throw new IllegalArgumentException("highDemandMultiplier out of range");
            if (maxOccupancy < 1 || maxOccupancy > 20) throw new IllegalArgumentException("maxOccupancy out of range");
            currency = Objects.requireNonNullElse(currency, "EUR").toUpperCase(Locale.ROOT);
            if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Invalid currency");
        }
    }
    public record NightRate(LocalDate date, BigDecimal baseRate, BigDecimal weekendFactor,
                            BigDecimal demandFactor, BigDecimal finalRate) {}
    public record Quote(List<NightRate> nights, BigDecimal subtotal, BigDecimal total,
                        String currency, int numberOfNights, BigDecimal occupancyRatio) {}

    public Quote quote(Rules rules, LocalDate checkIn, LocalDate checkOut, int guests, BigDecimal occupancyRatio) {
        Objects.requireNonNull(rules); Objects.requireNonNull(checkIn); Objects.requireNonNull(checkOut);
        if (!checkOut.isAfter(checkIn)) throw new IllegalArgumentException("checkOut must be after checkIn");
        if (guests < 1 || guests > rules.maxOccupancy()) throw new IllegalArgumentException("Guest count exceeds rate plan occupancy");
        BigDecimal occupancy = Objects.requireNonNullElse(occupancyRatio, BigDecimal.ZERO);
        if (occupancy.signum() < 0 || occupancy.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException("occupancyRatio must be between 0 and 1");
        BigDecimal demand = occupancy.compareTo(new BigDecimal("0.80")) >= 0 ? rules.highDemandMultiplier() : BigDecimal.ONE;
        List<NightRate> nights = new ArrayList<>(); BigDecimal subtotal = BigDecimal.ZERO;
        for (LocalDate day = checkIn; day.isBefore(checkOut); day = day.plusDays(1)) {
            boolean weekend = day.getDayOfWeek() == DayOfWeek.FRIDAY || day.getDayOfWeek() == DayOfWeek.SATURDAY;
            BigDecimal weekendFactor = weekend ? rules.weekendMultiplier() : BigDecimal.ONE;
            BigDecimal amount = rules.baseRate().multiply(weekendFactor).multiply(demand).setScale(2, RoundingMode.HALF_UP);
            nights.add(new NightRate(day, rules.baseRate(), weekendFactor, demand, amount)); subtotal = subtotal.add(amount);
        }
        return new Quote(List.copyOf(nights), subtotal, subtotal, rules.currency(), nights.size(), occupancy.setScale(4, RoundingMode.HALF_UP));
    }
}
