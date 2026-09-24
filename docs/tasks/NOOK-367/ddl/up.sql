ALTER TABLE places
    ADD COLUMN provider_category VARCHAR(255) NULL COMMENT '외부 장소 provider가 제공한 원본 카테고리 경로'
        AFTER category;

UPDATE places
SET provider_category = category
WHERE provider_category IS NULL
  AND category IS NOT NULL
  AND provider <> 'MANUAL';
