package com.ashkan.hotel;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.List;

public final class PayrollServiceSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("payroll-test"); Path file = dir.resolve("payroll.tsv");
        PayrollService service = new PayrollService(file);
        service.save(new PayrollService.Compensation("ashkan", new BigDecimal("3000"), new BigDecimal("200"), new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("1.5")));
        AttendanceService.Entry e = new AttendanceService.Entry("ashkan", LocalDate.of(2026,10,4), "Morning", LocalTime.of(8,0), LocalTime.of(16,0), LocalDateTime.of(2026,10,4,8,15), LocalDateTime.of(2026,10,4,16,30));
        var slip = service.calculate("ASHKAN", YearMonth.of(2026,10), List.of(e));
        check(slip.overtimeMinutes() == 30 && slip.lateMinutes() == 15, "attendance aggregation");
        check(slip.overtimePay().compareTo(new BigDecimal("15.00")) == 0, "overtime calculation");
        check(slip.lateDeduction().compareTo(new BigDecimal("2.50")) == 0, "late deduction");
        check(slip.netPay().compareTo(new BigDecimal("3212.50")) == 0, "net salary");
        PayrollService reloaded = new PayrollService(file);
        check(reloaded.find("ashkan").isPresent(), "persistence");
        check(PayrollService.csv(List.of(slip)).contains("NetPay"), "CSV header");
        try { new PayrollService.Compensation("x", new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE); throw new AssertionError("negative salary accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PayrollServiceSmokeTest: PASS");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
