# NOOK-147 서버와 API 로그를 간결한 단일 로그 스트림으로 통합

## 목적

Grafana의 API 요청 요약과 서버 원문 로그를 시간, 서비스, 레벨, 메시지 중심의 간결한 단일 로그 스트림으로
통합합니다.

## 범위

- `API Request Summary` 패널을 `Server & API Logs`로 변경합니다.
- API 요청 로그와 애플리케이션 서버 로그를 같은 Grafana Logs 패널에 표시합니다.
- 각 로그에는 timestamp, `nook-dev-api` 서비스, 레벨과 메시지를 기본 노출합니다.
- 로그는 최신순으로 표시하고 메시지의 줄바꿈을 유지합니다.
- 기존 level, request id, user id, route/path, status, keyword 필터를 유지합니다.
- 중복되는 `Raw API Logs` 패널은 제거합니다.

표시 형태는 다음과 같습니다.

```text
03:11:44  [nook-dev-api] [INFO]  req: GET /favicon.ico
                                      res: 401 3ms
03:11:43  [nook-dev-api] [WARN]  HikariPool-1 - Thread starvation or clock leap detected
```

## 제외 범위

- 애플리케이션 로그 포맷 변경
- Loki와 Promtail 수집 구조 변경
- Elasticsearch, OpenSearch 또는 Kibana 도입
- 로그 검색 변수 신규 추가
- API Alert 규칙 변경

## 성공 기준

- `Server & API Logs`가 테이블 컬럼 없이 단일 로그 스트림 형태로 표시됩니다.
- API 요청/응답 로그와 일반 서버 로그가 시간순으로 함께 표시됩니다.
- 각 로그에 시간, `nook-dev-api` 서비스, 로그 레벨과 메시지가 표시됩니다.
- 긴 request id나 transaction 컬럼으로 인한 가로 스크롤이 발생하지 않습니다.
- 기존 검색 필터가 계속 동작합니다.
- 로그는 최신순으로 표시됩니다.
- dev Grafana에서 실제 로그를 기준으로 레이아웃을 시각 검증합니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
jq empty ops/monitoring/grafana/dashboards/nook-dev-logs.json
./gradlew check
```

Loki API에서 패널 LogQL을 직접 실행해 API 요청 로그와 일반 서버 로그가 모두 다음 형태로 반환되는지 확인합니다.

```text
[nook-dev-api] [INFO]  req: GET /favicon.ico
                         res: 401 3ms
[nook-dev-api] [ERROR]  Application run failed
```

배포 후 Grafana dashboard API와 실제 화면에서 패널 type, query, options, 필터 동작과 레이아웃을 확인합니다.
