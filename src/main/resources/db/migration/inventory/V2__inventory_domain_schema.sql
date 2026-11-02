CREATE TABLE IF NOT EXISTS inventory_products (
 product_id VARCHAR(80) PRIMARY KEY, sku VARCHAR(80) NOT NULL UNIQUE, name VARCHAR(180) NOT NULL,
 unit VARCHAR(24) NOT NULL, quantity_on_hand NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (quantity_on_hand >= 0),
 reorder_level NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), deleted_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS ix_inventory_reorder ON inventory_products(quantity_on_hand,reorder_level) WHERE deleted_at IS NULL;
CREATE TABLE IF NOT EXISTS inventory_stock_movements (
 movement_id BIGSERIAL PRIMARY KEY, product_id VARCHAR(80) NOT NULL REFERENCES inventory_products(product_id),
 movement_type VARCHAR(20) NOT NULL CHECK (movement_type IN ('RECEIPT','ISSUE','ADJUSTMENT','RETURN')),
 quantity_delta NUMERIC(14,3) NOT NULL CHECK (quantity_delta <> 0), reference VARCHAR(160), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_stock_movements_product ON inventory_stock_movements(product_id,created_at DESC);
