SET NAMES utf8mb4;

ALTER TABLE place_parsing_jobs
    ADD COLUMN source_profile_hints TEXT NULL COMMENT '원본 게시물 태그 프로필의 장소명 교정 힌트 JSON'
        AFTER image_transcripts;
