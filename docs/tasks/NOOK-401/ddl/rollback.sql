ALTER TABLE user_place_bookmarks
    DROP INDEX idx_user_id_last_saved_at_id,
    ADD INDEX idx_user_id_created_at_id (user_id, created_at DESC, id DESC),
    DROP COLUMN last_saved_at;

ALTER TABLE user_place_bookmarks
    COMMENT = '사용자별 지도 장소 북마크';
