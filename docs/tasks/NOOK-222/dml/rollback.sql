START TRANSACTION;

SET @canonical_place_id := (
    SELECT id
    FROM places
    WHERE provider = 'KAKAO'
      AND external_place_id = '170705999'
    LIMIT 1
);
SET @duplicate_place_id := (
    SELECT id
    FROM places
    WHERE provider = 'NAVER'
      AND external_place_id = '0048087adc7f0ab0a6fbcf2f65751264bfca5212a9fdd7574f8161be6d9e9a07'
    LIMIT 1
);
SET @naver_post_id := (
    SELECT id
    FROM posts
    WHERE source_type = 'INSTAGRAM'
      AND external_post_id = 'DYhhhgNktcp'
    LIMIT 1
);

UPDATE place_provider_references
SET
    place_id = @duplicate_place_id,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE provider = 'NAVER'
  AND external_place_id = '0048087adc7f0ab0a6fbcf2f65751264bfca5212a9fdd7574f8161be6d9e9a07'
  AND @duplicate_place_id IS NOT NULL;

UPDATE post_places
SET
    place_id = @duplicate_place_id,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE post_id = @naver_post_id
  AND place_id = @canonical_place_id
  AND @duplicate_place_id IS NOT NULL;

UPDATE user_saved_post_places saved_place
JOIN user_saved_posts saved_post ON saved_post.id = saved_place.user_saved_post_id
SET
    saved_place.place_id = @duplicate_place_id,
    saved_place.updated_at = CURRENT_TIMESTAMP(6)
WHERE saved_post.post_id = @naver_post_id
  AND saved_place.place_id = @canonical_place_id
  AND @duplicate_place_id IS NOT NULL;

UPDATE post_place_tags
SET
    place_id = @duplicate_place_id,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE post_id = @naver_post_id
  AND place_id = @canonical_place_id
  AND @duplicate_place_id IS NOT NULL;

INSERT IGNORE INTO user_place_bookmarks (
    user_id,
    place_id,
    memo,
    created_at,
    updated_at
)
SELECT
    saved_post.user_id,
    @duplicate_place_id,
    canonical_bookmark.memo,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM user_saved_posts saved_post
LEFT JOIN user_place_bookmarks canonical_bookmark
  ON canonical_bookmark.user_id = saved_post.user_id
 AND canonical_bookmark.place_id = @canonical_place_id
WHERE saved_post.post_id = @naver_post_id
  AND @duplicate_place_id IS NOT NULL;

COMMIT;
