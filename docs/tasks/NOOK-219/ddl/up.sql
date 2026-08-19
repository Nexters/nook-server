ALTER TABLE post_places
    ADD COLUMN source_media_sequence INT NULL COMMENT '원본 게시물에서 장소에 대응하는 미디어 노출 순서'
        AFTER display_order;

ALTER TABLE user_saved_post_places
    ADD COLUMN thumbnail_url VARCHAR(2048) NULL COMMENT '원본 게시물에서 이 장소에 대응하는 썸네일 URL'
        AFTER display_order;
