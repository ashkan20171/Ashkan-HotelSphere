CREATE TABLE IF NOT EXISTS api_idempotency (
  scope VARCHAR(80) NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  resource_id VARCHAR(80) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '24 hours'),
  PRIMARY KEY(scope, idempotency_key)
);
CREATE INDEX IF NOT EXISTS ix_api_idempotency_expiry ON api_idempotency(expires_at);
