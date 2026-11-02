package com.ashkan.hotel;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** SQLite persistence adapter. Writes all four aggregates in one transaction. */
final class SqliteHotelStore {
    private final Path dir = Path.of(System.getProperty("user.home"), ".ashkan-hotelsphere");
    private final Path database = dir.resolve("hotel.db");
    private final Path legacy = dir.resolve("data.bin");

    Path databasePath() { return database; }
    void backup(Path target) throws SQLException, IOException {
        if (Files.exists(target)) throw new IOException("Backup destination already exists; choose a new filename.");
        try (Connection c=connect(); Statement st=c.createStatement()) {
            String escaped=target.toAbsolutePath().toString().replace("'", "''");
            st.execute("VACUUM INTO '" + escaped + "'");
        }
    }
    private Connection connect() throws SQLException { return DriverManager.getConnection("jdbc:sqlite:" + database); }

    void load(List<HotelApp.Room> rooms, List<HotelApp.Guest> guests,
              List<HotelApp.Booking> bookings, List<HotelApp.Payment> payments) throws Exception {
        Files.createDirectories(dir);
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("CREATE TABLE IF NOT EXISTS rooms (id TEXT PRIMARY KEY, type TEXT NOT NULL, rate REAL NOT NULL CHECK(rate>=0))");
            st.execute("CREATE TABLE IF NOT EXISTS guests (id TEXT PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS bookings (id TEXT PRIMARY KEY, room_id TEXT NOT NULL, guest_id TEXT NOT NULL, start_date TEXT NOT NULL, end_date TEXT NOT NULL, status TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS payments (id TEXT PRIMARY KEY, booking_id TEXT NOT NULL, amount TEXT NOT NULL, method TEXT NOT NULL, created_at TEXT NOT NULL)");
            try (ResultSet rs = st.executeQuery("SELECT id,type,rate FROM rooms")) {
                while (rs.next()) rooms.add(new HotelApp.Room(rs.getString(1),rs.getString(2),rs.getDouble(3)));
            }
            try (ResultSet rs = st.executeQuery("SELECT id,name,phone FROM guests")) {
                while (rs.next()) guests.add(new HotelApp.Guest(rs.getString(1),rs.getString(2),rs.getString(3)));
            }
            try (ResultSet rs = st.executeQuery("SELECT id,room_id,guest_id,start_date,end_date,status FROM bookings")) {
                while (rs.next()) bookings.add(new HotelApp.Booking(rs.getString(1),rs.getString(2),rs.getString(3),LocalDate.parse(rs.getString(4)),LocalDate.parse(rs.getString(5)),rs.getString(6)));
            }
            try (ResultSet rs = st.executeQuery("SELECT id,booking_id,amount,method,created_at FROM payments")) {
                while (rs.next()) payments.add(new HotelApp.Payment(rs.getString(1),rs.getString(2),new java.math.BigDecimal(rs.getString(3)),rs.getString(4),LocalDateTime.parse(rs.getString(5))));
            }
        }
        // Import only into an empty database; retain the original legacy file untouched.
        if (rooms.isEmpty() && guests.isEmpty() && bookings.isEmpty() && payments.isEmpty() && Files.exists(legacy)) {
            try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(legacy))) {
                Object r=in.readObject(), g=in.readObject(), b=in.readObject();
                if (!(r instanceof List<?> rr) || !(g instanceof List<?> gg) || !(b instanceof List<?> bb)
                    || !rr.stream().allMatch(HotelApp.Room.class::isInstance)
                    || !gg.stream().allMatch(HotelApp.Guest.class::isInstance)
                    || !bb.stream().allMatch(HotelApp.Booking.class::isInstance)) throw new IOException("Invalid legacy data");
                for (Object item: rr) rooms.add((HotelApp.Room)item);
                for (Object item: gg) guests.add((HotelApp.Guest)item);
                for (Object item: bb) bookings.add((HotelApp.Booking)item);
                try {
                    Object ps=in.readObject();
                    if (!(ps instanceof List<?> pp) || !pp.stream().allMatch(HotelApp.Payment.class::isInstance)) throw new IOException("Invalid legacy payments");
                    for (Object item: pp) payments.add((HotelApp.Payment)item);
                } catch (EOFException oldFormat) { /* Pre-payment release. */ }
                save(rooms,guests,bookings,payments);
            }
        }
    }

    void save(List<HotelApp.Room> rooms, List<HotelApp.Guest> guests,
              List<HotelApp.Booking> bookings, List<HotelApp.Payment> payments) throws SQLException {
        try (Connection c=connect()) {
            c.setAutoCommit(false);
            try (Statement st=c.createStatement()) {
                st.executeUpdate("DELETE FROM payments");
                st.executeUpdate("DELETE FROM bookings");
                st.executeUpdate("DELETE FROM guests");
                st.executeUpdate("DELETE FROM rooms");
                try (PreparedStatement ps=c.prepareStatement("INSERT INTO rooms VALUES(?,?,?)")) {
                    for (var r:rooms) {ps.setString(1,r.id());ps.setString(2,r.type());ps.setDouble(3,r.rate());ps.addBatch();} ps.executeBatch();
                }
                try (PreparedStatement ps=c.prepareStatement("INSERT INTO guests VALUES(?,?,?)")) {
                    for (var g:guests) {ps.setString(1,g.id());ps.setString(2,g.name());ps.setString(3,g.phone());ps.addBatch();} ps.executeBatch();
                }
                try (PreparedStatement ps=c.prepareStatement("INSERT INTO bookings VALUES(?,?,?,?,?,?)")) {
                    for (var b:bookings) {ps.setString(1,b.id);ps.setString(2,b.roomId);ps.setString(3,b.guestId);ps.setString(4,b.start.toString());ps.setString(5,b.end.toString());ps.setString(6,b.status);ps.addBatch();} ps.executeBatch();
                }
                try (PreparedStatement ps=c.prepareStatement("INSERT INTO payments VALUES(?,?,?,?,?)")) {
                    for (var p:payments) {ps.setString(1,p.id());ps.setString(2,p.bookingId());ps.setString(3,p.amount().toPlainString());ps.setString(4,p.method());ps.setString(5,p.createdAt().toString());ps.addBatch();} ps.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException ex) {c.rollback();throw ex;}
        }
    }
}
