ALTER TABLE places
    ADD COLUMN opening_hours JSON NULL COMMENT 'Google Places 기준 장소 정규 영업시간' AFTER thumbnail_url,
    ADD COLUMN photo_urls JSON NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '서비스 저장소에 보관된 장소 사진 URL 목록' AFTER opening_hours;
