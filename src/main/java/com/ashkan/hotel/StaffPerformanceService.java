package com.ashkan.hotel;

import java.util.*;

/** Aggregates recorded audit events. Activity counts are not measures of employee quality. */
public final class StaffPerformanceService {
    public record Summary(String actor, long total, long created, long statusChanges, String lastActivity) {}
    private final AuditService audit;
    public StaffPerformanceService(AuditService audit) { this.audit = Objects.requireNonNull(audit); }
    public List<Summary> summaries() { return aggregate(audit.recent(1000)); }
    public static List<Summary> aggregate(List<AuditService.Event> events) {
        Map<String, Mutable> byActor = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (AuditService.Event e : events) {
            if (e.actor() == null || e.actor().isBlank() || e.actor().equalsIgnoreCase("SYSTEM") || e.actor().equalsIgnoreCase("LEGACY")) continue;
            Mutable m = byActor.computeIfAbsent(e.actor(), k -> new Mutable());
            m.total++;
            String action = e.action() == null ? "" : e.action().toLowerCase(Locale.ROOT);
            if (action.contains("creat") || action.contains("add")) m.created++;
            if (action.contains("status") || action.contains("progress") || action.contains("resolv") || action.contains("done")) m.statusChanges++;
            if (e.time() != null && e.time().compareTo(m.last) > 0) m.last = e.time();
        }
        List<Summary> result = new ArrayList<>();
        byActor.forEach((actor, m) -> result.add(new Summary(actor, m.total, m.created, m.statusChanges, m.last)));
        result.sort(Comparator.comparingLong(Summary::total).reversed().thenComparing(Summary::actor));
        return List.copyOf(result);
    }
    private static final class Mutable { long total, created, statusChanges; String last = ""; }
}
