-- 애플리케이션 롤백 후 실행. 모든 온보딩 이벤트가 영구 삭제되므로 먼저 백업한다.
-- 반복 실행 가능. 기존 analytics_events에는 영향을 주지 않는다.
DROP TABLE IF EXISTS onboarding_events;
