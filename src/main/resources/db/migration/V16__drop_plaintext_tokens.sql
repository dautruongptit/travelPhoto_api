-- V16: Remove plaintext tokens from share_links (security hardening)
-- Token lookup is now by token_hash only; plaintext is no longer needed.
UPDATE share_links SET token = NULL WHERE token IS NOT NULL;

-- Make token column nullable (was NOT NULL before)
ALTER TABLE share_links MODIFY COLUMN token VARCHAR(255) NULL;

-- Drop the old unique index on plaintext token
ALTER TABLE share_links DROP INDEX IF EXISTS uq_share_links_token;
