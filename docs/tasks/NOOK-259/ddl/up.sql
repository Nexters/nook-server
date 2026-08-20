ALTER TABLE place_parsing_jobs
    ADD COLUMN parsing_outcome VARCHAR(20) NULL COMMENT '장소 파싱 완전성 결과' AFTER image_transcripts,
    ADD COLUMN expected_place_count INT NULL COMMENT '본문과 이미지 근거로 추정한 기대 장소 수' AFTER parsing_outcome,
    ADD COLUMN extracted_place_count INT NULL COMMENT '검색 전 추출 장소 단서 수' AFTER expected_place_count,
    ADD COLUMN resolved_place_count INT NULL COMMENT '실제 장소로 해결한 수' AFTER extracted_place_count,
    ADD COLUMN unresolved_place_clues MEDIUMTEXT NULL COMMENT '해결하지 못한 장소 단서와 실패 이유 JSON' AFTER resolved_place_count;
