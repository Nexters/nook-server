START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS nook_224_place_merges;
CREATE TEMPORARY TABLE nook_224_place_merges (
    canonical_place_id BIGINT NOT NULL,
    duplicate_place_id BIGINT NOT NULL,
    PRIMARY KEY (duplicate_place_id)
) ENGINE = MEMORY;

INSERT INTO nook_224_place_merges (canonical_place_id, duplicate_place_id)
SELECT canonical.id, duplicate.id
FROM places canonical
JOIN places duplicate
  ON duplicate.provider = 'NAVER'
 AND duplicate.external_place_id = '933ec67e8b4be972a8373632113c5c8dce13debda1603834a6da65435206f3b3'
WHERE canonical.provider = 'KAKAO'
  AND canonical.external_place_id = '1641347883'
UNION ALL
SELECT canonical.id, duplicate.id
FROM places canonical
JOIN places duplicate
  ON duplicate.provider = 'NAVER'
 AND duplicate.external_place_id = '3e5d4b557a6b8aaef26e2337a16c1593102937a8c87011d5a4d5e4eb0427d677'
WHERE canonical.provider = 'KAKAO'
  AND canonical.external_place_id = '2089608233';

UPDATE place_provider_references reference
JOIN nook_224_place_merges merge_target
  ON merge_target.duplicate_place_id = reference.place_id
SET
    reference.place_id = merge_target.canonical_place_id,
    reference.updated_at = CURRENT_TIMESTAMP(6);

DELETE duplicate_relation
FROM post_places duplicate_relation
JOIN nook_224_place_merges merge_target
  ON merge_target.duplicate_place_id = duplicate_relation.place_id
JOIN post_places canonical_relation
  ON canonical_relation.post_id = duplicate_relation.post_id
 AND canonical_relation.place_id = merge_target.canonical_place_id;

UPDATE post_places relation
JOIN nook_224_place_merges merge_target
  ON merge_target.duplicate_place_id = relation.place_id
SET
    relation.place_id = merge_target.canonical_place_id,
    relation.updated_at = CURRENT_TIMESTAMP(6);

DELETE duplicate_relation
FROM user_saved_post_places duplicate_relation
JOIN nook_224_place_merges merge_target
  ON merge_target.duplicate_place_id = duplicate_relation.place_id
JOIN user_saved_post_places canonical_relation
  ON canonical_relation.user_saved_post_id = duplicate_relation.user_saved_post_id
 AND canonical_relation.place_id = merge_target.canonical_place_id;

UPDATE user_saved_post_places relation
JOIN nook_224_place_merges merge_target
  ON merge_target.duplicate_place_id = relation.place_id
SET
    relation.place_id = merge_target.canonical_place_id,
    relation.updated_at = CURRENT_TIMESTAMP(6);

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
    tag.post_id,
    merge_target.canonical_place_id,
    tag.tag,
    tag.confidence,
    tag.evidence_source,
    tag.evidence_text,
    tag.created_at,
    CURRENT_TIMESTAMP(6)
FROM post_place_tags tag
JOIN nook_224_place_merges merge_target
  ON merge_target.duplicate_place_id = tag.place_id;

DELETE tag
FROM post_place_tags tag
JOIN nook_224_place_merges merge_target
  ON merge_target.duplicate_place_id = tag.place_id;

UPDATE user_place_bookmarks canonical_bookmark
JOIN nook_224_place_merges merge_target
  ON merge_target.canonical_place_id = canonical_bookmark.place_id
JOIN user_place_bookmarks duplicate_bookmark
  ON duplicate_bookmark.user_id = canonical_bookmark.user_id
 AND duplicate_bookmark.place_id = merge_target.duplicate_place_id
SET
    canonical_bookmark.memo = CASE
        WHEN NULLIF(TRIM(canonical_bookmark.memo), '') IS NULL THEN duplicate_bookmark.memo
        WHEN NULLIF(TRIM(duplicate_bookmark.memo), '') IS NULL THEN canonical_bookmark.memo
        WHEN canonical_bookmark.memo = duplicate_bookmark.memo THEN canonical_bookmark.memo
        ELSE CONCAT(canonical_bookmark.memo, CHAR(10), duplicate_bookmark.memo)
    END,
    canonical_bookmark.updated_at = CURRENT_TIMESTAMP(6);

DELETE duplicate_bookmark
FROM user_place_bookmarks duplicate_bookmark
JOIN nook_224_place_merges merge_target
  ON merge_target.duplicate_place_id = duplicate_bookmark.place_id
JOIN user_place_bookmarks canonical_bookmark
  ON canonical_bookmark.user_id = duplicate_bookmark.user_id
 AND canonical_bookmark.place_id = merge_target.canonical_place_id;

UPDATE user_place_bookmarks bookmark
JOIN nook_224_place_merges merge_target
  ON merge_target.duplicate_place_id = bookmark.place_id
SET
    bookmark.place_id = merge_target.canonical_place_id,
    bookmark.updated_at = CURRENT_TIMESTAMP(6);

DROP TEMPORARY TABLE nook_224_place_merges;

COMMIT;
