START TRANSACTION;

INSERT INTO members (
    nickname,
    profile_image_url,
    status,
    created_at,
    updated_at
)
SELECT
    seed.nickname,
    NULL,
    'ACTIVE',
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
FROM (
    SELECT '테스트1' AS nickname
    UNION ALL SELECT '테스트2'
    UNION ALL SELECT '테스트3'
    UNION ALL SELECT '테스트4'
    UNION ALL SELECT '테스트5'
    UNION ALL SELECT '테스트6'
    UNION ALL SELECT '테스트7'
) AS seed
LEFT JOIN members
    ON members.nickname = seed.nickname
WHERE members.id IS NULL;

COMMIT;
