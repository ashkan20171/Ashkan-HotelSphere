package com.ashkan.hotel;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Transactional operational workflow for front-desk booking actions and calendar queries. */
public final class BookingOperationsRepository extends JdbcDomainRepository {
    public record CalendarEntry(String bookingId,String roomId,String roomNumber,String guestId,LocalDate checkIn,LocalDate checkOut,String status) {}
    public record Cancellation(String bookingId,String status,BigDecimal fee,String currency,String reason) {}
    public BookingOperationsRepository(String url,String user,String password,int pool){super(url,user,password,pool);}

    public List<CalendarEntry> calendar(LocalDate from,LocalDate to,String roomType)throws SQLException {
        if(from==null||to==null||!to.isAfter(from))throw new IllegalArgumentException("to must be after from");
        return withConnection(c->{String sql="SELECT b.booking_id,b.room_id,r.room_number,b.guest_id,b.check_in,b.check_out,b.status FROM hotel_bookings b JOIN hotel_rooms r ON r.room_id=b.room_id WHERE b.deleted_at IS NULL AND b.check_in < ? AND b.check_out > ?"+(roomType==null||roomType.isBlank()?"":" AND r.room_type=?")+" ORDER BY b.check_in,r.room_number";
            try(PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,to);p.setObject(2,from);if(roomType!=null&&!roomType.isBlank())p.setString(3,roomType);try(ResultSet r=p.executeQuery()){List<CalendarEntry> out=new ArrayList<>();while(r.next())out.add(new CalendarEntry(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getObject(5,LocalDate.class),r.getObject(6,LocalDate.class),r.getString(7)));return out;}}
        });
    }

    public String transition(String id,String target,LocalDate today)throws SQLException {
        String next=target==null?"":target.toUpperCase(Locale.ROOT);
        if(!Set.of("CHECKED_IN","CHECKED_OUT").contains(next))throw new IllegalArgumentException("Unsupported booking transition");
        return withConnection(c->{boolean auto=c.getAutoCommit();c.setAutoCommit(false);try{
            String current;LocalDate in,out;
            try(PreparedStatement p=c.prepareStatement("SELECT status,check_in,check_out FROM hotel_bookings WHERE booking_id=? AND deleted_at IS NULL FOR UPDATE")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Booking not found");current=r.getString(1);in=r.getObject(2,LocalDate.class);out=r.getObject(3,LocalDate.class);}}
            if("CHECKED_IN".equals(next)){if(!"CONFIRMED".equals(current))throw new IllegalStateException("Only confirmed bookings can check in");if(today.isBefore(in))throw new IllegalStateException("Check-in date has not arrived");if(!today.isBefore(out))throw new IllegalStateException("Booking has already reached check-out date");}
            else {if(!"CHECKED_IN".equals(current))throw new IllegalStateException("Guest must be checked in first");if(today.isBefore(in))throw new IllegalStateException("Invalid check-out date");}
            try(PreparedStatement p=c.prepareStatement("UPDATE hotel_bookings SET status=?,updated_at=NOW() WHERE booking_id=?")){p.setString(1,next);p.setString(2,id);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO booking_status_history(booking_id,previous_status,new_status,changed_at) VALUES(?,?,?,NOW())")){p.setString(1,id);p.setString(2,current);p.setString(3,next);p.executeUpdate();}
            c.commit();BookingRepository.invalidateSharedCache(id);return next;
        }catch(Exception e){c.rollback();if(e instanceof SQLException s)throw s;if(e instanceof RuntimeException r)throw r;throw new SQLException(e);}finally{c.setAutoCommit(auto);}});
    }

    public Cancellation cancel(String id,LocalDate today)throws SQLException {
        return withConnection(c->{boolean auto=c.getAutoCommit();c.setAutoCommit(false);try{
            LocalDate checkIn;String status,currency;BigDecimal total;
            try(PreparedStatement p=c.prepareStatement("SELECT status,check_in,total_amount,currency FROM hotel_bookings WHERE booking_id=? AND deleted_at IS NULL FOR UPDATE")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Booking not found");status=r.getString(1);checkIn=r.getObject(2,LocalDate.class);total=r.getBigDecimal(3);currency=r.getString(4);}}
            if(!Set.of("PENDING","CONFIRMED").contains(status))throw new IllegalStateException("Only pending or confirmed bookings can be cancelled");
            int hours;BigDecimal feeRate;try(PreparedStatement p=c.prepareStatement("SELECT free_cancellation_hours,late_cancellation_fee_percent FROM cancellation_policy WHERE policy_id='DEFAULT'")){try(ResultSet r=p.executeQuery()){if(!r.next()){hours=24;feeRate=new BigDecimal("50.00");}else{hours=r.getInt(1);feeRate=r.getBigDecimal(2);}}}
            long hoursUntil=Duration.between(today.atStartOfDay(),checkIn.atStartOfDay()).toHours();BigDecimal fee=hoursUntil>=hours?BigDecimal.ZERO:total.multiply(feeRate).divide(new BigDecimal("100"),2,java.math.RoundingMode.HALF_UP);
            try(PreparedStatement p=c.prepareStatement("UPDATE hotel_bookings SET status='CANCELLED',updated_at=NOW() WHERE booking_id=?")){p.setString(1,id);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO booking_status_history(booking_id,previous_status,new_status,changed_at) VALUES(?,?,'CANCELLED',NOW())")){p.setString(1,id);p.setString(2,status);p.executeUpdate();}
            c.commit();BookingRepository.invalidateSharedCache(id);return new Cancellation(id,"CANCELLED",fee,currency,hoursUntil>=hours?"FREE_CANCELLATION_WINDOW":"LATE_CANCELLATION_FEE");
        }catch(Exception e){c.rollback();if(e instanceof SQLException s)throw s;if(e instanceof RuntimeException r)throw r;throw new SQLException(e);}finally{c.setAutoCommit(auto);}});
    }

    public void reassignRoom(String id,String roomId)throws SQLException {
        if(roomId==null||roomId.isBlank())throw new IllegalArgumentException("roomId is required");
        withConnection(c->{boolean auto=c.getAutoCommit();c.setAutoCommit(false);try{
            String oldRoom;LocalDate in,out;String status;
            try(PreparedStatement p=c.prepareStatement("SELECT room_id,check_in,check_out,status FROM hotel_bookings WHERE booking_id=? AND deleted_at IS NULL FOR UPDATE")){p.setString(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Booking not found");oldRoom=r.getString(1);in=r.getObject(2,LocalDate.class);out=r.getObject(3,LocalDate.class);status=r.getString(4);}}
            if(!Set.of("PENDING","CONFIRMED").contains(status))throw new IllegalStateException("Only pending or confirmed bookings can be reassigned");
            try(PreparedStatement p=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")){p.setString(1,roomId);p.execute();}
            try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM hotel_rooms WHERE room_id=? AND status='ACTIVE'")){p.setString(1,roomId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Target room is not active");}}
            try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM hotel_bookings WHERE room_id=? AND booking_id<>? AND deleted_at IS NULL AND status NOT IN ('CANCELLED','CHECKED_OUT') AND check_in < ? AND check_out > ? LIMIT 1")){p.setString(1,roomId);p.setString(2,id);p.setObject(3,out);p.setObject(4,in);try(ResultSet r=p.executeQuery()){if(r.next())throw new SQLException("Target room is unavailable for these dates","23P01");}}
            try(PreparedStatement p=c.prepareStatement("UPDATE hotel_bookings SET room_id=?,updated_at=NOW() WHERE booking_id=?")){p.setString(1,roomId);p.setString(2,id);p.executeUpdate();}c.commit();BookingRepository.invalidateSharedCache(id);return null;
        }catch(Exception e){c.rollback();if(e instanceof SQLException s)throw s;if(e instanceof RuntimeException r)throw r;throw new SQLException(e);}finally{c.setAutoCommit(auto);}});
    }
}
