ALTER TABLE inventory_stock_movements ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160);
CREATE UNIQUE INDEX IF NOT EXISTS ux_stock_movement_idempotency ON inventory_stock_movements(idempotency_key) WHERE idempotency_key IS NOT NULL;
