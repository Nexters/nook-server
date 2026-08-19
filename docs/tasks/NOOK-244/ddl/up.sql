ALTER TABLE posts
    ADD COLUMN content_manually_overridden BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '운영자 수동 수정으로 자동 콘텐츠 갱신을 보호하는지 여부'
        AFTER source_location_tag;

