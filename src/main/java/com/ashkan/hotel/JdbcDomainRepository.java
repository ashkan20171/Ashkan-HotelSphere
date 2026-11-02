package com.ashkan.hotel;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Shared JDBC boundary for service-specific domain repositories. */
public abstract class JdbcDomainRepository implements AutoCloseable {
    @FunctionalInterface protected interface SqlWork<T> { T run(Connection connection) throws SQLException; }
    private final JdbcConnectionPool pool;
    protected JdbcDomainRepository(String url, String user, String password, int poolSize) {
        pool = new JdbcConnectionPool(Objects.requireNonNull(url), user, password, poolSize);
    }
    protected final <T> T withConnection(SqlWork<T> work) throws SQLException {
        Connection connection = pool.acquire();
        try { return work.run(connection); }
        finally { pool.release(connection); }
    }
    @Override public final void close() { pool.close(); }
}
