-- V16: Remove plaintext tokens from share_links (security hardening)
-- Token lookup is now by token_hash only; plaintext is no longer needed.

-- 1. Drop the unique constraint and index on plaintext token
--    V1 created: `token VARCHAR(64) NOT NULL UNIQUE` → auto-named index `token`
--    V1 also created: `CREATE INDEX idx_share_token ON share_links(token)`
ALTER TABLE share_links DROP INDEX token;
ALTER TABLE share_links DROP INDEX idx_share_token;

-- 2. Null out plaintext tokens
UPDATE share_links SET token = NULL WHERE token IS NOT NULL;

-- 3. Make token column nullable (was NOT NULL)
ALTER TABLE share_links MODIFY COLUMN token VARCHAR(64) NULL;
