package com.ashkan.hotel;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Filtering and safe CSV serialization for audit reports; independent of Swing and SQLite. */
public final class AuditReportService {
    public record Filter(LocalDate from, LocalDate through, String actor, String query) {
        public Filter {
            if (from != null && through != null && from.isAfter(through))
                throw new IllegalArgumentException("Start date must be on or before end date");
            actor = actor == null ? "" : actor.trim();
            query = query == null ? "" : query.trim();
        }
        public static Filter all() { return new Filter(null, null, "", ""); }
    }

    private AuditReportService() {}

    public static List<AuditService.Event> filter(List<AuditService.Event> events, Filter filter) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(filter, "filter");
        List<AuditService.Event> result = new ArrayList<>();
        for (AuditService.Event event : events) {
            LocalDate date = eventDate(event.time());
            if (filter.from() != null && (date == null || date.isBefore(filter.from()))) continue;
            if (filter.through() != null && (date == null || date.isAfter(filter.through()))) continue;
            if (!containsIgnoreCase(event.actor(), filter.actor())) continue;
            String haystack = String.join(" ", safe(event.area()), safe(event.action()), safe(event.recordId()), safe(event.detail()), safe(event.actor()));
            if (!containsIgnoreCase(haystack, filter.query())) continue;
            result.add(event);
        }
        return List.copyOf(result);
    }

    public static void writeCsv(List<AuditService.Event> events, Writer writer) throws IOException {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(writer, "writer");
        writer.write("ID,Time (UTC),Area,Action,Record ID,Details,Actor\r\n");
        for (AuditService.Event event : events) {
            writer.write(csv(Long.toString(event.id()))); writer.write(',');
            writer.write(csv(event.time())); writer.write(','); writer.write(csv(event.area())); writer.write(',');
            writer.write(csv(event.action())); writer.write(','); writer.write(csv(event.recordId())); writer.write(',');
            writer.write(csv(event.detail())); writer.write(','); writer.write(csv(event.actor())); writer.write("\r\n");
        }
    }

    private static LocalDate eventDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDate(); }
        catch (DateTimeParseException ignored) {
            try { return OffsetDateTime.parse(value).toLocalDate(); }
            catch (DateTimeParseException ignoredAgain) {
                try { return LocalDateTime.parse(value).toLocalDate(); }
                catch (DateTimeParseException ignoredLast) { return null; }
            }
        }
    }
    private static boolean containsIgnoreCase(String value, String query) {
        return query.isEmpty() || safe(value).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String csv(String value) {
        String safe = safe(value);
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
