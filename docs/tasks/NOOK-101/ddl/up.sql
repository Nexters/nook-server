ALTER TABLE places
    ADD COLUMN thumbnail_url VARCHAR(2048) NULL COMMENT '장소 대표 썸네일 URL' AFTER phone_number;
