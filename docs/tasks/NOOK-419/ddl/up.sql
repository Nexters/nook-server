-- MySQL 8.4 LTS. 신규 테이블만 생성하며 반복 실행은 가능하다.
-- 기존 테이블이 있다면 구조가 일치하는지는 별도로 확인해야 한다. 앱 배포 전에 적용한다.
CREATE TABLE IF NOT EXISTS user_place_reminder_preferences (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '장소 연결 리마인드 설정 식별자',
    user_id BIGINT NOT NULL COMMENT '설정 소유 사용자 식별자',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '장소 연결 리마인드 사용 여부',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '설정 생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '설정 최종 변경 시각',
    PRIMARY KEY (id),
    UNIQUE KEY idx_u_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='사용자별 장소 미연결 게시물 안내 설정';
