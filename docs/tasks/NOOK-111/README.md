# NOOK-111 그룹 삭제 시 단독 저장 게시물 soft delete

## 목적

그룹 삭제 후 어떤 활성 그룹에도 속하지 않는 사용자 저장 게시물을 함께 soft delete하여 전체 게시물과
지도 조회에서 제외한다.

## 범위

- 삭제 그룹의 활성 게시물 연결 식별
- 그룹 연결을 soft delete한 뒤 다른 활성 그룹 연결이 없는 저장 게시물만 soft delete
- 다른 활성 그룹에 속한 저장 게시물 유지
- 단일 그룹, 다중 그룹, 빈 그룹 및 권한 실패 테스트 보강
- 동일 Instagram 원본 재저장 시 기존 저장 게시물 복구 동작 유지

## 제외 범위

- 공용 게시물, 장소, 북마크, Bright Data 응답 및 미디어 캐시 삭제
- 그룹 또는 저장 게시물 복구 API
- 공개 API 계약 변경
- 데이터베이스 스키마 변경

## 성공 기준

- 삭제 그룹에만 속한 저장 게시물은 `deleted_at`이 기록된다.
- 다른 활성 그룹에 속한 저장 게시물은 삭제되지 않는다.
- 그룹과 그룹 연결은 기존처럼 soft delete된다.
- 소유하지 않은 그룹 삭제의 오류 의미가 유지된다.
- `./gradlew check`가 성공한다.

## 검증

- `./gradlew :nook-api-infrastructure:test --tests '*GroupPersistenceAdapterTest'`
- `./gradlew check`
