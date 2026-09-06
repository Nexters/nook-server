CREATE TABLE analytics_events (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '사용자 행동 이벤트 식별자',
    event_id CHAR(36) NOT NULL COMMENT '이벤트 UUID',
    deduplication_key VARCHAR(191) NOT NULL COMMENT '사용자·대상·일자 기반 중복 제거 키',
    event_name VARCHAR(30) NOT NULL COMMENT '사용자 행동 이벤트 이름',
    member_id BIGINT NOT NULL COMMENT '행동을 수행한 회원 식별자',
    target_type VARCHAR(20) NULL COMMENT '행동 대상 유형: POST, PLACE, GROUP',
    target_id BIGINT NULL COMMENT '행동 대상 식별자',
    occurred_at TIMESTAMP(6) NOT NULL COMMENT '행동 발생 일시',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '이벤트 저장 일시',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '이벤트 수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_event_id (event_id),
    UNIQUE KEY idx_u_deduplication_key (deduplication_key),
    KEY idx_event_name_occurred_at (event_name, occurred_at),
    KEY idx_member_id_occurred_at_event_name (member_id, occurred_at, event_name)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자 행동 분석 이벤트 원장';
