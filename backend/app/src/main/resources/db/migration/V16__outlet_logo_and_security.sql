-- ============================================================================
-- V16  Receipt logo storage and till-unlock PIN.
--
-- No object-storage provider (S3/MinIO) is wired up yet, so the logo is stored
-- as a small base64 data URL directly on the outlet row rather than as an
-- object-storage key. logo_object_key (added aspirationally in V2) is left in
-- place for when real object storage is added later.
-- ============================================================================
ALTER TABLE outlets ADD COLUMN IF NOT EXISTS logo_data_url text;

-- pin_hash already exists on users (added in V4) but was never actually used
-- until now; nothing to add there.
