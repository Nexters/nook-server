ALTER TABLE places
    ADD COLUMN google_place_id VARCHAR(255) NULL COMMENT 'Google Places 장소 식별자' AFTER phone_number;
