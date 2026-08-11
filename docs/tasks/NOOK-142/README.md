# NOOK-142 로그 대시보드 가독성과 요청 로그 payload 정리

## 목적

NOOK-138로 추가된 구조화 로그가 Grafana에서 긴 JSON 라인으로 노출되어 읽기 어려운 문제를 줄입니다. 기본 화면은
핵심 API 요청 필드만 테이블로 보여주고, 상세 확인이 필요하면 원본 로그 패널을 사용합니다.

## 범위

- API request id를 dash 없는 16자 영문/숫자 식별자로 정규화합니다.
- `request.body`, `response.body`는 개별 필드로 평탄화하지 않고 JSON 문자열 하나로 남깁니다.
- `@PrivacyArgument`가 붙은 DTO 필드와 기존 민감 키워드 필드는 body 로그에서 `****`로 마스킹합니다.
- `Nook Dev Logs`의 기본 API 요청 패널을 주요 필드 테이블로 바꿉니다.
- 원본 로그 확인을 위한 `Raw API Logs` 패널은 유지합니다.

## 제외 범위

- 분산 tracing 백엔드 도입
- 서버 간, 큐 간 request id 전파 신규 구현
- OpenSearch/Elasticsearch 도입

## 주요 필드

`API Request Summary` 테이블은 다음 필드만 기본 노출합니다.

```text
time
level
request_id
user_id
method
route
status
duration_ms
transaction
```

body 상세는 원본 로그의 `request.body`, `response.body`에서 JSON 문자열로 확인합니다.

## 성공 기준

- Grafana 로그 대시보드의 기본 API 요청 패널이 긴 JSON 라인 대신 주요 필드 테이블로 보입니다.
- API 응답의 `X-Request-Id`와 로그의 `request.id`가 dash 없는 16자 문자열입니다.
- request/response body는 `request.body`, `response.body` JSON 문자열 필드로 남습니다.
- `@PrivacyArgument` 필드는 body 로그에서 `****`로 마스킹됩니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew check
```

배포 후 dev API에서 `X-Request-Id` 길이와 Grafana 대시보드 테이블 표시를 확인합니다.
