-- Database-level protection remains authoritative even if Redis is unavailable.
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE hotel_bookings
  ADD CONSTRAINT ex_booking_room_date_overlap
  EXCLUDE USING gist (
    room_id WITH =,
    daterange(check_in, check_out, '[)') WITH &&
  ) WHERE (deleted_at IS NULL AND status NOT IN ('CANCELLED','CHECKED_OUT'));
