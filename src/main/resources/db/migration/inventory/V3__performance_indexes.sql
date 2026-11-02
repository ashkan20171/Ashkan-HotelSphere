-- Query-focused indexes for common service access patterns.
CREATE INDEX IF NOT EXISTS ix_inventory_sku_active ON inventory_products(sku) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_stock_movement_recent ON inventory_stock_movements(created_at DESC);
