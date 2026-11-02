CREATE TABLE IF NOT EXISTS hotel_bookings (
 booking_id VARCHAR(80) PRIMARY KEY, guest_id VARCHAR(80) NOT NULL, room_id VARCHAR(80) NOT NULL,
 check_in DATE NOT NULL, check_out DATE NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 total_amount NUMERIC(14,2) NOT NULL CHECK (total_amount >= 0), currency CHAR(3) NOT NULL DEFAULT 'EUR',
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), deleted_at TIMESTAMPTZ,
 CONSTRAINT ck_booking_dates CHECK (check_out > check_in),
 CONSTRAINT ck_booking_status CHECK (status IN ('PENDING','CONFIRMED','CHECKED_IN','CHECKED_OUT','CANCELLED'))
);
CREATE INDEX IF NOT EXISTS ix_booking_guest ON hotel_bookings(guest_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_booking_dates ON hotel_bookings(check_in,check_out) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_booking_status ON hotel_bookings(status,created_at DESC) WHERE deleted_at IS NULL;
