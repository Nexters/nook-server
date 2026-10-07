-- MySQL 8.4. 신규 테이블 생성만 수행한다. IF NOT EXISTS로 반복 실행 가능.
-- 기존 장소 테이블의 변경/데이터 backfill 및 물리 FK는 없다.
CREATE TABLE IF NOT EXISTS place_reprocessing_jobs (
    place_id BIGINT NOT NULL COMMENT '재처리 대상 장소 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '전체 처리 상태',
    attempt INT NOT NULL DEFAULT 0 COMMENT '누적 실행 회차 및 오래된 결과 저장 방지 토큰',
    photos VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '사진 재수집 결과',
    tags VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '태그 재추출 결과',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '최초 재처리 요청 생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '작업 상태 최종 변경 시각',
    PRIMARY KEY (place_id),
    INDEX idx_status_updated_at (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='관리자가 요청한 장소별 재처리 최신 작업';
