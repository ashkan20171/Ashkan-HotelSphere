CREATE TABLE IF NOT EXISTS hotel_rooms (
    room_id VARCHAR(80) PRIMARY KEY,
    room_type VARCHAR(80) NOT NULL,
    room_number VARCHAR(30) NOT NULL UNIQUE,
    floor_number INTEGER NOT NULL DEFAULT 1 CHECK (floor_number >= 0),
    capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','OUT_OF_SERVICE','INACTIVE')),
    amenities JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_hotel_rooms_type_status ON hotel_rooms(room_type,status,capacity);

CREATE TABLE IF NOT EXISTS rate_plans (
    rate_plan_id VARCHAR(80) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    room_type VARCHAR(80) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'EUR' CHECK (currency ~ '^[A-Z]{3}$'),
    base_rate NUMERIC(12,2) NOT NULL CHECK (base_rate > 0),
    weekend_multiplier NUMERIC(5,3) NOT NULL DEFAULT 1.000 CHECK (weekend_multiplier BETWEEN 0.5 AND 5.0),
    high_demand_multiplier NUMERIC(5,3) NOT NULL DEFAULT 1.000 CHECK (high_demand_multiplier BETWEEN 0.5 AND 5.0),
    max_occupancy INTEGER NOT NULL DEFAULT 2 CHECK (max_occupancy BETWEEN 1 AND 20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);
CREATE INDEX IF NOT EXISTS idx_rate_plans_type_active ON rate_plans(room_type,active);
