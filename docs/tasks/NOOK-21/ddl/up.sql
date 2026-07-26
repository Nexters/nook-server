ALTER TABLE posts
    DROP INDEX idx_u_source_type_external_post_id,
    ADD COLUMN memo TEXT NULL COMMENT '사용자가 작성한 게시물 메모'
        AFTER title;

ALTER TABLE post_places
    ADD COLUMN bookmarked BOOLEAN NOT NULL DEFAULT TRUE COMMENT '사용자 지도 북마크 여부'
        AFTER display_order;
