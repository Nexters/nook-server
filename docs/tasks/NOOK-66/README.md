# NOOK-66 Swagger 필드 description 문서화 보강

## 목적

Swagger/OpenAPI 문서에서 공개 API request/response 필드와 API parameter의 의미가 누락되지 않도록 설명을 보강합니다.

## 범위

- 공개 API request/response DTO schema field description 추가
- path, query, header parameter description 추가
- OpenAPI 문서화 정책에 field/parameter description 규칙 명시
- schema field와 parameter description 누락을 검출하는 정책 테스트 추가

## 제외 범위

- endpoint 계약 변경
- 응답 필드 추가, 삭제 또는 타입 변경
- 비공개 내부 모델 문서화

## 검증

- `./gradlew :nook-api-presentation:test`
- `./gradlew check`
