package com.ashkan.hotel;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Transactional SQLite repository for operational room, staff and guest workflows. */
public final class OperationalSqliteStore {
    private final Path database;
    public OperationalSqliteStore(Path database) { this.database = Objects.requireNonNull(database); }
    public static OperationalSqliteStore defaultStore() {
        return new OperationalSqliteStore(Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere", "hotel.db"));
    }
    private Connection connect() throws Exception {
        Files.createDirectories(database.toAbsolutePath().getParent());
        Connection c=DriverManager.getConnection("jdbc:sqlite:"+database.toAbsolutePath());
        try (Statement s=c.createStatement()) {
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS operational_records (kind TEXT NOT NULL, record_id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(kind,record_id))");
        }
        return c;
    }
    public Map<String,String> read(String kind) {
        Map<String,String> result=new LinkedHashMap<>();
        try(Connection c=connect();PreparedStatement ps=c.prepareStatement("SELECT record_id,payload FROM operational_records WHERE kind=? ORDER BY rowid")) {
            ps.setString(1,kind);
            try(ResultSet rs=ps.executeQuery()) {while(rs.next())result.put(rs.getString(1),rs.getString(2));}
            return result;
        }catch(Exception ex){throw new IllegalStateException("SQLite read failed: "+kind,ex);}
    }
    public void replace(String kind,Map<String,String> records) {
        try(Connection c=connect()) {
            c.setAutoCommit(false);
            try(PreparedStatement del=c.prepareStatement("DELETE FROM operational_records WHERE kind=?");PreparedStatement ins=c.prepareStatement("INSERT INTO operational_records(kind,record_id,payload) VALUES(?,?,?)")) {
                del.setString(1,kind);del.executeUpdate();
                for(var entry:records.entrySet()) {ins.setString(1,kind);ins.setString(2,entry.getKey());ins.setString(3,entry.getValue());ins.addBatch();}
                ins.executeBatch();c.commit();
            }catch(Exception ex){c.rollback();throw ex;}
        }catch(Exception ex){throw new IllegalStateException("SQLite write failed: "+kind,ex);}
    }
    /** Persist a snapshot and its audit event atomically in the same SQLite transaction. */
    public void replaceWithAudit(String kind, Map<String,String> records, String area,
                                 String action, String recordId, String detail) {
        Objects.requireNonNull(records);
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                try (Statement schema = c.createStatement()) {
                    schema.execute("CREATE TABLE IF NOT EXISTS operational_audit (id INTEGER PRIMARY KEY AUTOINCREMENT, event_time TEXT NOT NULL, area TEXT NOT NULL, action TEXT NOT NULL, record_id TEXT NOT NULL, detail TEXT NOT NULL, actor TEXT NOT NULL DEFAULT 'LEGACY')");
                    boolean hasActor = false;
                    try (ResultSet columns = schema.executeQuery("PRAGMA table_info(operational_audit)")) {
                        while (columns.next()) if ("actor".equalsIgnoreCase(columns.getString("name"))) hasActor = true;
                    }
                    if (!hasActor) schema.execute("ALTER TABLE operational_audit ADD COLUMN actor TEXT NOT NULL DEFAULT 'LEGACY'");
                }
                try (PreparedStatement del = c.prepareStatement("DELETE FROM operational_records WHERE kind=?");
                     PreparedStatement ins = c.prepareStatement("INSERT INTO operational_records(kind,record_id,payload) VALUES(?,?,?)")) {
                    del.setString(1,kind); del.executeUpdate();
                    for (var entry : records.entrySet()) {
                        ins.setString(1,kind); ins.setString(2,entry.getKey()); ins.setString(3,entry.getValue()); ins.addBatch();
                    }
                    ins.executeBatch();
                }
                try (PreparedStatement audit = c.prepareStatement("INSERT INTO operational_audit(event_time,area,action,record_id,detail,actor) VALUES(?,?,?,?,?,?)")) {
                    audit.setString(1,java.time.Instant.now().toString());
                    audit.setString(2,area);audit.setString(3,action);audit.setString(4,recordId);
                    audit.setString(5,detail);audit.setString(6,AuditService.currentActor());audit.executeUpdate();
                }
                c.commit();
            } catch (Exception ex) { c.rollback(); throw ex; }
        } catch (Exception ex) { throw new IllegalStateException("Atomic operational update failed: " + kind, ex); }
    }

}
