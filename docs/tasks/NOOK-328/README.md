# NOOK-328 아카이브 선택 편집

## 목적

여러 저장 게시물의 아카이브 소속을 한 번의 요청으로 원자적으로 변경한다.

## 범위

- `PUT /api/v1/posts/groups` 일괄 재지정 API
- 게시물과 대상 그룹 전체의 사용자 소유권 선검증
- 최대 100개 게시물과 100개 그룹, 중복 식별자 멱등 처리
- 일부 식별자가 유효하지 않으면 변경 없이 기존 not found 오류 반환

## 제외 범위

- 기존 단건 `PUT /api/v1/posts/{postId}/groups` 변경
- 게시물 삭제
- 대상 그룹이 없는 상태로의 일괄 이동

## 검증

- application 유스케이스의 식별자 중복 제거
- presentation 요청 및 빈 대상 그룹 검증
- persistence 전체 선검증과 다건 변경 확인
- `./gradlew check`
