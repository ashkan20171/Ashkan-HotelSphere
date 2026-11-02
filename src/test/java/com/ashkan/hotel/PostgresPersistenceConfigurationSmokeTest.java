package com.ashkan.hotel;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PostgresPersistenceConfigurationSmokeTest {
    public static void main(String[] args) throws Exception {
        boolean rejected=false;
        try { new JdbcConnectionPool("jdbc:sqlite:test.db","","",2); }
        catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected) throw new AssertionError("Non-PostgreSQL URL accepted");
        JdbcConnectionPool pool=new JdbcConnectionPool("jdbc:postgresql://localhost:5432/ashkan_booking","hotel","secret",3);
        pool.close();
        Path migration=Path.of("src/main/resources/db/migration/common/V1__create_service_records.sql");
        if(!Files.exists(migration)) throw new AssertionError("Flyway migration missing");
        String sql=Files.readString(migration);
        if(!sql.contains("CREATE TABLE IF NOT EXISTS service_records") || !sql.contains("PRIMARY KEY"))
            throw new AssertionError("Migration schema incomplete");
        System.out.println("PostgresPersistenceConfigurationSmokeTest passed");
    }
}
