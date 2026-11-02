CREATE TABLE IF NOT EXISTS payment_refunds (
  refund_id VARCHAR(80) PRIMARY KEY,
  payment_id VARCHAR(80) NOT NULL REFERENCES payment_transactions(transaction_id),
  booking_id VARCHAR(80) NOT NULL,
  amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  currency CHAR(3) NOT NULL,
  status VARCHAR(24) NOT NULL CHECK (status IN ('SIMULATED','PENDING','SUCCEEDED','FAILED')),
  provider_reference VARCHAR(160),
  idempotency_key VARCHAR(120) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_payment_refunds_payment ON payment_refunds(payment_id,created_at DESC);
