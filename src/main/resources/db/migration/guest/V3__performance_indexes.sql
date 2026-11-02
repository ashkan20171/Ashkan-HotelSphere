-- Query-focused indexes for common service access patterns.
CREATE INDEX IF NOT EXISTS ix_guest_email_lookup ON guest_profiles(lower(email)) WHERE email IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_guest_phone_lookup ON guest_profiles(phone) WHERE phone IS NOT NULL AND deleted_at IS NULL;
