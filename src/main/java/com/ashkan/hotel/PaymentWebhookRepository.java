package com.ashkan.hotel;

import java.sql.*;

/** Durable provider-event inbox; the unique provider event ID makes callback delivery idempotent. */
public final class PaymentWebhookRepository extends JdbcDomainRepository {
    public PaymentWebhookRepository(String url,String user,String password,int poolSize){super(url,user,password,poolSize);}
    public String payloadHash(String provider,String eventId)throws SQLException {
        return withConnection(c->{try(PreparedStatement p=c.prepareStatement("SELECT payload_sha256 FROM payment_webhook_events WHERE provider=? AND event_id=?")){
            p.setString(1,provider);p.setString(2,eventId);try(ResultSet r=p.executeQuery()){return r.next()?r.getString(1):null;}
        }});
    }
    public boolean claim(String provider,String eventId,String paymentId,String eventType,String payloadHash)throws SQLException {
        return withConnection(c->{try(PreparedStatement p=c.prepareStatement("INSERT INTO payment_webhook_events(provider,event_id,payment_id,event_type,payload_sha256) VALUES(?,?,?,?,?) ON CONFLICT(provider,event_id) DO NOTHING")){
            p.setString(1,provider);p.setString(2,eventId);p.setString(3,paymentId);p.setString(4,eventType);p.setString(5,payloadHash);return p.executeUpdate()==1;
        }});
    }
}
