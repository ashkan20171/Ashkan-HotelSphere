package com.ashkan.hotel;
import java.util.*;
/** Pure operational metrics and request filtering, independent of Swing and persistence. */
public final class OperationsService {
    private OperationsService() {}
    public record Summary(long openTasks, long activeTasks, long openRequests, long urgentRequests) {}
    public static Summary summarize(List<HousekeepingTaskService.Task> tasks, List<GuestRequestService.Request> requests) {
        Objects.requireNonNull(tasks); Objects.requireNonNull(requests);
        return new Summary(
            tasks.stream().filter(t -> t.status() == HousekeepingTaskService.Status.OPEN).count(),
            tasks.stream().filter(t -> t.status() == HousekeepingTaskService.Status.IN_PROGRESS).count(),
            requests.stream().filter(r -> r.status() == GuestRequestService.Status.OPEN).count(),
            requests.stream().filter(r -> r.status() != GuestRequestService.Status.RESOLVED && r.priority() == GuestRequestService.Priority.URGENT).count()
        );
    }
    public static List<GuestRequestService.Request> filterRequests(List<GuestRequestService.Request> requests, String query, String status) {
        Objects.requireNonNull(requests);
        String needle = Objects.requireNonNullElse(query, "").strip().toLowerCase(Locale.ROOT);
        String desired = Objects.requireNonNullElse(status, "ALL");
        return requests.stream().filter(r -> desired.equals("ALL") || r.status().name().equals(desired))
            .filter(r -> needle.isEmpty() || (r.id() + " " + r.roomId() + " " + r.category() + " " + r.description() + " " + r.assignee()).toLowerCase(Locale.ROOT).contains(needle))
            .toList();
    }
}
