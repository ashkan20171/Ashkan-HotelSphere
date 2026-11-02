package com.ashkan.hotel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Local attendance register with one daily record per employee. */
public final class AttendanceService {
    public record Shift(String name, LocalTime start, LocalTime end) {
        public Shift { if (name == null || name.isBlank() || start == null || end == null || !end.isAfter(start)) throw new IllegalArgumentException("Invalid shift"); }
    }
    public record Entry(String username, LocalDate date, String shift, LocalTime scheduledStart, LocalTime scheduledEnd,
                        LocalDateTime checkIn, LocalDateTime checkOut) {
        public long lateMinutes() { return checkIn == null ? 0 : Math.max(0, ChronoUnit.MINUTES.between(scheduledStart, checkIn.toLocalTime())); }
        public long overtimeMinutes() { return checkOut == null ? 0 : Math.max(0, ChronoUnit.MINUTES.between(scheduledEnd, checkOut.toLocalTime())); }
        public long workedMinutes() { return checkIn == null || checkOut == null ? 0 : Math.max(0, ChronoUnit.MINUTES.between(checkIn, checkOut)); }
    }
    private final Path file;
    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    public AttendanceService() { this(Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere", "attendance.tsv"), Clock.systemDefaultZone()); }
    public AttendanceService(Path file) { this(file, Clock.systemDefaultZone()); }
    public AttendanceService(Path file, Clock clock) {
        this.file = Objects.requireNonNull(file); this.clock = Objects.requireNonNull(clock);
        try { Files.createDirectories(file.toAbsolutePath().getParent()); if (Files.exists(file)) load(); }
        catch (IOException ex) { throw new IllegalStateException("Cannot load attendance records", ex); }
    }
    private static String key(String user, LocalDate date) { return user.toLowerCase(Locale.ROOT) + "|" + date; }
    public synchronized Entry clockIn(String username, Shift shift) throws IOException {
        String user = clean(username); if (user.isBlank()) throw new IllegalArgumentException("Username is required");
        LocalDateTime now = LocalDateTime.now(clock); String k = key(user, now.toLocalDate());
        if (entries.containsKey(k)) throw new IllegalStateException("Attendance already started for today");
        Entry e = new Entry(user, now.toLocalDate(), shift.name(), shift.start(), shift.end(), now, null);
        entries.put(k, e); persist(); return e;
    }
    public synchronized Entry clockOut(String username) throws IOException {
        LocalDateTime now = LocalDateTime.now(clock); String k = key(clean(username), now.toLocalDate()); Entry old = entries.get(k);
        if (old == null || old.checkIn() == null) throw new IllegalStateException("No check-in found for today");
        if (old.checkOut() != null) throw new IllegalStateException("Already checked out");
        if (now.isBefore(old.checkIn())) throw new IllegalStateException("Check-out cannot precede check-in");
        Entry updated = new Entry(old.username(), old.date(), old.shift(), old.scheduledStart(), old.scheduledEnd(), old.checkIn(), now);
        entries.put(k, updated); persist(); return updated;
    }
    public synchronized Optional<Entry> today(String username) { return Optional.ofNullable(entries.get(key(username, LocalDate.now(clock)))); }
    public synchronized List<Entry> all() { return entries.values().stream().sorted(Comparator.comparing(Entry::date).reversed().thenComparing(Entry::username)).toList(); }
    public synchronized List<Entry> forMonth(YearMonth month, String usernameOrNull) {
        return all().stream().filter(e -> YearMonth.from(e.date()).equals(month))
                .filter(e -> usernameOrNull == null || e.username().equalsIgnoreCase(usernameOrNull)).toList();
    }
    public static String csv(List<Entry> rows) {
        StringBuilder b = new StringBuilder("Username,Date,Shift,CheckIn,CheckOut,WorkedMinutes,LateMinutes,OvertimeMinutes\n");
        for (Entry e : rows) b.append(csvCell(e.username())).append(',').append(e.date()).append(',').append(csvCell(e.shift())).append(',')
                .append(e.checkIn() == null ? "" : e.checkIn()).append(',').append(e.checkOut() == null ? "" : e.checkOut()).append(',')
                .append(e.workedMinutes()).append(',').append(e.lateMinutes()).append(',').append(e.overtimeMinutes()).append('\n');
        return b.toString();
    }
    private static String csvCell(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
    private static String clean(String s) { return s == null ? "" : s.strip(); }
    private static String enc(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String s) { return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8); }
    private void load() throws IOException {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue; String[] f = line.split("\\t", -1); if (f.length != 7) continue;
            try { Entry e = new Entry(dec(f[0]), LocalDate.parse(f[1]), dec(f[2]), LocalTime.parse(f[3]), LocalTime.parse(f[4]),
                    f[5].isEmpty() ? null : LocalDateTime.parse(f[5]), f[6].isEmpty() ? null : LocalDateTime.parse(f[6])); entries.put(key(e.username(), e.date()), e); }
            catch (RuntimeException ignored) { /* skip malformed individual row */ }
        }
    }
    private void persist() throws IOException {
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "attendance-", ".tmp");
        try { List<String> lines = new ArrayList<>(); for (Entry e : entries.values()) lines.add(String.join("\t", enc(e.username()), e.date().toString(), enc(e.shift()), e.scheduledStart().toString(), e.scheduledEnd().toString(), e.checkIn() == null ? "" : e.checkIn().toString(), e.checkOut() == null ? "" : e.checkOut().toString()));
            Files.write(temp, lines, StandardCharsets.UTF_8); try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
