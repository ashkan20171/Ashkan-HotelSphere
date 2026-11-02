package com.ashkan.hotel;

import java.sql.*;
import java.util.Objects;

/** Persists idempotency keys so retries resolve to the same domain resource across service instances. */
public final class PostgresIdempotencyStore {
    public record Reservation(String resourceId, boolean replay) { }
    private final String url, user, password;
    public PostgresIdempotencyStore(String url, String user, String password) {
        this.url=Objects.requireNonNull(url); this.user=user; this.password=password;
    }
    public Reservation reserve(String scope, String key, String requestHash, String proposedResourceId) throws SQLException {
        if(key==null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key cannot be blank");
        if(key.length()>160) throw new IllegalArgumentException("Idempotency-Key must be at most 160 characters");
        try(Connection c=DriverManager.getConnection(url,user,password)) {
            try(PreparedStatement cleanup=c.prepareStatement("DELETE FROM api_idempotency WHERE expires_at < NOW()")){cleanup.executeUpdate();}
            try(PreparedStatement insert=c.prepareStatement("INSERT INTO api_idempotency(scope,idempotency_key,request_hash,resource_id) VALUES(?,?,?,?) ON CONFLICT(scope,idempotency_key) DO NOTHING")) {
                insert.setString(1,scope); insert.setString(2,key); insert.setString(3,requestHash); insert.setString(4,proposedResourceId);
                int inserted=insert.executeUpdate();
                try(PreparedStatement select=c.prepareStatement("SELECT request_hash,resource_id FROM api_idempotency WHERE scope=? AND idempotency_key=? AND expires_at > NOW()")) {
                    select.setString(1,scope); select.setString(2,key);
                    try(ResultSet r=select.executeQuery()) {
                        if(!r.next()) throw new SQLException("Idempotency reservation expired; retry with a new key");
                        if(!requestHash.equals(r.getString(1))) { ConcurrencyTelemetry.conflict(); throw new IdempotencyConflictException("Idempotency-Key was already used with a different request body"); }
                        if(inserted==0) ConcurrencyTelemetry.replay();
                        return new Reservation(r.getString(2),inserted==0);
                    }
                }
            }
        }
    }
    public static final class IdempotencyConflictException extends SQLException {
        public IdempotencyConflictException(String message){super(message,"23505");}
    }
}
