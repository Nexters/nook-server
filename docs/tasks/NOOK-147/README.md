# NOOK-147 API 요청 로그 패널을 간결한 로그 스트림 형태로 개선

## 목적

Grafana의 `API Request Summary` 패널을 가로로 긴 테이블에서 시간, 서비스, 요청/응답 요약 중심의 간결한 로그
스트림 형태로 변경합니다.

## 범위

- `API Request Summary` 패널을 Table에서 Grafana Logs 패널로 변경합니다.
- 각 로그에는 timestamp, `nook-dev-api` 서비스 라벨과 요청/응답 2줄 요약만 기본 노출합니다.
- 로그는 최신순으로 표시하고 메시지의 줄바꿈을 유지합니다.
- 기존 level, request id, user id, route/path, status, keyword 필터를 유지합니다.
- 전체 구조화 로그 확인을 위한 `Raw API Logs` 패널은 유지합니다.

표시 형태는 다음과 같습니다.

```text
03:11:44  [nook-dev-api]
          req: GET /favicon.ico
          res: 401 3ms
```

## 제외 범위

- 애플리케이션 로그 포맷 변경
- Loki와 Promtail 수집 구조 변경
- `Raw API Logs` 패널 제거
- Elasticsearch, OpenSearch 또는 Kibana 도입
- 로그 검색 변수 신규 추가
- API Alert 규칙 변경

## 성공 기준

- `API Request Summary`가 테이블 컬럼 없이 로그 스트림 형태로 표시됩니다.
- 각 로그에 시간, `nook-dev-api` 서비스 라벨, 요청/응답 2줄 요약이 표시됩니다.
- 긴 request id나 transaction 컬럼으로 인한 가로 스크롤이 발생하지 않습니다.
- 기존 검색 필터가 계속 동작합니다.
- 로그는 최신순으로 표시됩니다.
- `Raw API Logs`에서 전체 로그를 계속 확인할 수 있습니다.
- dev Grafana에서 실제 로그를 기준으로 레이아웃을 시각 검증합니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
jq empty ops/monitoring/grafana/dashboards/nook-dev-logs.json
./gradlew check
```

Loki API에서 패널 LogQL을 직접 실행해 `service_name=nook-dev-api` 라벨과 다음 형태의 메시지가 반환되는지 확인합니다.

```text
req: GET /favicon.ico
res: 401 3ms
```

배포 후 Grafana dashboard API와 실제 화면에서 패널 type, query, options, 필터 동작과 레이아웃을 확인합니다.
