package com.ashkan.hotel;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class BookingRepository extends JdbcDomainRepository {
    public record Booking(String id,String guestId,String roomId,LocalDate checkIn,LocalDate checkOut,String status,BigDecimal total,String currency) {
        public Booking { required(id,"id"); required(guestId,"guestId"); required(roomId,"roomId"); Objects.requireNonNull(checkIn); Objects.requireNonNull(checkOut); if(!checkOut.isAfter(checkIn))throw new IllegalArgumentException("checkOut must be after checkIn"); if(total==null||total.signum()<0)throw new IllegalArgumentException("total must be non-negative"); status=Objects.requireNonNullElse(status,"PENDING").toUpperCase(Locale.ROOT); if(!Set.of("PENDING","CONFIRMED","CHECKED_IN","CHECKED_OUT","CANCELLED").contains(status))throw new IllegalArgumentException("Invalid booking status"); currency=Objects.requireNonNullElse(currency,"EUR").toUpperCase(Locale.ROOT); if(!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("Invalid currency"); }
    }
    private static final TtlCache<String,Optional<Booking>> cache=new TtlCache<>(CacheSettings.ttlMillis(),CacheSettings.maxEntries());
    static void invalidateSharedCache(String id){cache.invalidate(id);new RedisCacheClient().delete("hotelsphere:booking:"+id);}
    private final RedisCacheClient redis=new RedisCacheClient();
    private String redisKey(String id){return "hotelsphere:booking:"+id;}
    public BookingRepository(String url,String user,String password,int poolSize){super(url,user,password,poolSize);}
    public void save(Booking b)throws SQLException{
        String lockKey="hotelsphere:lock:booking-room:"+b.roomId();
        try(RedisDistributedLock lock=RedisDistributedLock.tryAcquire(redis,lockKey,15000)) {
            withConnection(c->{boolean auto=c.getAutoCommit();c.setAutoCommit(false);try{
                // Database advisory lock is authoritative even when Redis is unavailable.
                try(PreparedStatement p=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")){p.setString(1,b.roomId());p.execute();}
                if(!Set.of("CANCELLED","CHECKED_OUT").contains(b.status())){
                    try(PreparedStatement p=c.prepareStatement("SELECT booking_id FROM hotel_bookings WHERE room_id=? AND deleted_at IS NULL AND status NOT IN ('CANCELLED','CHECKED_OUT') AND check_in < ? AND check_out > ? AND booking_id <> ? LIMIT 1")){
                        p.setString(1,b.roomId());p.setObject(2,b.checkOut());p.setObject(3,b.checkIn());p.setString(4,b.id());
                        try(ResultSet r=p.executeQuery()){if(r.next())throw new SQLException("Room is already booked for the requested date range","23P01");}
                    }
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO hotel_bookings(booking_id,guest_id,room_id,check_in,check_out,status,total_amount,currency) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(booking_id) DO UPDATE SET guest_id=EXCLUDED.guest_id,room_id=EXCLUDED.room_id,check_in=EXCLUDED.check_in,check_out=EXCLUDED.check_out,status=EXCLUDED.status,total_amount=EXCLUDED.total_amount,currency=EXCLUDED.currency,updated_at=NOW(),deleted_at=NULL")){p.setString(1,b.id());p.setString(2,b.guestId());p.setString(3,b.roomId());p.setObject(4,b.checkIn());p.setObject(5,b.checkOut());p.setString(6,b.status());p.setBigDecimal(7,b.total());p.setString(8,b.currency());p.executeUpdate();}
                c.commit();
            }catch(SQLException e){c.rollback();throw e;}finally{c.setAutoCommit(auto);}return null;});
        }
        cache.invalidate(b.id());redis.delete(redisKey(b.id()));
    }
    public Optional<Booking> find(String id)throws SQLException{Optional<Booking> cached=cache.get(id);if(cached!=null)return cached;String remote=redis.get(redisKey(id));if(remote!=null){try{String[] a=CacheCodec.unpack(remote,8);Optional<Booking> value=Optional.of(new Booking(a[0],a[1],a[2],LocalDate.parse(a[3]),LocalDate.parse(a[4]),a[5],new BigDecimal(a[6]),a[7]));cache.put(id,value);return value;}catch(RuntimeException ignored){redis.delete(redisKey(id));}}Optional<Booking> result=withConnection(c->{try(PreparedStatement p=c.prepareStatement("SELECT * FROM hotel_bookings WHERE booking_id=? AND deleted_at IS NULL")){p.setString(1,id);try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(new Booking(r.getString("booking_id"),r.getString("guest_id"),r.getString("room_id"),r.getObject("check_in",LocalDate.class),r.getObject("check_out",LocalDate.class),r.getString("status"),r.getBigDecimal("total_amount"),r.getString("currency"))):Optional.empty();}}});cache.put(id,result);result.ifPresent(b->redis.set(redisKey(id),CacheCodec.pack(b.id(),b.guestId(),b.roomId(),b.checkIn().toString(),b.checkOut().toString(),b.status(),b.total().toPlainString(),b.currency()),(int)Math.max(1,CacheSettings.ttlMillis()/1000)));return result;}
    public List<Booking> list(int limit,int offset,String status)throws SQLException{return withConnection(c->{String sql="SELECT * FROM hotel_bookings WHERE deleted_at IS NULL"+(status==null||status.isBlank()?"":" AND status=?")+" ORDER BY created_at DESC LIMIT ? OFFSET ?";try(PreparedStatement p=c.prepareStatement(sql)){int i=1;if(status!=null&&!status.isBlank())p.setString(i++,status.toUpperCase(Locale.ROOT));p.setInt(i++,limit);p.setInt(i,offset);try(ResultSet r=p.executeQuery()){List<Booking> out=new ArrayList<>();while(r.next())out.add(new Booking(r.getString("booking_id"),r.getString("guest_id"),r.getString("room_id"),r.getObject("check_in",LocalDate.class),r.getObject("check_out",LocalDate.class),r.getString("status"),r.getBigDecimal("total_amount"),r.getString("currency")));return out;}}});}
    public List<Booking> listForGuest(String guestId,int limit)throws SQLException{if(guestId==null||guestId.isBlank())throw new IllegalArgumentException("guestId is required");int safe=Math.max(1,Math.min(limit,100));return withConnection(c->{try(PreparedStatement p=c.prepareStatement("SELECT * FROM hotel_bookings WHERE guest_id=? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT ?")){p.setString(1,guestId);p.setInt(2,safe);try(ResultSet r=p.executeQuery()){List<Booking> out=new ArrayList<>();while(r.next())out.add(new Booking(r.getString("booking_id"),r.getString("guest_id"),r.getString("room_id"),r.getObject("check_in",LocalDate.class),r.getObject("check_out",LocalDate.class),r.getString("status"),r.getBigDecimal("total_amount"),r.getString("currency")));return out;}}});}
    public void softDelete(String id)throws SQLException{withConnection(c->{try(PreparedStatement p=c.prepareStatement("UPDATE hotel_bookings SET deleted_at=NOW(),updated_at=NOW() WHERE booking_id=? AND deleted_at IS NULL")){p.setString(1,id);p.executeUpdate();}return null;});cache.invalidate(id);redis.delete(redisKey(id));}
    private static void required(String s,String n){if(s==null||s.isBlank())throw new IllegalArgumentException(n+" is required");}
}
