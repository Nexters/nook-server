DROP TABLE IF EXISTS place_parsing_jobs;
DROP TABLE IF EXISTS post_hashtags;

ALTER TABLE posts
    DROP COLUMN source_location_tag;
