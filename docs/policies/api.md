# API 정책

- presentation request/response와 domain model을 분리합니다.
- 입력값은 presentation 경계에서 검증합니다.
- 하위 호환성을 깨는 변경은 작업 문서에 명시합니다.

## 응답 형식

- 본문이 있는 성공 및 실패 응답은 `ApiResponse<T>` envelope를 사용합니다.
- 성공 응답은 `resultType: SUCCESS`와 `success`를 포함합니다.
- 실패 응답은 `resultType: FAIL`과 `error`를 포함합니다.
- `success`와 `error`는 동시에 노출하지 않습니다.
- 204 응답에는 본문과 envelope를 사용하지 않습니다.

## OpenAPI 문서화

- 모든 controller에는 Swagger UI 그룹명이 명확히 보이도록 `@Tag(name = "...")`를 명시합니다.
- 모든 공개 API handler에는 endpoint 목적을 한국어로 설명하는 `@Operation(summary = "...")`를 명시합니다.
- `@Tag` 이름은 도메인 또는 리소스 단위의 명사형 영문 이름을 사용합니다.
- `@Operation` 요약은 구현 방식이 아니라 클라이언트가 호출하는 사용자 의도를 설명합니다.

## 오류 처리

- 예상 가능한 오류는 `NookException`으로 표현합니다.
- application의 `NookException`은 HTTP 및 Spring 타입을 알지 않습니다.
- domain의 불변식 실패는 application 경계에서 `NookException`으로 변환합니다.
- presentation이 `ErrorType`을 HTTP 상태로 변환합니다.
- 실패 응답도 오류 의미에 맞는 실제 HTTP 4xx 또는 5xx 상태를 유지합니다.
- 예상하지 못한 오류의 메시지, 스택, SQL 및 provider 원문을 응답에 노출하지 않습니다.
- 오류 응답 상세 계약은 `ADR-0002-api-response-and-error-handling.md`를 따릅니다.
