-- MySQL 8.4. API/worker 배포 전에 적용. 실행은 별도 승인 필요.
-- 기존 저장 건은 초기 공유 세대 빈 문자열과 자동 장소 동기화 정책을 유지한다.
SET @nook369_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'user_saved_posts' AND column_name = 'share_generation'),
    'SELECT 1',
    'ALTER TABLE user_saved_posts ADD COLUMN share_generation VARCHAR(36) NOT NULL DEFAULT '''' COMMENT ''삭제 시 갱신하여 이전 게시물 공유 링크 영구 만료'''
);
PREPARE nook369_stmt FROM @nook369_sql;
EXECUTE nook369_stmt;
DEALLOCATE PREPARE nook369_stmt;

SET @nook369_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'user_saved_posts' AND column_name = 'place_links_frozen'),
    'SELECT 1',
    'ALTER TABLE user_saved_posts ADD COLUMN place_links_frozen BIT(1) NOT NULL DEFAULT b''0'' COMMENT ''공유 복사본의 장소 연결을 공통 파싱 결과와 동기화하지 않음'''
);
PREPARE nook369_stmt FROM @nook369_sql;
EXECUTE nook369_stmt;
DEALLOCATE PREPARE nook369_stmt;

CREATE TABLE IF NOT EXISTS post_share_links (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '게시물 공유 링크 ID',
    token_hash VARCHAR(64) NOT NULL COMMENT '공유 토큰 SHA-256; 원문은 저장하지 않음',
    source_saved_post_id BIGINT NOT NULL COMMENT '공유자 저장 게시물 ID',
    source_post_id BIGINT NOT NULL COMMENT '공통 게시물 ID',
    source_generation VARCHAR(36) NOT NULL COMMENT '공유 시점 원본의 삭제 세대',
    snapshot MEDIUMTEXT NOT NULL COMMENT '개인 정보 제외 공개 JSON',
    captured_at TIMESTAMP(6) NOT NULL COMMENT '공유 스냅샷 생성 시각',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_u_token_hash (token_hash),
    INDEX idx_source_saved_post_id (source_saved_post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='게시물 공유 콘텐츠와 장소 목록 스냅샷';
