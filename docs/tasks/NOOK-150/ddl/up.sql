ALTER TABLE place_parsing_jobs
    ADD COLUMN image_transcripts MEDIUMTEXT NULL COMMENT '이미지 순번별 OpenAI 원문 전사 결과 JSON'
        AFTER text_place_clues;
