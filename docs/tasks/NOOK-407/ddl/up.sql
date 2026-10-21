-- NOOK-407: 아카이브 소개글. MySQL 8.4 LTS, 비반복 실행.
-- 적용 전 information_schema.columns에서 기존 칼럼 유무와 테이블을 확인한다.
-- utf8mb4로 조합 이모지를 보존한다. 150자는 서버에서 grapheme cluster 기준으로 검증한다.
-- ALGORITHM=INSTANT는 테이블 복사를 피하지만 짧은 metadata lock이 필요하다.
ALTER TABLE user_groups
    ADD COLUMN description TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
        NULL COMMENT '아카이브 소개글 (사용자에게 보이는 글자 기준 최대 150자)',
    ALGORITHM=INSTANT;
