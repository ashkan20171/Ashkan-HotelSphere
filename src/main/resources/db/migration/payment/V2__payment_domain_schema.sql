CREATE TABLE IF NOT EXISTS payment_transactions (
 transaction_id VARCHAR(80) PRIMARY KEY, booking_id VARCHAR(80) NOT NULL,
 amount NUMERIC(14,2) NOT NULL CHECK (amount > 0), currency CHAR(3) NOT NULL DEFAULT 'EUR',
 method VARCHAR(24) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'INITIATED', provider_reference VARCHAR(160),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CONSTRAINT ck_payment_status CHECK (status IN ('INITIATED','AUTHORIZED','CAPTURED','FAILED','REFUNDED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_provider_ref ON payment_transactions(provider_reference) WHERE provider_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_payment_booking ON payment_transactions(booking_id,created_at DESC);
CREATE INDEX IF NOT EXISTS ix_payment_status ON payment_transactions(status,created_at DESC);
