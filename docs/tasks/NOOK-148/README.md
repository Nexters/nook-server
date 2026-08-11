# NOOK-148 Grafana 로그 상세에서 전체 구조화 필드 복원

## 목적

`Server & API Logs`의 간결한 목록 표시는 유지하면서 로그 상세에서 Loki가 파싱한 전체 구조화 필드를
확인할 수 있게 합니다.

## 범위

- 패널 LogQL에서 파싱 필드를 제한하던 `keep service_name, level, log_type` 구문을 제거합니다.
- 목록의 `[service] [level] message` 표시 형식을 유지합니다.
- `Log Type`의 `All`, `API`, `Server` 분류를 유지합니다.
- 기존 level, request id, user id, route/path, status, keyword 필터를 유지합니다.
- API 로그 상세에서 요청 관련 구조화 필드를 확인합니다.
- 서버 로그 상세에서 logger, thread 등 구조화 필드를 확인합니다.

## 제외 범위

- 애플리케이션 로그 포맷 변경
- Loki와 Promtail 수집 구조 변경
- 구조화 필드 추가 또는 삭제
- 대시보드 레이아웃 변경
- Alert 규칙 변경

## 성공 기준

- 로그 목록은 기존과 동일하게 간결한 메시지로 표시됩니다.
- API 로그 상세에 원본에 존재하는 request id, user id, method, route, status, duration, transaction 등의
  파싱 필드가 표시됩니다.
- 서버 로그 상세에 원본에 존재하는 logger name, thread name 등의 파싱 필드가 표시됩니다.
- `Log Type`과 기존 검색 필터가 계속 동작합니다.
- dev Grafana에서 API 로그와 서버 로그의 상세 필드를 시각 검증합니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
jq empty ops/monitoring/grafana/dashboards/nook-dev-logs.json
./gradlew check
```

Loki API에서 패널 LogQL을 직접 실행해 목록 메시지가 유지되고 전체 파싱 필드가 결과에 남는지 확인합니다.

배포 후 Grafana dashboard API로 `keep` 제거를 확인하고, 실제 화면에서 API 로그와 서버 로그의 상세를 각각
열어 파싱 필드를 확인합니다.
