package com.ashkan.hotel;

import java.nio.charset.StandardCharsets;

public final class IdempotencySchemaSmokeTest {
    public static void main(String[] args) throws Exception {
        String common=read("/db/migration/common/V10__api_idempotency.sql");
        String booking=read("/db/migration/booking/V4__booking_concurrency_guard.sql");
        String inventory=read("/db/migration/inventory/V5__stock_movement_idempotency.sql");
        if(!common.contains("PRIMARY KEY(scope, idempotency_key)")||!common.contains("request_hash"))throw new AssertionError("API idempotency schema incomplete");
        if(!booking.contains("EXCLUDE USING gist")||!booking.contains("daterange"))throw new AssertionError("Booking overlap constraint missing");
        if(!inventory.contains("ux_stock_movement_idempotency"))throw new AssertionError("Stock movement idempotency index missing");
        System.out.println("IdempotencySchemaSmokeTest: PASS");
    }
    private static String read(String path)throws Exception{try(var in=IdempotencySchemaSmokeTest.class.getResourceAsStream(path)){if(in==null)throw new AssertionError("Missing resource "+path);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
}
