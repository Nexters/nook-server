START TRANSACTION;

UPDATE members
SET
    nickname = CASE id
        WHEN 1 THEN '김윤영'
        WHEN 2 THEN '배서영'
        WHEN 3 THEN '박찬형'
        WHEN 4 THEN '권기준'
        WHEN 5 THEN '백도현'
        WHEN 6 THEN '문지우'
        WHEN 7 THEN '김태임'
    END,
    updated_at = UTC_TIMESTAMP(6)
WHERE id IN (1, 2, 3, 4, 5, 6, 7);

COMMIT;
