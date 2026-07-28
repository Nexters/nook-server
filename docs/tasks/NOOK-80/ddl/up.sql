ALTER TABLE group_posts
    DROP FOREIGN KEY fk_group_posts_group,
    DROP FOREIGN KEY fk_group_posts_user_saved_post;

ALTER TABLE place_parsing_jobs
    DROP FOREIGN KEY fk_place_parsing_jobs_post;

ALTER TABLE post_content_parsing_jobs
    DROP FOREIGN KEY fk_post_content_parsing_jobs_post;

ALTER TABLE post_hashtags
    DROP FOREIGN KEY fk_post_hashtags_post;

ALTER TABLE post_media
    DROP FOREIGN KEY fk_post_media_post;

ALTER TABLE post_places
    DROP FOREIGN KEY fk_post_places_post,
    DROP FOREIGN KEY fk_post_places_place;

ALTER TABLE refresh_tokens
    DROP FOREIGN KEY fk_refresh_tokens_member_id,
    DROP FOREIGN KEY fk_refresh_tokens_replaced_by_token_id,
    RENAME INDEX fk_refresh_tokens_replaced_by_token_id TO idx_replaced_by_token_id;

ALTER TABLE social_accounts
    DROP FOREIGN KEY fk_social_accounts_member_id;

ALTER TABLE user_place_bookmarks
    DROP FOREIGN KEY fk_user_place_bookmarks_place;

ALTER TABLE user_saved_posts
    DROP FOREIGN KEY fk_user_saved_posts_post;
