package com.ashkan.hotel;

import java.io.IOException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.YearMonth;
import java.util.*;

/** Local payroll configuration and reproducible monthly payroll calculations. Amounts use the hotel's configured currency unit. */
public final class PayrollService {
    public record Compensation(String username, BigDecimal baseSalary, BigDecimal allowances,
                                BigDecimal hourlyRate, BigDecimal lateDeductionPerHour, BigDecimal overtimeMultiplier) {
        public Compensation {
            username = Objects.requireNonNull(username).strip();
            if (username.isBlank()) throw new IllegalArgumentException("Username is required");
            baseSalary = nonNegative(baseSalary, "Base salary"); allowances = nonNegative(allowances, "Allowances");
            hourlyRate = nonNegative(hourlyRate, "Hourly rate"); lateDeductionPerHour = nonNegative(lateDeductionPerHour, "Late deduction rate");
            overtimeMultiplier = nonNegative(overtimeMultiplier, "Overtime multiplier");
        }
    }
    public record Payslip(String username, YearMonth month, BigDecimal baseSalary, BigDecimal allowances,
                          BigDecimal overtimePay, BigDecimal lateDeduction, BigDecimal netPay,
                          long overtimeMinutes, long lateMinutes) { }
    private final Path file;
    private final Map<String, Compensation> compensation = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    public PayrollService() { this(Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere", "payroll.tsv")); }
    public PayrollService(Path file) {
        this.file = Objects.requireNonNull(file);
        try { Files.createDirectories(file.toAbsolutePath().getParent()); if (Files.exists(file)) load(); }
        catch (IOException ex) { throw new IllegalStateException("Cannot load payroll settings", ex); }
    }
    private static BigDecimal nonNegative(BigDecimal n, String label) {
        Objects.requireNonNull(n, label); if (n.signum() < 0) throw new IllegalArgumentException(label + " cannot be negative"); return n.setScale(2, RoundingMode.HALF_UP);
    }
    public synchronized void save(Compensation c) throws IOException { compensation.put(c.username(), c); persist(); }
    public synchronized Optional<Compensation> find(String username) { return Optional.ofNullable(compensation.get(username)); }
    public synchronized List<Compensation> all() { return List.copyOf(compensation.values()); }
    public Payslip calculate(String username, YearMonth month, List<AttendanceService.Entry> attendance) {
        Compensation c = find(username).orElseThrow(() -> new IllegalStateException("No compensation settings for " + username));
        long overtime = 0, late = 0;
        for (AttendanceService.Entry e : attendance) if (e.username().equalsIgnoreCase(username) && YearMonth.from(e.date()).equals(month)) {
            overtime = Math.addExact(overtime, e.overtimeMinutes()); late = Math.addExact(late, e.lateMinutes());
        }
        BigDecimal overtimePay = c.hourlyRate().multiply(BigDecimal.valueOf(overtime)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).multiply(c.overtimeMultiplier()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deduction = c.lateDeductionPerHour().multiply(BigDecimal.valueOf(late)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal net = c.baseSalary().add(c.allowances()).add(overtimePay).subtract(deduction).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Payslip(c.username(), month, c.baseSalary(), c.allowances(), overtimePay, deduction, net, overtime, late);
    }
    public static String csv(List<Payslip> rows) {
        StringBuilder b = new StringBuilder("Username,Month,BaseSalary,Allowances,OvertimePay,LateDeduction,NetPay,OvertimeMinutes,LateMinutes\n");
        for (Payslip p : rows) b.append(cell(p.username())).append(',').append(p.month()).append(',').append(p.baseSalary()).append(',').append(p.allowances()).append(',').append(p.overtimePay()).append(',').append(p.lateDeduction()).append(',').append(p.netPay()).append(',').append(p.overtimeMinutes()).append(',').append(p.lateMinutes()).append('\n');
        return b.toString();
    }
    private static String cell(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
    private void load() throws IOException {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) { String[] f = line.split("\\t", -1); if (f.length != 6) continue;
            try { String user = new String(Base64.getDecoder().decode(f[0]), StandardCharsets.UTF_8); compensation.put(user, new Compensation(user, new BigDecimal(f[1]), new BigDecimal(f[2]), new BigDecimal(f[3]), new BigDecimal(f[4]), new BigDecimal(f[5]))); } catch (RuntimeException ignored) { }
        }
    }
    private void persist() throws IOException {
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "payroll-", ".tmp");
        try { List<String> lines = new ArrayList<>(); for (Compensation c : compensation.values()) lines.add(String.join("\t", Base64.getEncoder().encodeToString(c.username().getBytes(StandardCharsets.UTF_8)), c.baseSalary().toPlainString(), c.allowances().toPlainString(), c.hourlyRate().toPlainString(), c.lateDeductionPerHour().toPlainString(), c.overtimeMultiplier().toPlainString()));
            Files.write(temp, lines, StandardCharsets.UTF_8); try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
