# NOOK-371 HTTP 요청 지연시간 percentile 계측

## 목적

API 응답 지연을 평균값이 아니라 p95 기준으로 관측할 수 있게 한다. 기존 Grafana 대시보드와
알림은 `http_server_requests_seconds_bucket`을 조회하지만 애플리케이션에서 histogram을
발행하지 않아 값이 비어 있었다.

## 범위

- `http.server.requests` percentile histogram을 활성화한다.
- 50ms부터 5초까지 서비스 수준 목표 bucket을 명시한다.
- 애플리케이션 테스트에서 Prometheus histogram 노출을 검증한다.
- 기존 p95 대시보드, Hikari pool pending 알림, Worker queue age 알림 구성을 확인한다.

## 제외 범위

- 비즈니스 로직과 API 계약 변경
- 운영 알림 임계치 변경
- Worker HTTP endpoint histogram 추가

## 성공 기준과 검증

- `/actuator/prometheus`가 `http_server_requests_seconds_bucket`을 노출한다.
- 기존 Grafana p95 쿼리가 데이터를 반환할 수 있다.
- `./gradlew check`가 성공한다.
- develop 배포 후 dev Prometheus에서 bucket과 p95 쿼리를 확인한다.
