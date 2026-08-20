START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS nook_224_place_rollbacks;
CREATE TEMPORARY TABLE nook_224_place_rollbacks (
    canonical_place_id BIGINT NOT NULL,
    duplicate_place_id BIGINT NOT NULL,
    duplicate_provider VARCHAR(50) NOT NULL,
    duplicate_external_place_id VARCHAR(255) NOT NULL,
    duplicate_source_external_post_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (duplicate_place_id)
) ENGINE = MEMORY;

INSERT INTO nook_224_place_rollbacks (
    canonical_place_id,
    duplicate_place_id,
    duplicate_provider,
    duplicate_external_place_id,
    duplicate_source_external_post_id
)
SELECT
    canonical.id,
    duplicate.id,
    'NAVER',
    '933ec67e8b4be972a8373632113c5c8dce13debda1603834a6da65435206f3b3',
    'DSRBsTfEqsr'
FROM places canonical
JOIN places duplicate
  ON duplicate.provider = 'NAVER'
 AND duplicate.external_place_id = '933ec67e8b4be972a8373632113c5c8dce13debda1603834a6da65435206f3b3'
WHERE canonical.provider = 'KAKAO'
  AND canonical.external_place_id = '1641347883'
UNION ALL
SELECT
    canonical.id,
    duplicate.id,
    'NAVER',
    '3e5d4b557a6b8aaef26e2337a16c1593102937a8c87011d5a4d5e4eb0427d677',
    'Dah2s5nsGDs'
FROM places canonical
JOIN places duplicate
  ON duplicate.provider = 'NAVER'
 AND duplicate.external_place_id = '3e5d4b557a6b8aaef26e2337a16c1593102937a8c87011d5a4d5e4eb0427d677'
WHERE canonical.provider = 'KAKAO'
  AND canonical.external_place_id = '2089608233';

UPDATE place_provider_references reference
JOIN nook_224_place_rollbacks rollback_target
  ON rollback_target.duplicate_provider = reference.provider
 AND rollback_target.duplicate_external_place_id = reference.external_place_id
SET
    reference.place_id = rollback_target.duplicate_place_id,
    reference.updated_at = CURRENT_TIMESTAMP(6);

UPDATE post_places relation
JOIN posts post
  ON post.id = relation.post_id
 AND post.source_type = 'INSTAGRAM'
JOIN nook_224_place_rollbacks rollback_target
  ON rollback_target.duplicate_source_external_post_id = post.external_post_id
 AND rollback_target.canonical_place_id = relation.place_id
SET
    relation.place_id = rollback_target.duplicate_place_id,
    relation.updated_at = CURRENT_TIMESTAMP(6);

UPDATE user_saved_post_places relation
JOIN user_saved_posts saved_post
  ON saved_post.id = relation.user_saved_post_id
JOIN posts post
  ON post.id = saved_post.post_id
 AND post.source_type = 'INSTAGRAM'
JOIN nook_224_place_rollbacks rollback_target
  ON rollback_target.duplicate_source_external_post_id = post.external_post_id
 AND rollback_target.canonical_place_id = relation.place_id
SET
    relation.place_id = rollback_target.duplicate_place_id,
    relation.updated_at = CURRENT_TIMESTAMP(6);

UPDATE post_place_tags tag
JOIN posts post
  ON post.id = tag.post_id
 AND post.source_type = 'INSTAGRAM'
JOIN nook_224_place_rollbacks rollback_target
  ON rollback_target.duplicate_source_external_post_id = post.external_post_id
 AND rollback_target.canonical_place_id = tag.place_id
SET
    tag.place_id = rollback_target.duplicate_place_id,
    tag.updated_at = CURRENT_TIMESTAMP(6);

INSERT IGNORE INTO user_place_bookmarks (
    user_id,
    place_id,
    memo,
    created_at,
    updated_at
)
SELECT DISTINCT
    saved_post.user_id,
    rollback_target.duplicate_place_id,
    canonical_bookmark.memo,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM nook_224_place_rollbacks rollback_target
JOIN posts post
  ON post.source_type = 'INSTAGRAM'
 AND post.external_post_id = rollback_target.duplicate_source_external_post_id
JOIN user_saved_posts saved_post
  ON saved_post.post_id = post.id
LEFT JOIN user_place_bookmarks canonical_bookmark
  ON canonical_bookmark.user_id = saved_post.user_id
 AND canonical_bookmark.place_id = rollback_target.canonical_place_id;

DROP TEMPORARY TABLE nook_224_place_rollbacks;

COMMIT;
