package com.ashkan.hotel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Date-bounded management report calculations independent of Swing and persistence. */
public final class PeriodReportService {
    private PeriodReportService() {}

    public record Daily(LocalDate date, long occupiedRooms, int occupancyPercent, BigDecimal paymentsReceived) {}
    public record MethodTotal(String method, BigDecimal amount, long transactionCount) {}
    public record Summary(LocalDate from, LocalDate through, int bookingCount, long roomNights,
                          BigDecimal paymentsReceived, BigDecimal outstanding, List<Daily> daily,
                          List<MethodTotal> methodTotals) {
        public Summary { daily = List.copyOf(daily); methodTotals = List.copyOf(methodTotals); }
    }

    /** The end date is inclusive. Payments are counted by their local creation date. */
    public static Summary summarize(List<HotelApp.Room> rooms, List<HotelApp.Booking> bookings,
                                    List<HotelApp.Payment> payments, LocalDate from, LocalDate through) {
        return summarize(rooms, bookings, payments, from, through, "");
    }

    /** Filters payment-derived metrics by method; blank means all methods. Booking/occupancy metrics remain unchanged. */
    public static Summary summarize(List<HotelApp.Room> rooms, List<HotelApp.Booking> bookings,
                                    List<HotelApp.Payment> payments, LocalDate from, LocalDate through, String paymentMethod) {
        if (rooms == null || bookings == null || payments == null || from == null || through == null)
            throw new IllegalArgumentException("Report inputs cannot be null");
        if (through.isBefore(from)) throw new IllegalArgumentException("End date must be on or after start date");
        long days = ChronoUnit.DAYS.between(from, through) + 1;
        if (days > 366) throw new IllegalArgumentException("Date range cannot exceed 366 days");

        String selectedMethod = paymentMethod == null ? "" : paymentMethod.trim();
        List<HotelApp.Payment> filteredPayments = payments.stream()
                .filter(p -> matchesMethod(p, selectedMethod)).toList();
        List<Daily> daily = new ArrayList<>();
        BigDecimal received = BigDecimal.ZERO;
        long roomNights = 0;
        for (LocalDate date = from; !date.isAfter(through); date = date.plusDays(1)) {
            long occupied = AnalyticsService.occupiedRooms(rooms, bookings, date);
            roomNights += occupied;
            LocalDate currentDate = date;
            BigDecimal dailyPaid = filteredPayments.stream()
                    .filter(p -> p != null && p.createdAt() != null && p.createdAt().toLocalDate().equals(currentDate))
                    .map(HotelApp.Payment::amount).filter(a -> a != null && a.signum() > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            received = received.add(dailyPaid);
            daily.add(new Daily(date, occupied, rooms.isEmpty() ? 0 : (int)Math.round(100.0 * occupied / rooms.size()), dailyPaid));
        }
        int count = (int) bookings.stream().filter(b -> !"CANCELLED".equals(b.status)
                && b.start.isBefore(through.plusDays(1)) && b.end.isAfter(from)).count();
        BigDecimal outstanding = FinanceService.balances(bookings, rooms, payments).stream()
                .map(FinanceService.Balance::outstanding).filter(x -> x.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<MethodTotal> methodTotals = paymentsByMethod(filteredPayments, from, through);
        return new Summary(from, through, count, roomNights, received, outstanding, daily, methodTotals);
    }

    private static boolean matchesMethod(HotelApp.Payment payment, String selectedMethod) {
        if (selectedMethod == null || selectedMethod.isBlank()) return true;
        if (payment == null) return false;
        String method = payment.method() == null || payment.method().isBlank() ? "Unspecified" : payment.method().trim();
        return method.equalsIgnoreCase(selectedMethod);
    }

    /** Groups positive payments by method using the inclusive local payment date range. */
    public static List<MethodTotal> paymentsByMethod(List<HotelApp.Payment> payments, LocalDate from, LocalDate through) {
        if (payments == null || from == null || through == null) throw new IllegalArgumentException("Inputs cannot be null");
        if (through.isBefore(from)) throw new IllegalArgumentException("End date must be on or after start date");
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (HotelApp.Payment payment : payments) {
            if (payment == null || payment.createdAt() == null || payment.amount() == null || payment.amount().signum() <= 0) continue;
            LocalDate date = payment.createdAt().toLocalDate();
            if (date.isBefore(from) || date.isAfter(through)) continue;
            String method = payment.method() == null || payment.method().isBlank() ? "Unspecified" : payment.method().trim();
            amounts.merge(method, payment.amount(), BigDecimal::add);
            counts.merge(method, 1L, Long::sum);
        }
        List<MethodTotal> result = new ArrayList<>();
        amounts.forEach((method, amount) -> result.add(new MethodTotal(method, amount, counts.getOrDefault(method, 0L))));
        result.sort(Comparator.comparing(MethodTotal::amount).reversed().thenComparing(MethodTotal::method, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public static String toCsv(Summary summary) {
        StringBuilder out = new StringBuilder("Date,Occupied rooms,Occupancy %,Payments received\r\n");
        for (Daily row : summary.daily()) {
            out.append(row.date()).append(',').append(row.occupiedRooms()).append(',')
                    .append(row.occupancyPercent()).append(',').append(csv(row.paymentsReceived().toPlainString())).append("\r\n");
        }
        out.append("\r\nPayment method,Amount,Transaction count\r\n");
        for (MethodTotal total : summary.methodTotals()) {
            out.append(csv(total.method())).append(',').append(csv(total.amount().toPlainString())).append(',')
                    .append(total.transactionCount()).append("\r\n");
        }
        return "\uFEFF" + out;
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
