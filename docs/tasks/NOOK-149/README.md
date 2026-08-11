# NOOK-149 API 요청 로그 메시지와 파라미터 필드 분리

## 목적

API 요청 로그의 `message`에는 method와 route template만 표시하고 query parameter와 path variable 값은
별도의 구조화 JSON 필드로 기록합니다.

## 범위

- 메시지의 실제 URI를 Spring route template으로 대체합니다.
- 메시지에서 query string을 제거합니다.
- Query parameter를 `request.query_params` JSON 필드로 기록합니다.
- 마스킹되지 않은 중복 원문인 `request.query` 필드는 제거합니다.
- 동일한 query key의 복수 값은 배열로 보존합니다.
- Path variable을 `request.path_params` JSON 필드로 기록합니다.
- 민감한 query/path parameter 값은 기존 개인정보 필드명 규칙으로 마스킹합니다.
- route template이 없는 요청은 query string을 제외한 실제 URI를 표시합니다.
- 기존 request path, URL, body와 tracing 필드는 유지합니다.

표시 및 상세 필드 형태는 다음과 같습니다.

```text
req: GET /api/v1/posts/{postId}
res: 200 59ms
```

```json
{
  "request.query_params": {
    "includePlaces": ["true"],
    "tag": ["cafe", "date"]
  },
  "request.path_params": {
    "postId": "179"
  }
}
```

## 제외 범위

- Request body 구조 변경
- 응답 로그 구조 변경
- Loki와 Promtail 수집 구조 변경
- Grafana 대시보드 레이아웃 변경
- 그 외 기존 request 필드 제거
- Alert 규칙 변경

## 성공 기준

- 정상 매핑 요청의 메시지에 실제 path variable 값과 query string이 표시되지 않습니다.
- 메시지에 method와 route template이 표시됩니다.
- Query parameter의 다중 값과 URL decoding 결과가 JSON 배열로 기록됩니다.
- Path variable이 JSON object로 기록됩니다.
- 민감 parameter 값이 마스킹됩니다.
- route template이 없는 요청은 query string 없는 실제 URI가 표시됩니다.
- 기존 구조화 필드와 필터가 계속 동작합니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew :nook-api-presentation:test --tests '*RequestLoggingFilterTest'
./gradlew check
```

dev 배포 후 실제 API를 호출하고 Loki와 Grafana에서 메시지, `request_query_params`,
`request_path_params`를 확인합니다.
