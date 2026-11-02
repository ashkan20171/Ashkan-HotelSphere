-- Query-focused indexes for common service access patterns.
CREATE INDEX IF NOT EXISTS ix_booking_room_dates_active ON hotel_bookings(room_id,check_in,check_out) INCLUDE(status,total_amount) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_booking_recent_active ON hotel_bookings(created_at DESC) WHERE deleted_at IS NULL;
