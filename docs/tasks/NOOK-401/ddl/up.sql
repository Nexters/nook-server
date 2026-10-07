ALTER TABLE user_place_bookmarks
    ADD COLUMN last_saved_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '이 장소가 마지막으로 저장 게시물에 연결된 시각';

UPDATE user_place_bookmarks
SET last_saved_at = created_at,
    updated_at = updated_at;

SET @nook_401_drop_index = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_place_bookmarks'
             AND INDEX_NAME = 'idx_user_id_created_at_id'),
    'ALTER TABLE user_place_bookmarks DROP INDEX idx_user_id_created_at_id',
    'SELECT 1');
PREPARE nook_401_statement FROM @nook_401_drop_index;
EXECUTE nook_401_statement;
DEALLOCATE PREPARE nook_401_statement;

ALTER TABLE user_place_bookmarks
    ADD INDEX idx_user_id_last_saved_at_id (user_id, last_saved_at DESC, id DESC);

ALTER TABLE user_place_bookmarks
    COMMENT = '사용자별 지도 장소 북마크';
