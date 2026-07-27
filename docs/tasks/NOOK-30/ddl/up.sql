UPDATE user_saved_posts AS saved_post
INNER JOIN posts AS post
    ON post.id = saved_post.post_id
SET saved_post.memo = post.memo
WHERE saved_post.memo IS NULL
  AND post.memo IS NOT NULL;

ALTER TABLE posts
    DROP COLUMN memo;
