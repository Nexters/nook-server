ALTER TABLE post_content_parsing_jobs
    ADD COLUMN progress_stage VARCHAR(40) NULL COMMENT '현재 콘텐츠 파싱 진행 마일스톤' AFTER next_attempt_at,
    ADD COLUMN progress_stage_started_at TIMESTAMP(6) NULL COMMENT '현재 콘텐츠 파싱 마일스톤 시작 시각' AFTER progress_stage,
    ADD COLUMN progress_percent INT NOT NULL DEFAULT 5 COMMENT '외부에 확정 노출한 콘텐츠 파싱 최저 진행률' AFTER progress_stage_started_at,
    ALGORITHM=INSTANT;

ALTER TABLE place_parsing_jobs
    ADD COLUMN progress_stage VARCHAR(40) NULL COMMENT '현재 장소 파싱 진행 마일스톤' AFTER next_attempt_at,
    ADD COLUMN progress_stage_started_at TIMESTAMP(6) NULL COMMENT '현재 장소 파싱 마일스톤 시작 시각' AFTER progress_stage,
    ADD COLUMN progress_percent INT NOT NULL DEFAULT 50 COMMENT '외부에 확정 노출한 장소 파싱 최저 진행률' AFTER progress_stage_started_at,
    ALGORITHM=INSTANT;
