-- 워커와 API를 이전 버전으로 되돌린 후 실행한다.
-- 대기 작업과 최신 결과를 삭제한다. 장소 사진·태그과 감사 로그는 되돌리지 않는다.
DROP TABLE IF EXISTS place_reprocessing_jobs;
