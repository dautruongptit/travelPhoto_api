-- V16: Remove plaintext tokens from share_links (security hardening)
-- Token lookup is now by token_hash only; plaintext is no longer needed.

-- 1. Make token column nullable first (was NOT NULL)
ALTER TABLE share_links MODIFY COLUMN token VARCHAR(64) NULL;

-- 2. Now null out plaintext tokens
UPDATE share_links SET token = NULL WHERE token IS NOT NULL;
