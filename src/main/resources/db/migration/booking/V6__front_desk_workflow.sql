CREATE TABLE IF NOT EXISTS cancellation_policy (
    policy_id VARCHAR(40) PRIMARY KEY,
    free_cancellation_hours INTEGER NOT NULL DEFAULT 24 CHECK (free_cancellation_hours BETWEEN 0 AND 8760),
    late_cancellation_fee_percent NUMERIC(5,2) NOT NULL DEFAULT 50.00 CHECK (late_cancellation_fee_percent BETWEEN 0 AND 100),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO cancellation_policy(policy_id,free_cancellation_hours,late_cancellation_fee_percent)
VALUES ('DEFAULT',24,50.00) ON CONFLICT(policy_id) DO NOTHING;
CREATE TABLE IF NOT EXISTS booking_status_history (
    history_id BIGSERIAL PRIMARY KEY,
    booking_id VARCHAR(80) NOT NULL,
    previous_status VARCHAR(24) NOT NULL,
    new_status VARCHAR(24) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    changed_by VARCHAR(120)
);
CREATE INDEX IF NOT EXISTS ix_booking_status_history_booking ON booking_status_history(booking_id,changed_at DESC);
