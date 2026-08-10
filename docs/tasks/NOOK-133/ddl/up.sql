ALTER TABLE places
    ADD COLUMN thumbnail_parsing_status VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING'
        COMMENT '장소 썸네일 파싱 상태'
        AFTER thumbnail_url;

UPDATE places
SET thumbnail_parsing_status = 'COMPLETED';
