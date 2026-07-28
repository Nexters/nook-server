ALTER TABLE group_posts
    ADD CONSTRAINT fk_group_posts_group
        FOREIGN KEY (group_id) REFERENCES user_groups (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_group_posts_user_saved_post
        FOREIGN KEY (user_saved_post_id) REFERENCES user_saved_posts (id) ON DELETE CASCADE;

ALTER TABLE place_parsing_jobs
    ADD CONSTRAINT fk_place_parsing_jobs_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;

ALTER TABLE post_content_parsing_jobs
    ADD CONSTRAINT fk_post_content_parsing_jobs_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;

ALTER TABLE post_hashtags
    ADD CONSTRAINT fk_post_hashtags_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;

ALTER TABLE post_media
    ADD CONSTRAINT fk_post_media_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;

ALTER TABLE post_places
    ADD CONSTRAINT fk_post_places_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_post_places_place
        FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE;

ALTER TABLE refresh_tokens
    RENAME INDEX idx_replaced_by_token_id TO fk_refresh_tokens_replaced_by_token_id;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_member_id
        FOREIGN KEY (member_id) REFERENCES members (id),
    ADD CONSTRAINT fk_refresh_tokens_replaced_by_token_id
        FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens (id);

ALTER TABLE social_accounts
    ADD CONSTRAINT fk_social_accounts_member_id
        FOREIGN KEY (member_id) REFERENCES members (id);

ALTER TABLE user_place_bookmarks
    ADD CONSTRAINT fk_user_place_bookmarks_place
        FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE;

ALTER TABLE user_saved_posts
    ADD CONSTRAINT fk_user_saved_posts_post
        FOREIGN KEY (post_id) REFERENCES posts (id);
