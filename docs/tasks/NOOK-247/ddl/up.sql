ALTER TABLE post_media
    ADD COLUMN thumbnail_url VARCHAR(2048) NULL COMMENT '영상 포스터 이미지 URL'
        AFTER media_url;
