-- 새 코드 중단 및 이전 버전 복귀 후 적용. 재시도 회차별 횟수 정보가 유실됨.
ALTER TABLE parsing_follow_up_jobs DROP COLUMN retry_attempt_count;
