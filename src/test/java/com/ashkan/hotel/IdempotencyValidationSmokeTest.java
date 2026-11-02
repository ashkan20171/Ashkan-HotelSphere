package com.ashkan.hotel;

public final class IdempotencyValidationSmokeTest {
    public static void main(String[] args) throws Exception {
        PostgresIdempotencyStore store=new PostgresIdempotencyStore("jdbc:postgresql://127.0.0.1:1/unused","x","x");
        boolean blank=false;try{store.reserve("booking"," ","hash","id");}catch(IllegalArgumentException expected){blank=true;}if(!blank)throw new AssertionError("Blank idempotency key accepted");
        boolean longKey=false;try{store.reserve("booking","k".repeat(161),"hash","id");}catch(IllegalArgumentException expected){longKey=true;}if(!longKey)throw new AssertionError("Oversized idempotency key accepted");
        String metrics=ConcurrencyTelemetry.prometheus();if(!metrics.contains("hotelsphere_distributed_lock_attempts_total")||metrics.contains("\\n# TYPE"))throw new AssertionError("Concurrency metrics format is invalid");
        System.out.println("IdempotencyValidationSmokeTest: PASS");
    }
}
