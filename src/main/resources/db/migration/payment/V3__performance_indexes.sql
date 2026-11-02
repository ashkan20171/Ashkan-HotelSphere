-- Query-focused indexes for common service access patterns.
CREATE INDEX IF NOT EXISTS ix_payment_booking_status_recent ON payment_transactions(booking_id,status,created_at DESC) INCLUDE(amount,currency);
CREATE INDEX IF NOT EXISTS ix_payment_captured_recent ON payment_transactions(created_at DESC) WHERE status = 'CAPTURED';
