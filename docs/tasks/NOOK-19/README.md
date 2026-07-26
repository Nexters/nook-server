# NOOK-19 Swagger /v3/api-docs 500 오류 수정

## 목적

dev 서버 Swagger UI가 `/v3/api-docs`를 불러오지 못해 API 문서를 확인할 수 없는 문제를 수정합니다.

## 범위

- `ApiError.data` OpenAPI 스키마 생성 오류 수정
- 공통 오류 응답 JSON 계약 유지
- OpenAPI 설정 회귀 테스트 추가

## 제외 범위

- nginx 설정 변경
- API 응답 포맷 변경
- Swagger 문서 구조 전반 재설계

## 원인

springdoc이 `ApiError.data`의 `Map<String, Any?>` 타입을 OpenAPI schema로 변환하는 과정에서
`additionalProperties`를 잘못 처리해 `/v3/api-docs` 요청 중 예외가 발생했습니다.

## 성공 기준

- `/v3/api-docs`가 200으로 응답할 수 있는 schema를 생성합니다.
- `error.data`는 값이 없어도 `null`로 노출되는 기존 계약을 유지합니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew :nook-api-presentation:test
./gradlew check
```
