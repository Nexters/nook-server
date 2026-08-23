SET NAMES utf8mb4;

CREATE TABLE post_processing_traces
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '파싱 실행 이력 식별자',
    post_id     BIGINT       NOT NULL COMMENT '원본 게시글 식별자',
    flow        VARCHAR(30)  NOT NULL COMMENT '처리 흐름 구분',
    stage       VARCHAR(50)  NOT NULL COMMENT '처리 단계',
    action      VARCHAR(80)  NOT NULL COMMENT '실행 동작',
    outcome     VARCHAR(20)  NOT NULL COMMENT '실행 결과',
    attempt     INT          NULL COMMENT '처리 시도 횟수',
    duration_ms BIGINT       NULL COMMENT '처리 소요 시간(밀리초)',
    details     TEXT         NULL COMMENT '운영자 확인용 구조화 상세 JSON',
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    INDEX idx_post_id_created_at (post_id, created_at)
) COMMENT '게시글 콘텐츠 및 장소 파싱 실행 이력';
