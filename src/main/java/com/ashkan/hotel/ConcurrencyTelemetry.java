package com.ashkan.hotel;

import java.util.concurrent.atomic.LongAdder;

/** Low-cardinality process metrics for concurrency safeguards. */
public final class ConcurrencyTelemetry {
    private static final LongAdder LOCK_ATTEMPTS=new LongAdder(), LOCK_ACQUIRED=new LongAdder(), LOCK_NOT_ACQUIRED=new LongAdder();
    private static final LongAdder IDEMPOTENCY_REPLAYS=new LongAdder(), IDEMPOTENCY_CONFLICTS=new LongAdder(), STOCK_DUPLICATES=new LongAdder();
    private ConcurrencyTelemetry() { }
    static void lock(boolean acquired){LOCK_ATTEMPTS.increment();if(acquired)LOCK_ACQUIRED.increment();else LOCK_NOT_ACQUIRED.increment();}
    static void replay(){IDEMPOTENCY_REPLAYS.increment();} static void conflict(){IDEMPOTENCY_CONFLICTS.increment();} static void stockDuplicate(){STOCK_DUPLICATES.increment();}
    public static String prometheus(){return "# HELP hotelsphere_distributed_lock_attempts_total Redis lease attempts.\n# TYPE hotelsphere_distributed_lock_attempts_total counter\nhotelsphere_distributed_lock_attempts_total "+LOCK_ATTEMPTS.sum()+"\n"+
        "# HELP hotelsphere_distributed_lock_acquired_total Redis leases acquired.\n# TYPE hotelsphere_distributed_lock_acquired_total counter\nhotelsphere_distributed_lock_acquired_total "+LOCK_ACQUIRED.sum()+"\n"+
        "# HELP hotelsphere_distributed_lock_not_acquired_total Redis leases not acquired or unavailable.\n# TYPE hotelsphere_distributed_lock_not_acquired_total counter\nhotelsphere_distributed_lock_not_acquired_total "+LOCK_NOT_ACQUIRED.sum()+"\n"+
        "# HELP hotelsphere_idempotency_replays_total Idempotent API retries.\n# TYPE hotelsphere_idempotency_replays_total counter\nhotelsphere_idempotency_replays_total "+IDEMPOTENCY_REPLAYS.sum()+"\n"+
        "# HELP hotelsphere_idempotency_conflicts_total Idempotency key payload conflicts.\n# TYPE hotelsphere_idempotency_conflicts_total counter\nhotelsphere_idempotency_conflicts_total "+IDEMPOTENCY_CONFLICTS.sum()+"\n"+
        "# HELP hotelsphere_stock_duplicate_movements_total Duplicate stock movement requests ignored.\n# TYPE hotelsphere_stock_duplicate_movements_total counter\nhotelsphere_stock_duplicate_movements_total "+STOCK_DUPLICATES.sum()+"\n";}
}
