CREATE TEMPORARY TABLE nook_71_post_merge (
    old_post_id BIGINT NOT NULL COMMENT '병합 대상 게시물 식별자',
    keep_post_id BIGINT NOT NULL COMMENT '유지할 대표 게시물 식별자',
    PRIMARY KEY (old_post_id),
    KEY idx_keep_post_id (keep_post_id)
) ENGINE = InnoDB
  COMMENT = 'NOOK-71 게시물 병합 매핑';

INSERT INTO nook_71_post_merge (old_post_id, keep_post_id)
SELECT
    post.id,
    (
        SELECT candidate.id
        FROM posts AS candidate
        LEFT JOIN place_parsing_jobs AS candidate_job
            ON candidate_job.post_id = candidate.id
        WHERE candidate.source_type = post.source_type
          AND candidate.external_post_id = post.external_post_id
        ORDER BY
            CASE candidate_job.status WHEN 'COMPLETED' THEN 0 ELSE 1 END,
            (SELECT COUNT(*) FROM post_places AS candidate_place WHERE candidate_place.post_id = candidate.id) DESC,
            candidate.id
        LIMIT 1
    )
FROM posts AS post;

CREATE TEMPORARY TABLE nook_71_saved_post_merge (
    old_saved_post_id BIGINT NOT NULL COMMENT '병합 대상 사용자 저장 게시물 식별자',
    keep_saved_post_id BIGINT NOT NULL COMMENT '유지할 사용자 저장 게시물 식별자',
    keep_post_id BIGINT NOT NULL COMMENT '유지할 대표 게시물 식별자',
    PRIMARY KEY (old_saved_post_id),
    KEY idx_keep_saved_post_id (keep_saved_post_id),
    KEY idx_keep_post_id (keep_post_id)
) ENGINE = InnoDB
  COMMENT = 'NOOK-71 사용자 저장 게시물 병합 매핑';

INSERT INTO nook_71_saved_post_merge (old_saved_post_id, keep_saved_post_id, keep_post_id)
SELECT
    saved_post.id,
    (
        SELECT candidate_saved_post.id
        FROM user_saved_posts AS candidate_saved_post
        INNER JOIN nook_71_post_merge AS candidate_merge
            ON candidate_merge.old_post_id = candidate_saved_post.post_id
        WHERE candidate_saved_post.user_id = saved_post.user_id
          AND candidate_merge.keep_post_id = post_merge.keep_post_id
        ORDER BY candidate_saved_post.id
        LIMIT 1
    ),
    post_merge.keep_post_id
FROM user_saved_posts AS saved_post
INNER JOIN nook_71_post_merge AS post_merge
    ON post_merge.old_post_id = saved_post.post_id;

CREATE TEMPORARY TABLE nook_71_saved_post_memo (
    keep_saved_post_id BIGINT NOT NULL COMMENT '유지할 사용자 저장 게시물 식별자',
    memo TEXT NULL COMMENT '보존할 최초 메모',
    PRIMARY KEY (keep_saved_post_id)
) ENGINE = InnoDB
  COMMENT = 'NOOK-71 사용자 저장 게시물 메모 보존';

INSERT INTO nook_71_saved_post_memo (keep_saved_post_id, memo)
SELECT keep_saved_post_id, memo
FROM (
    SELECT
        saved_merge.keep_saved_post_id,
        saved_post.memo,
        ROW_NUMBER() OVER (
            PARTITION BY saved_merge.keep_saved_post_id
            ORDER BY (saved_post.memo IS NULL OR saved_post.memo = ''), saved_post.id
        ) AS memo_order
    FROM nook_71_saved_post_merge AS saved_merge
    INNER JOIN user_saved_posts AS saved_post
        ON saved_post.id = saved_merge.old_saved_post_id
) AS ranked_memo
WHERE memo_order = 1;

INSERT IGNORE INTO group_posts (group_id, user_saved_post_id, created_at, updated_at)
SELECT
    group_post.group_id,
    saved_merge.keep_saved_post_id,
    MIN(group_post.created_at),
    MAX(group_post.updated_at)
FROM group_posts AS group_post
INNER JOIN nook_71_saved_post_merge AS saved_merge
    ON saved_merge.old_saved_post_id = group_post.user_saved_post_id
GROUP BY group_post.group_id, saved_merge.keep_saved_post_id;

DELETE group_post
FROM group_posts AS group_post
INNER JOIN nook_71_saved_post_merge AS saved_merge
    ON saved_merge.old_saved_post_id = group_post.user_saved_post_id
WHERE saved_merge.old_saved_post_id <> saved_merge.keep_saved_post_id;

UPDATE user_saved_posts AS saved_post
INNER JOIN nook_71_saved_post_memo AS saved_memo
    ON saved_memo.keep_saved_post_id = saved_post.id
SET saved_post.memo = saved_memo.memo;

DELETE saved_post
FROM user_saved_posts AS saved_post
INNER JOIN nook_71_saved_post_merge AS saved_merge
    ON saved_merge.old_saved_post_id = saved_post.id
WHERE saved_merge.old_saved_post_id <> saved_merge.keep_saved_post_id;

UPDATE user_saved_posts AS saved_post
INNER JOIN nook_71_saved_post_merge AS saved_merge
    ON saved_merge.keep_saved_post_id = saved_post.id
SET saved_post.post_id = saved_merge.keep_post_id;

INSERT IGNORE INTO post_places (post_id, place_id, display_order, created_at, updated_at)
SELECT
    post_merge.keep_post_id,
    post_place.place_id,
    COALESCE(kept_place.max_display_order, -1) +
        ROW_NUMBER() OVER (
            PARTITION BY post_merge.keep_post_id
            ORDER BY post_merge.old_post_id, post_place.display_order
        ),
    post_place.created_at,
    post_place.updated_at
FROM post_places AS post_place
INNER JOIN nook_71_post_merge AS post_merge
    ON post_merge.old_post_id = post_place.post_id
LEFT JOIN (
    SELECT post_id, MAX(display_order) AS max_display_order
    FROM post_places
    GROUP BY post_id
) AS kept_place
    ON kept_place.post_id = post_merge.keep_post_id
WHERE post_merge.old_post_id <> post_merge.keep_post_id;

UPDATE posts
SET canonical_url = CONCAT(TRIM(TRAILING '/' FROM SUBSTRING_INDEX(canonical_url, '?', 1)), '/')
WHERE source_type = 'INSTAGRAM';

DELETE post
FROM posts AS post
INNER JOIN nook_71_post_merge AS post_merge
    ON post_merge.old_post_id = post.id
WHERE post_merge.old_post_id <> post_merge.keep_post_id;

ALTER TABLE posts
    ADD UNIQUE KEY idx_u_source_type_external_post_id (source_type, external_post_id);

ALTER TABLE user_saved_posts
    ADD UNIQUE KEY idx_u_user_id_post_id (user_id, post_id);

DROP TEMPORARY TABLE nook_71_saved_post_memo;
DROP TEMPORARY TABLE nook_71_saved_post_merge;
DROP TEMPORARY TABLE nook_71_post_merge;
