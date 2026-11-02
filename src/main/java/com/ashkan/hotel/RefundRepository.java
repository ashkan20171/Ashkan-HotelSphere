package com.ashkan.hotel;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Append-only refund ledger. A unique idempotency key prevents duplicate refund requests. */
public final class RefundRepository extends JdbcDomainRepository {
    public record Refund(String id, String paymentId, String bookingId, BigDecimal amount, String currency,
                         String status, String providerReference, String idempotencyKey) {}
    public RefundRepository(String url,String user,String password,int poolSize){super(url,user,password,poolSize);}
    public Refund save(Refund r)throws SQLException{return withConnection(c->{try(PreparedStatement p=c.prepareStatement("INSERT INTO payment_refunds(refund_id,payment_id,booking_id,amount,currency,status,provider_reference,idempotency_key) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(idempotency_key) DO NOTHING")){p.setString(1,r.id());p.setString(2,r.paymentId());p.setString(3,r.bookingId());p.setBigDecimal(4,r.amount());p.setString(5,r.currency());p.setString(6,r.status());p.setString(7,r.providerReference());p.setString(8,r.idempotencyKey());p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("SELECT refund_id,payment_id,booking_id,amount,currency,status,provider_reference,idempotency_key FROM payment_refunds WHERE idempotency_key=?")){p.setString(1,r.idempotencyKey());try(ResultSet rs=p.executeQuery()){if(!rs.next())throw new SQLException("Refund ledger write failed");return new Refund(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBigDecimal(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8));}}});}
    public Optional<Refund> findByIdempotencyKey(String key)throws SQLException{return withConnection(c->{try(PreparedStatement p=c.prepareStatement("SELECT refund_id,payment_id,booking_id,amount,currency,status,provider_reference,idempotency_key FROM payment_refunds WHERE idempotency_key=?")){p.setString(1,key);try(ResultSet rs=p.executeQuery()){return rs.next()?Optional.of(new Refund(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBigDecimal(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8))):Optional.empty();}}});}
    public List<Refund> listForPayment(String paymentId)throws SQLException{return withConnection(c->{try(PreparedStatement p=c.prepareStatement("SELECT refund_id,payment_id,booking_id,amount,currency,status,provider_reference,idempotency_key FROM payment_refunds WHERE payment_id=? ORDER BY created_at DESC")){p.setString(1,paymentId);try(ResultSet rs=p.executeQuery()){List<Refund> out=new ArrayList<>();while(rs.next())out.add(new Refund(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBigDecimal(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8)));return out;}}});}
}
