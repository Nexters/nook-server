START TRANSACTION;

INSERT IGNORE INTO place_provider_references (
    place_id,
    provider,
    external_place_id,
    created_at,
    updated_at
)
SELECT
    id,
    provider,
    external_place_id,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM places;

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

UPDATE place_provider_references
SET
    place_id = @canonical_place_id,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE provider = 'NAVER'
  AND external_place_id = '0048087adc7f0ab0a6fbcf2f65751264bfca5212a9fdd7574f8161be6d9e9a07'
  AND @canonical_place_id IS NOT NULL
  AND @duplicate_place_id IS NOT NULL;

DELETE duplicate_relation
FROM post_places duplicate_relation
JOIN post_places canonical_relation
  ON canonical_relation.post_id = duplicate_relation.post_id
 AND canonical_relation.place_id = @canonical_place_id
WHERE duplicate_relation.place_id = @duplicate_place_id;

UPDATE post_places
SET
    place_id = @canonical_place_id,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE place_id = @duplicate_place_id
  AND @canonical_place_id IS NOT NULL;

DELETE duplicate_relation
FROM user_saved_post_places duplicate_relation
JOIN user_saved_post_places canonical_relation
  ON canonical_relation.user_saved_post_id = duplicate_relation.user_saved_post_id
 AND canonical_relation.place_id = @canonical_place_id
WHERE duplicate_relation.place_id = @duplicate_place_id;

UPDATE user_saved_post_places
SET
    place_id = @canonical_place_id,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE place_id = @duplicate_place_id
  AND @canonical_place_id IS NOT NULL;

INSERT IGNORE INTO post_place_tags (
    post_id,
    place_id,
    tag,
    confidence,
    evidence_source,
    evidence_text,
    created_at,
    updated_at
)
SELECT
    post_id,
    @canonical_place_id,
    tag,
    confidence,
    evidence_source,
    evidence_text,
    created_at,
    CURRENT_TIMESTAMP(6)
FROM post_place_tags
WHERE place_id = @duplicate_place_id
  AND @canonical_place_id IS NOT NULL;

DELETE FROM post_place_tags
WHERE place_id = @duplicate_place_id
  AND @canonical_place_id IS NOT NULL;

UPDATE user_place_bookmarks canonical_bookmark
JOIN user_place_bookmarks duplicate_bookmark
  ON duplicate_bookmark.user_id = canonical_bookmark.user_id
 AND duplicate_bookmark.place_id = @duplicate_place_id
SET
    canonical_bookmark.memo = COALESCE(NULLIF(canonical_bookmark.memo, ''), duplicate_bookmark.memo),
    canonical_bookmark.updated_at = CURRENT_TIMESTAMP(6)
WHERE canonical_bookmark.place_id = @canonical_place_id;

DELETE duplicate_bookmark
FROM user_place_bookmarks duplicate_bookmark
JOIN user_place_bookmarks canonical_bookmark
  ON canonical_bookmark.user_id = duplicate_bookmark.user_id
 AND canonical_bookmark.place_id = @canonical_place_id
WHERE duplicate_bookmark.place_id = @duplicate_place_id;

UPDATE user_place_bookmarks
SET
    place_id = @canonical_place_id,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE place_id = @duplicate_place_id
  AND @canonical_place_id IS NOT NULL;

COMMIT;
