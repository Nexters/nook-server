ALTER TABLE place_parsing_jobs
    ADD COLUMN text_place_clues TEXT NULL COMMENT '게시물 콘텐츠 통합 추론에서 추출한 텍스트 장소 단서 JSON'
        AFTER post_id;
