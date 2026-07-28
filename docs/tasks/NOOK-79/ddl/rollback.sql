-- 같은 사용자의 동일 그룹명이 존재하면 유니크 인덱스 복원이 실패하므로 롤백 전에 중복 데이터를 정리해야 한다.
ALTER TABLE user_groups
    ADD UNIQUE INDEX idx_u_user_id_name (user_id, name);
