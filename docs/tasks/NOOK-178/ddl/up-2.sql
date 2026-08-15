-- NOOK-178 후속: 장소 메모의 주체를 (저장 게시물, 장소) 에서 (사용자, 장소) 로 옮긴다.
-- up.sql 이 이미 적용된 환경에서만 실행한다. 순서: up.sql -> up-2.sql

-- 1. 북마크에 메모 컬럼을 추가한다.
ALTER TABLE user_place_bookmarks
    ADD COLUMN memo TEXT NULL COMMENT '사용자가 이 장소에 남긴 메모' AFTER place_id;

-- 2. 기존 장소 메모를 북마크로 옮긴다.
--    같은 (사용자, 장소) 에 게시물별 메모가 여러 건이면 가장 최근 것만 남긴다.
UPDATE user_place_bookmarks b
    JOIN (
        SELECT m.user_id,
               m.place_id,
               SUBSTRING_INDEX(
                   GROUP_CONCAT(m.memo ORDER BY m.updated_at DESC, m.id DESC SEPARATOR 0x1F),
                   0x1F,
                   1
               ) AS memo
        FROM user_saved_post_place_memos m
        GROUP BY m.user_id, m.place_id
    ) latest ON latest.user_id = b.user_id AND latest.place_id = b.place_id
SET b.memo = latest.memo
WHERE b.memo IS NULL;

-- 3. 옮겨지지 않은 메모가 없는지 확인한다. 결과가 0행이어야 한다.
SELECT m.user_id, m.place_id, m.memo
FROM user_saved_post_place_memos m
         LEFT JOIN user_place_bookmarks b
                   ON b.user_id = m.user_id AND b.place_id = m.place_id AND b.memo IS NOT NULL
WHERE b.id IS NULL;

-- 4. 위 확인이 0행일 때만 실행한다.
DROP TABLE user_saved_post_place_memos;
