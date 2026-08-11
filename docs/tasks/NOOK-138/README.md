# NOOK-138 구조화 API 로그와 요청 추적 컨텍스트 추가

## 목적

API 요청, 서버 로그, 에러 로그를 같은 요청 컨텍스트로 묶어 Grafana/Loki에서 검색할 수 있게 합니다.

## 범위

- 모든 API 응답에 `X-Request-Id` 헤더를 포함합니다.
- 요청별 MDC에 `request.id`, `user.id`, `http.route`, `transaction.name`, `transaction.duration.ms` 등을 남깁니다.
- API access log를 Spring Boot 구조화 로그의 `logstash` JSON 형식으로 출력합니다.
- 허용된 request header 기반 클라이언트 컨텍스트를 로그 필드로 남깁니다.
- JSON request/response body를 설정 기반으로 수집하고, 제한 깊이로 `request.body.*`, `response.body.*` 필드에 평탄화합니다.
- body 수집 시 크기 제한과 민감 필드 마스킹을 적용합니다.
- 요청에서 시작된 비동기 parsing task와 retry scheduler 로그에 MDC request context를 전파합니다.
- dev Promtail이 JSON 로그의 `level` 필드를 Loki label로 추출하도록 갱신합니다.
- `Nook Dev Logs` 대시보드에 request id, user id, route, status, keyword 기반 검색 변수를 추가합니다.

## 제외 범위

- OpenSearch/Elasticsearch 신규 도입
- 분산 tracing 백엔드 구축
- request/response body 원문 전체 저장
- API request/response 계약 변경

## 설계

Spring Boot 4의 내장 structured logging을 사용해 console log를 `logstash` JSON으로 출력합니다. 별도 logstash encoder
의존성은 추가하지 않습니다.

`RequestLoggingFilter`는 security filter보다 앞에서 실행되어 요청 초기에 `request.id`, method, path, client IP,
허용된 header를 MDC에 넣고 응답 헤더에 `X-Request-Id`를 설정합니다. 요청 처리 종료 시 response status, route,
duration, body 필드를 추가한 뒤 다음 형식의 access log를 남깁니다.

```text
req: POST /api/v1/posts
res: 201 123ms
```

`RequestContextInterceptor`는 handler mapping 이후 controller 실행 전에 route와 인증된 `user.id`를 MDC에 넣습니다.
따라서 controller/application log도 같은 `request.id`와 `user.id`로 검색할 수 있습니다.

`MdcTaskDecorator`는 `@Async` executor와 parsing retry scheduler에 등록되어 task 제출 시점의 MDC snapshot을 worker
thread에 복원합니다. 이 전파는 같은 JVM 안에서 decorator가 적용된 executor/scheduler를 통해 실행되는 작업에
적용됩니다. 다른 서버, 큐, 외부 HTTP 호출로 이어지는 작업은 `X-Request-Id` 전달 또는 메시지 metadata 저장 같은 별도
전파 구현이 필요합니다.

body 로그는 다음 정책을 따릅니다.

- 공통 기본값은 request/response body 수집 off입니다.
- local/dev/staging은 기본 on, live는 기본 off입니다.
- `HTTP_LOG_REQUEST_BODY_ENABLED`, `HTTP_LOG_RESPONSE_BODY_ENABLED`로 환경별 override가 가능합니다.
- `application/json`, `application/*+json`만 수집합니다.
- 기본 최대 크기는 16KB입니다.
- JSON 필드는 최대 깊이 5, 최대 80개 필드, 배열당 최대 10개 항목만 평탄화합니다.
- `authorization`, `cookie`, `password`, `secret`, `token`, `signature`, `presigned` 등 민감 키워드가 포함된 필드는 `[REDACTED]`로 남깁니다.

주요 로그 필드는 다음과 같습니다.

```text
request.id
request.method
request.path
request.query
request.url
request.client.ip
request.headers.user_agent
request.headers.x_forwarded_for
user.id
http.method
http.route
http.status_code
response.status
transaction.name
transaction.type
transaction.duration.ms
request.body.<field>
response.body.<field>
error.type
error.message
```

## Loki 검색 예시

Loki label은 `env`, `job`, `container`, `stream`, `level`처럼 cardinality가 낮은 값만 유지합니다. `request.id`,
`user.id`, body field 등은 JSON 필드로 검색합니다.

```logql
{env="dev", job="nook-api"} | json | request_id="req-test-1"
{env="dev", job="nook-api"} | json | user_id="42"
{env="dev", job="nook-api"} | json | http_route="/api/v1/posts/{postId}"
{env="dev", job="nook-api"} | json | response_status="500"
{env="dev", job="nook-api"} | json | request_body_url="https://example.com/post"
```

Grafana/Loki의 `json` parser는 dot key를 query identifier에서 underscore로 노출할 수 있습니다. 예를 들어
`request.id`는 `request_id`, `transaction.duration.ms`는 `transaction_duration_ms`로 조회합니다.

## Grafana 대시보드 검색

`Nook Dev Logs` 대시보드는 다음 변수로 `Filtered API Request Logs` 패널을 필터링합니다.

```text
Level
Request ID
User ID
Route
Status
Keyword
```

각 검색 변수의 기본값은 빈 값이며, 빈 값은 전체 로그를 의미합니다. 특정 요청만 보려면 `Request ID`에 요청 id를
입력하고, 특정 사용자의 로그만 보려면 `User ID`에 user id를 입력합니다. 경로와 상태 코드는 각각 `Route`,
`Status`로 좁힙니다.

`Filtered API Request Logs` 패널은 입력값을 로그 라인에 대한 포함 검색으로 적용한 뒤 JSON 필드를 펼쳐 보여줍니다.
구조화되지 않았거나 요청 컨텍스트가 없는 로그도 기본 상태에서는 함께 확인할 수 있습니다.

## 성공 기준

- API 응답 헤더에서 `X-Request-Id`를 확인할 수 있습니다.
- 서버 로그와 API access log를 `request.id`로 묶어 조회할 수 있습니다.
- 요청에서 시작된 비동기 parsing task 로그를 같은 `request.id`로 조회할 수 있습니다.
- 인증된 요청은 `user.id`로 조회할 수 있습니다.
- API access log에서 route, status, duration, client header context를 조회할 수 있습니다.
- dev/staging/local에서 JSON body 필드를 검색할 수 있고 민감 필드는 마스킹됩니다.
- `Nook Dev Logs` 대시보드에서 request id, user id, route, status, keyword 기준으로 검색할 수 있습니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew check
```

배포 후 dev API에서 다음을 확인합니다.

```shell
curl -i https://api-dev.everynook.co.kr/actuator/health
curl -i -H 'X-Request-Id: manual-test-1' https://api-dev.everynook.co.kr/api/v1/members/me
```

Grafana Explore에서 다음 LogQL을 확인합니다.

```logql
{env="dev", job="nook-api"} | json | request_id="manual-test-1"
{env="dev", job="nook-api"} |= "place-parsing" | json | request_id="manual-test-1"
```

`Nook Dev Logs` 대시보드에서는 `Request ID`에 `manual-test-1`을 입력해 같은 로그가 조회되는지 확인합니다.

## 배포 및 롤백

애플리케이션 배포와 dev exporter Promtail 재배포가 필요합니다.

```shell
rsync -av ops/dev-exporters/ nook-dev:/opt/nook/exporters/
ssh nook-dev 'cd /opt/nook/exporters && ./scripts/deploy.sh'
```

롤백은 애플리케이션 이전 버전 재배포와 Promtail 설정 이전 버전 재배포로 수행합니다. 긴급하게 body 수집만 끄려면
다음 환경변수를 false로 설정하고 애플리케이션을 재시작합니다.

```shell
HTTP_LOG_REQUEST_BODY_ENABLED=false
HTTP_LOG_RESPONSE_BODY_ENABLED=false
```
