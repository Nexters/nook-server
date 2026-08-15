# NOOK-156 Swagger 응답 DTO required 스키마 복구

## 목적

Jackson 3 전환 이후 Swagger 응답 DTO의 Kotlin non-null 필드가 `required`에서 누락된 회귀를 복구합니다.

## 범위

- Springdoc의 Kotlin 스키마 분석에 필요한 Jackson 2 Kotlin 모듈을 presentation 런타임에 복구합니다.
- 애플리케이션의 Jackson 3 ObjectMapper 구성은 유지합니다.
- `/v3/api-docs` 응답 스키마의 required/nullable 계약을 검증하는 회귀 테스트를 추가합니다.
- 실제 OpenAPI 문서에서 응답 DTO의 required 필드가 복구되는지 확인합니다.

## 제외 범위

- 공개 API 요청·응답 필드 변경
- 애플리케이션 ObjectMapper의 Jackson 2 회귀
- DB 스키마 변경
- Swagger UI 디자인 변경

## 성공 기준

- Kotlin non-null 응답 필드가 `/v3/api-docs`의 `schema.required`에 포함됩니다.
- nullable 응답 필드는 `required`로 잘못 노출되지 않습니다.
- 기존 Jackson 3 ObjectMapper 기반 애플리케이션 기동과 직렬화가 유지됩니다.
- `./gradlew check`가 통과합니다.
