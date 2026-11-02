CREATE TABLE IF NOT EXISTS guest_profiles (
 guest_id VARCHAR(80) PRIMARY KEY, first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100) NOT NULL,
 email VARCHAR(254), phone VARCHAR(40), nationality VARCHAR(80), date_of_birth DATE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), deleted_at TIMESTAMPTZ,
 CONSTRAINT ck_guest_email CHECK (email IS NULL OR position('@' in email) > 1)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_guest_email_active ON guest_profiles(lower(email)) WHERE email IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_guest_name ON guest_profiles(lower(last_name),lower(first_name)) WHERE deleted_at IS NULL;
CREATE TABLE IF NOT EXISTS guest_history (
 history_id BIGSERIAL PRIMARY KEY, guest_id VARCHAR(80) NOT NULL REFERENCES guest_profiles(guest_id),
 event_type VARCHAR(60) NOT NULL, description TEXT, occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_guest_history_timeline ON guest_history(guest_id,occurred_at DESC);
