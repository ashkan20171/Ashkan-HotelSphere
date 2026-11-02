package com.ashkan.hotel;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
/** Append-only operational event log; failures are reported to the caller. */
public final class AuditService {
    public record Event(long id, String time, String area, String action, String recordId, String detail, String actor) {}
    private final Path database;
    private static final ThreadLocal<String> ACTOR = ThreadLocal.withInitial(() -> "SYSTEM");
    public static void setActor(String actor) { ACTOR.set(actor == null || actor.isBlank() ? "SYSTEM" : actor.trim()); }
    public static String currentActor() { return ACTOR.get(); }
    public static void clearActor() { ACTOR.remove(); }
    public AuditService(Path database) { this.database=Objects.requireNonNull(database); }
    public static AuditService defaultService() {return new AuditService(Path.of(System.getProperty("user.home"),".ashkan-hotelsphere","hotel.db"));}
    private Connection connect() throws Exception {
        Files.createDirectories(database.toAbsolutePath().getParent());
        Connection c=DriverManager.getConnection("jdbc:sqlite:"+database.toAbsolutePath());
        try(Statement s=c.createStatement()) {
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS operational_audit (id INTEGER PRIMARY KEY AUTOINCREMENT, event_time TEXT NOT NULL, area TEXT NOT NULL, action TEXT NOT NULL, record_id TEXT NOT NULL, detail TEXT NOT NULL)");
            try (ResultSet columns = s.executeQuery("PRAGMA table_info(operational_audit)")) {
                boolean found = false;
                while (columns.next()) if ("actor".equalsIgnoreCase(columns.getString("name"))) found = true;
                if (!found) s.execute("ALTER TABLE operational_audit ADD COLUMN actor TEXT NOT NULL DEFAULT 'LEGACY'");
            }
        } return c;
    }
    public void record(String area,String action,String recordId,String detail) {
        try(Connection c=connect();PreparedStatement ps=c.prepareStatement("INSERT INTO operational_audit(event_time,area,action,record_id,detail,actor) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1,Instant.now().toString());ps.setString(2,area);ps.setString(3,action);ps.setString(4,recordId);ps.setString(5,detail);ps.setString(6,ACTOR.get());ps.executeUpdate();
        }catch(Exception ex){throw new IllegalStateException("Cannot write audit event",ex);}
    }
    public List<Event> recent(int limit) {
        if(limit<1||limit>1000)throw new IllegalArgumentException("Limit must be 1..1000");
        List<Event> events=new ArrayList<>();
        try(Connection c=connect();PreparedStatement ps=c.prepareStatement("SELECT id,event_time,area,action,record_id,detail,actor FROM operational_audit ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1,limit);try(ResultSet rs=ps.executeQuery()) {while(rs.next())events.add(new Event(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7)));}
            return List.copyOf(events);
        }catch(Exception ex){throw new IllegalStateException("Cannot read audit events",ex);}
    }
}
