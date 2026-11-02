package com.ashkan.hotel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/** Integration test for transactional operational writes and audit consistency. */
public final class OperationalSqliteStoreIntegrationTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("hotel-sqlite-integration-");
        Path database = directory.resolve("test-hotel.db");
        try {
            OperationalSqliteStore store = new OperationalSqliteStore(database);
            AuditService audit = new AuditService(database);
            AuditService.setActor("integration-admin");

            Map<String, String> initial = new LinkedHashMap<>();
            initial.put("task-1", "initial-payload");
            store.replaceWithAudit("tasks", initial, "HOUSEKEEPING", "CREATE", "task-1", "initial event");

            if (!"initial-payload".equals(store.read("tasks").get("task-1"))) {
                throw new AssertionError("Initial operational record was not persisted");
            }
            var events = audit.recent(10);
            if (events.size() != 1 || !"integration-admin".equals(events.get(0).actor())) {
                throw new AssertionError("Audit actor/event was not persisted atomically: " + events);
            }

            // Force the audit insert to fail after the record snapshot has been replaced.
            // The store must roll back both changes, leaving the initial snapshot intact.
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_audit_insert BEFORE INSERT ON operational_audit " +
                        "BEGIN SELECT RAISE(ABORT, 'forced audit failure'); END");
            }

            Map<String, String> replacement = new LinkedHashMap<>();
            replacement.put("task-2", "replacement-payload");
            boolean failed = false;
            try {
                store.replaceWithAudit("tasks", replacement, "HOUSEKEEPING", "UPDATE", "task-2", "must roll back");
            } catch (IllegalStateException expected) {
                failed = true;
            }
            if (!failed) throw new AssertionError("Expected forced audit failure");
            Map<String, String> afterFailure = store.read("tasks");
            if (afterFailure.size() != 1 || !"initial-payload".equals(afterFailure.get("task-1")) || afterFailure.containsKey("task-2")) {
                throw new AssertionError("Operational snapshot was not rolled back: " + afterFailure);
            }
            if (audit.recent(10).size() != 1) throw new AssertionError("Failed transaction left an audit event");

            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
                if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                    throw new AssertionError("SQLite integrity_check did not return ok");
                }
            }
            System.out.println("OperationalSqliteStoreIntegrationTest PASS");
        } finally {
            AuditService.clearActor();
            try (var paths = Files.walk(directory)) {
                paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }
}
