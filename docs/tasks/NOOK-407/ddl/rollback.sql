-- NOOK-407 rollback. 비반복 실행이며 저장된 소개글이 모두 유실된다.
-- 이 칼럼을 참조하지 않는 이전 앱/워커 버전으로 되돌린 뒤 실행한다.
ALTER TABLE user_groups
    DROP COLUMN description,
    ALGORITHM=INSTANT;
