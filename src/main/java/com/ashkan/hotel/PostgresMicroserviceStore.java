package com.ashkan.hotel;

import java.sql.*;
import java.util.*;

/** PostgreSQL persistence isolated per microservice database. */
public final class PostgresMicroserviceStore implements ServiceDataStore {
    private final JdbcConnectionPool pool;
    private final String serviceName, url, user, password;
    public PostgresMicroserviceStore(String serviceName, String url, String user, String password, int poolSize) {
        this.serviceName=Objects.requireNonNull(serviceName); this.url=Objects.requireNonNull(url); this.user=Objects.requireNonNull(user); this.password=Objects.requireNonNull(password);
        this.pool=new JdbcConnectionPool(url,user,password,poolSize);
    }
    @Override public void initialize() throws SQLException {
        try {
            Class<?> flywayType=Class.forName("org.flywaydb.core.Flyway");
            Object config=flywayType.getMethod("configure").invoke(null);
            config=config.getClass().getMethod("dataSource",String.class,String.class,String.class).invoke(config,
                    url,user,password);
            config=config.getClass().getMethod("locations",String[].class).invoke(config,(Object)new String[]{"classpath:db/migration/common","classpath:db/migration/"+serviceName});
            Object flyway=config.getClass().getMethod("load").invoke(config);
            flyway.getClass().getMethod("migrate").invoke(flyway);
        } catch(ClassNotFoundException e) {
            throw new SQLException("Flyway is required for PostgreSQL migrations",e);
        } catch(ReflectiveOperationException e) {
            throw new SQLException("Flyway migration failed",e);
        }
        try(Connection c=pool.acquire();PreparedStatement p=c.prepareStatement("SELECT 1 FROM service_records LIMIT 1")){p.executeQuery();}
    }
    @Override public List<String> ids() throws SQLException {
        Connection c=pool.acquire();try(PreparedStatement p=c.prepareStatement("SELECT id FROM service_records ORDER BY created_at,id");ResultSet r=p.executeQuery()){List<String> out=new ArrayList<>();while(r.next())out.add(r.getString(1));return out;}finally{pool.release(c);}
    }
    @Override public String get(String id) throws SQLException {
        Connection c=pool.acquire();try(PreparedStatement p=c.prepareStatement("SELECT payload FROM service_records WHERE id=?")){p.setString(1,id);try(ResultSet r=p.executeQuery()){return r.next()?r.getString(1):null;}}finally{pool.release(c);}
    }
    @Override public void insert(String id,String payload) throws SQLException {
        Connection c=pool.acquire();try(PreparedStatement p=c.prepareStatement("INSERT INTO service_records(id,payload) VALUES(?,?)")){p.setString(1,id);p.setString(2,payload);p.executeUpdate();}finally{pool.release(c);}
    }
    @Override public void delete(String id) throws SQLException {
        Connection c=pool.acquire();try(PreparedStatement p=c.prepareStatement("DELETE FROM service_records WHERE id=?")){p.setString(1,id);p.executeUpdate();}finally{pool.release(c);}
    }
    @Override public void close(){pool.close();}
    public String serviceName(){return serviceName;}
}
