CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL COMMENT '분산 잠금 식별자',
    lock_until TIMESTAMP(3) NOT NULL COMMENT '잠금 만료 시각(UTC)',
    locked_at TIMESTAMP(3) NOT NULL COMMENT '잠금 획득 시각(UTC)',
    locked_by VARCHAR(255) NOT NULL COMMENT '잠금을 획득한 애플리케이션 인스턴스',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '레코드 최초 생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '레코드 최종 갱신 시각',
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '애플리케이션 스케줄러 분산 잠금';
