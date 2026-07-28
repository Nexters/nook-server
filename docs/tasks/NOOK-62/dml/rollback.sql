START TRANSACTION;

DELETE refresh_tokens
FROM refresh_tokens
INNER JOIN members
    ON members.id = refresh_tokens.member_id
WHERE members.nickname IN (
    '테스트1',
    '테스트2',
    '테스트3',
    '테스트4',
    '테스트5',
    '테스트6',
    '테스트7',
    '김윤영',
    '배서영',
    '박찬형',
    '권기준',
    '백도현',
    '문지우',
    '김태임'
);

DELETE social_accounts
FROM social_accounts
INNER JOIN members
    ON members.id = social_accounts.member_id
WHERE members.nickname IN (
    '테스트1',
    '테스트2',
    '테스트3',
    '테스트4',
    '테스트5',
    '테스트6',
    '테스트7',
    '김윤영',
    '배서영',
    '박찬형',
    '권기준',
    '백도현',
    '문지우',
    '김태임'
);

DELETE FROM members
WHERE nickname IN (
    '테스트1',
    '테스트2',
    '테스트3',
    '테스트4',
    '테스트5',
    '테스트6',
    '테스트7',
    '김윤영',
    '배서영',
    '박찬형',
    '권기준',
    '백도현',
    '문지우',
    '김태임'
);

COMMIT;
