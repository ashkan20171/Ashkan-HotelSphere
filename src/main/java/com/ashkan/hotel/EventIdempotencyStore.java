package com.ashkan.hotel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Durable consumer-side inbox. A message ID is recorded only after its handler succeeds. */
public final class EventIdempotencyStore {
    private final Path file;
    private final Set<String> processed = new LinkedHashSet<>();
    public EventIdempotencyStore(Path file) { this.file = Objects.requireNonNull(file); load(); }
    public synchronized boolean contains(String id) { return processed.contains(required(id)); }
    public synchronized boolean markProcessed(String id) {
        String key = required(id);
        if (!processed.add(key)) return false;
        persist(); return true;
    }
    public synchronized int size() { return processed.size(); }
    private void load() {
        if (!Files.exists(file)) return;
        try { for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) if (!line.isBlank()) processed.add(line.trim()); }
        catch (IOException e) { throw new IllegalStateException("Cannot load idempotency inbox", e); }
    }
    private void persist() {
        try {
            Path parent = file.toAbsolutePath().getParent(); Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, "event-inbox-", ".tmp");
            try { Files.write(tmp, processed, StandardCharsets.UTF_8); try { Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(tmp); }
        } catch (IOException e) { throw new IllegalStateException("Cannot persist idempotency inbox", e); }
    }
    private static String required(String id) { String v = Objects.requireNonNullElse(id, "").trim(); if (v.isEmpty()) throw new IllegalArgumentException("messageId is required"); return v; }
}
