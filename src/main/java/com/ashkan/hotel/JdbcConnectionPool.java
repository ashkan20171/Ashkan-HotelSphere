package com.ashkan.hotel;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Small bounded JDBC pool; uses only JDBC APIs so the application remains Java 17 compilable. */
public final class JdbcConnectionPool implements AutoCloseable {
    private final String url, user, password;
    private final int maxSize;
    private final Semaphore permits;
    private final DataSource hikariDataSource;
    private Object hikariCloseable;
    private final Deque<Connection> idle = new ArrayDeque<>();
    private volatile boolean closed;

    public JdbcConnectionPool(String url, String user, String password, int maxSize) {
        if (url == null || !url.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException("A PostgreSQL JDBC URL is required");
        this.url=url; this.user=user == null ? "" : user; this.password=password == null ? "" : password;
        this.maxSize=Math.max(1,maxSize); this.permits=new Semaphore(this.maxSize,true);
        DataSource hikari=null;
        try {
            Class<?> configType=Class.forName("com.zaxxer.hikari.HikariConfig");
            Object config=configType.getConstructor().newInstance();
            configType.getMethod("setJdbcUrl",String.class).invoke(config,url);
            configType.getMethod("setUsername",String.class).invoke(config,this.user);
            configType.getMethod("setPassword",String.class).invoke(config,this.password);
            configType.getMethod("setMaximumPoolSize",int.class).invoke(config,this.maxSize);
            configType.getMethod("setPoolName",String.class).invoke(config,"ashkan-"+Integer.toHexString(url.hashCode()));
            Class<?> dsType=Class.forName("com.zaxxer.hikari.HikariDataSource");
            Object ds=dsType.getConstructor(configType).newInstance(config);
            hikari=(DataSource)ds; this.hikariCloseable=ds;
        } catch(ClassNotFoundException ignored) { /* fallback pool for plain javac smoke tests */ }
        catch(ReflectiveOperationException e) { throw new IllegalStateException("Unable to initialize HikariCP",e); }
        this.hikariDataSource=hikari;
    }
    public Connection acquire() throws SQLException {
        if (closed) throw new SQLException("Connection pool is closed");
        if(hikariDataSource!=null) return hikariDataSource.getConnection();
        try { if (!permits.tryAcquire(10, TimeUnit.SECONDS)) throw new SQLException("Timed out waiting for database connection"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException("Interrupted waiting for database connection",e); }
        synchronized(idle) {
            while(!idle.isEmpty()) {
                Connection c=idle.removeFirst();
                try { if(c.isValid(2)) return c; else c.close(); } catch(SQLException ignored) { try{c.close();}catch(SQLException ignoredAgain){} }
            }
        }
        try { return DriverManager.getConnection(url,user,password); }
        catch(SQLException e) { permits.release(); throw e; }
    }
    public void release(Connection c) {
        if(c==null)return;
        if(hikariDataSource!=null){try{c.close();}catch(SQLException ignored){}return;}
        if(closed) { try{c.close();}catch(SQLException ignored){} permits.release(); return; }
        try {
            if(c.isClosed() || !c.getAutoCommit()) { c.rollback(); c.setAutoCommit(true); }
            synchronized(idle){idle.addLast(c);}
        } catch(SQLException e) { try{c.close();}catch(SQLException ignored){} permits.release(); }
    }
    @Override public void close() {
        closed=true;
        if(hikariCloseable!=null){try{hikariCloseable.getClass().getMethod("close").invoke(hikariCloseable);}catch(Exception ignored){} }
        synchronized(idle){while(!idle.isEmpty()){try{idle.removeFirst().close();}catch(SQLException ignored){}}}
    }
}
