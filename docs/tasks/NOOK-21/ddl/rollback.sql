ALTER TABLE post_places
    DROP COLUMN bookmarked;

ALTER TABLE posts
    DROP COLUMN memo,
    ADD UNIQUE KEY idx_u_source_type_external_post_id (source_type, external_post_id);
