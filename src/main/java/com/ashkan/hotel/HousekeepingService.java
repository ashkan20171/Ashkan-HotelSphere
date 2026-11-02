package com.ashkan.hotel;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Persistent local room-cleaning workflow; separate from booking occupancy. */
public final class HousekeepingService {
    public enum Status { CLEAN, DIRTY, IN_PROGRESS, INSPECTION, OUT_OF_SERVICE }
    private final Properties data = new Properties();
    private final Path file;
    private final OperationalSqliteStore sqlite;

    public HousekeepingService() {
        this(OperationalSqliteStore.defaultStore());
    }
    public HousekeepingService(Path file) {
        this.file = Objects.requireNonNull(file); this.sqlite=null;
        if (Files.exists(file)) {
            try (var in = Files.newInputStream(file)) { data.load(in); }
            catch (IOException ex) { throw new IllegalStateException("Cannot read housekeeping data", ex); }
        }
    }
    public HousekeepingService(OperationalSqliteStore store) {
        this.file=null;this.sqlite=Objects.requireNonNull(store);
        var rows=sqlite.read("housekeeping");
        if(rows.isEmpty()) {
            Path legacy=Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","housekeeping.properties");
            if(Files.exists(legacy)) {
                HousekeepingService previous=new HousekeepingService(legacy);
                for(String id:previous.data.stringPropertyNames())data.setProperty(id,previous.data.getProperty(id));
                persistSqlite();
                return;
            }
        }
        for(var e:rows.entrySet())data.setProperty(e.getKey(),e.getValue());
    }
    private void persistSqlite() {
        Map<String,String> rows=new LinkedHashMap<>();
        for(String id:data.stringPropertyNames()) rows.put(id,data.getProperty(id));
        sqlite.replace("housekeeping",rows);
    }
    public Status status(String roomId) {
        try { return Status.valueOf(data.getProperty(roomId, Status.CLEAN.name())); }
        catch (IllegalArgumentException ex) { return Status.INSPECTION; }
    }
    public void update(String roomId, Status next) {
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("Room ID required");
        Objects.requireNonNull(next);
        data.setProperty(roomId, next.name());
        if(sqlite!=null) {persistSqlite();return;}
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "housekeeping-", ".tmp");
            try {
                try (var out = Files.newOutputStream(temp)) { data.store(out, "Ashkan HotelSphere housekeeping"); }
                try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException ex) { throw new IllegalStateException("Cannot save housekeeping status", ex); }
    }
}
