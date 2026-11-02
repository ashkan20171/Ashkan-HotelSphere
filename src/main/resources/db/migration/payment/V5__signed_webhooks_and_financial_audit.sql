CREATE TABLE IF NOT EXISTS payment_webhook_events (
  id BIGSERIAL PRIMARY KEY,
  provider VARCHAR(40) NOT NULL,
  event_id VARCHAR(160) NOT NULL,
  payment_id VARCHAR(80) NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  payload_sha256 CHAR(64) NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(provider,event_id)
);
CREATE INDEX IF NOT EXISTS ix_payment_webhook_payment ON payment_webhook_events(payment_id,received_at DESC);
CREATE TABLE IF NOT EXISTS financial_audit_events (
  id BIGSERIAL PRIMARY KEY,
  actor VARCHAR(120) NOT NULL,
  action VARCHAR(80) NOT NULL,
  resource_id VARCHAR(120) NOT NULL,
  details TEXT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_financial_audit_resource ON financial_audit_events(resource_id,occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_financial_audit_time ON financial_audit_events(occurred_at DESC);
