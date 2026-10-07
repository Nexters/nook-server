-- MySQL 8.4. 신규 테이블만 생성하며 기존 분석 이벤트는 변경하지 않는다.
-- 반복 실행 가능. CREATE TABLE의 짧은 metadata lock이 발생할 수 있다.
CREATE TABLE IF NOT EXISTS onboarding_events (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '이벤트 행 식별자',
    member_id BIGINT NOT NULL COMMENT '인증된 회원 식별자',
    event_id VARCHAR(36) NOT NULL COMMENT '클라이언트 재전송 중복 방지 UUID',
    event_name VARCHAR(30) NOT NULL COMMENT '온보딩 이벤트 종류',
    occurred_at TIMESTAMP(6) NOT NULL COMMENT '클라이언트 실제 발생 시각 UTC',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '서버 최초 수신 시각 UTC',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '행 갱신 시각 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_member_id_event_id (member_id, event_id),
    KEY idx_event_name_member_id_occurred_at (event_name, member_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='온보딩 CTA 및 Share Extension 클라이언트 이벤트';
