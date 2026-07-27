ALTER TABLE posts
    ADD COLUMN memo TEXT NULL COMMENT '사용자가 작성한 게시물 메모' AFTER title;

UPDATE posts AS post
INNER JOIN (
    SELECT
        post_id,
        MAX(memo) AS memo
    FROM user_saved_posts
    WHERE memo IS NOT NULL
    GROUP BY post_id
) AS saved_post
    ON saved_post.post_id = post.id
SET post.memo = saved_post.memo;
