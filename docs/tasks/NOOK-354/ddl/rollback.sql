-- Stop summary publisher before rollback. Removes deduplication history only.
ALTER TABLE posts DROP COLUMN parsing_alert_fingerprint, ALGORITHM=INSTANT;
