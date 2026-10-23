-- V15: Secure album sharing & permission system
-- 1. Soft delete for photos
ALTER TABLE photos ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE photos ADD COLUMN deleted_by BIGINT NULL;

-- 2. Token hashing for share_links
ALTER TABLE share_links ADD COLUMN token_hash VARCHAR(64) NULL;
-- Backfill: hash existing plaintext tokens with SHA-256
UPDATE share_links SET token_hash = SHA2(token, 256) WHERE token IS NOT NULL;
ALTER TABLE share_links MODIFY COLUMN token_hash VARCHAR(64) NOT NULL;
ALTER TABLE share_links ADD UNIQUE INDEX uq_share_links_token_hash (token_hash);
-- Keep token column for now (will drop after verifying migration success)
-- Old unique index on token stays until token column is dropped

-- 3. Password protection placeholder for share_links
ALTER TABLE share_links ADD COLUMN password_hash VARCHAR(60) NULL;

-- 4. Download permission separation placeholder
ALTER TABLE share_links ADD COLUMN allow_download BOOLEAN NOT NULL DEFAULT TRUE;

-- 5. Audit log detail field
ALTER TABLE audit_logs ADD COLUMN detail VARCHAR(500) NULL;

-- 6. Index for soft-deleted photos (queries filter by deleted_at IS NULL)
CREATE INDEX idx_photos_deleted_at ON photos (deleted_at);
