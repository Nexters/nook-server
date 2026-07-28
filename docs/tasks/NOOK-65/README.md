# NOOK-65 Swagger 요청 스키마에서 groupIds 검증용 필드 숨김

## 목적

`groupIds` 검증을 위한 Kotlin 계산 프로퍼티 `areGroupIdsPositive`가 Swagger/OpenAPI 요청 스키마에 공개 필드처럼 노출되지 않도록 수정합니다.

## 범위

- `CreatePostRequest`의 검증용 프로퍼티를 JSON/OpenAPI 모델에서 숨김
- `ReplaceSavedPostGroupsRequest`의 검증용 프로퍼티를 JSON/OpenAPI 모델에서 숨김
- OpenAPI 요청 스키마에 `areGroupIdsPositive`가 포함되지 않는 회귀 테스트 추가

## 제외 범위

- 요청/응답 공개 계약 변경
- `groupIds` 검증 정책 변경
- 다른 DTO 구조 리팩터링

## 검증

- `./gradlew :nook-api-presentation:test`
- `./gradlew check`
