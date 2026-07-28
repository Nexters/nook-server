ALTER TABLE user_saved_posts
    DROP INDEX idx_u_user_id_post_id;

ALTER TABLE posts
    DROP INDEX idx_u_source_type_external_post_id;

-- NOOK-71의 중복 데이터 병합은 되돌릴 수 없다.
