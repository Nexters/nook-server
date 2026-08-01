DROP TABLE IF EXISTS media_url_caches;
DROP TABLE IF EXISTS bright_data_responses;

ALTER TABLE group_posts DROP COLUMN deleted_at;
ALTER TABLE user_groups DROP COLUMN deleted_at;
ALTER TABLE user_saved_posts DROP COLUMN deleted_at;
