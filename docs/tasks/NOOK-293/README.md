# NOOK-293 ERROR 로그 Slack 알림 요청 컨텍스트 추가

## 목적

dev 및 live ERROR 로그 Slack 알림에서 장애 요청의 핵심 컨텍스트를 즉시 확인하고,
request ID가 적용된 Grafana 로그 대시보드로 이동할 수 있게 한다.

## 범위

- Slack 알림에 `Service Name`, `Request ID`, `User ID`, `URL Path`를 굵은 제목과 함께 표시한다.
- 값이 없는 비요청성 오류는 `-`로 표시한다.
- request ID가 있으면 환경별 Grafana 로그 대시보드를 최근 15분, ERROR 레벨, 해당 request ID로 연다.
- dev 및 live error-log-forwarder에 동일하게 적용한다.
- 구조화 로그 파싱, Slack 메시지, Grafana URL 생성 단위 테스트를 추가한다.

## 제외 범위

- 애플리케이션 로그 포맷 변경
- Grafana 또는 Loki 구성 변경
- dev 및 live 배포
- Slack webhook이나 Grafana 실제 도메인 커밋

## 성공 기준

- dev/live ERROR 로그 알림에 네 가지 컨텍스트가 같은 형식으로 표시된다.
- 각 제목은 값 위에 굵게 표시되며 두 열로 배치된다.
- request ID가 있는 알림의 버튼이 환경별 Grafana 로그 대시보드를 해당 request ID 필터와 함께 연다.
- 일부 컨텍스트가 없는 ERROR 로그도 알림 생성에 실패하지 않는다.

## 환경 변수

```dotenv
ERROR_LOG_GRAFANA_BASE_URL=https://grafana.example.com
```

실제 Grafana 도메인은 각 환경 VM의 exporter `.env`에만 저장한다.

## 검증

```shell
python3 -m unittest discover -s ops/dev-exporters/error-log-forwarder -p 'test_*.py' -v
python3 -m py_compile \
  ops/dev-exporters/error-log-forwarder/forward_error_logs.py \
  ops/live-exporters/error-log-forwarder/forward_error_logs.py
docker compose --env-file ops/dev-exporters/.env.example -f ops/dev-exporters/docker-compose.yml config
docker compose --env-file ops/live-exporters/.env.example -f ops/live-exporters/docker-compose.yml config
./gradlew check
```
