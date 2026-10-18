-- API/worker를 이전 버전으로 되돌린 후에만 실행. 공유 링크와 고정 상태가 삭제된다.
-- 사용자 저장 게시물/장소 연결 자체는 삭제하지 않는다. 물리 FK 없음.
DROP TABLE IF EXISTS post_share_links;
ALTER TABLE user_saved_posts DROP COLUMN place_links_frozen, DROP COLUMN share_generation;
