# NOOK-91 게시물 상세 및 장소 상세 연관 게시물에 그룹 정보 추가

## 목적

게시물 상세 화면과 장소 상세의 연관 게시물 목록에서 게시물이 속한 그룹을 표시할 수 있도록 응답에 그룹 정보를 포함한다.

## 범위

- `GET /api/v1/posts/{postId}` 응답에 게시물이 속한 그룹 목록 추가
  - 그룹 ID
  - 그룹명
  - 그룹 색상
- 게시물이 여러 그룹에 속할 수 있으므로 배열로 응답
- `GET /api/v1/places/{placeId}` 장소 상세 및 연관 게시물 조회 응답의 각 연관 게시물에 동일한 그룹 목록 추가
- 현재 사용자 기준으로 저장/연결된 그룹만 노출
- 응답 DTO, application view, persistence query, controller 테스트 갱신

## 제외 범위

- 그룹 생성/수정/삭제 계약 변경
- 게시물-그룹 연결 변경 API 수정
- 장소-게시물 연결 정책 변경
- DB 스키마 변경

## 검증

- `./gradlew :nook-api-presentation:test --tests '*PostControllerTest' --tests '*PlaceControllerTest' --no-daemon --no-build-cache`
- `./gradlew :nook-api-infrastructure:test --tests '*SavedPostQueryPersistenceAdapterTest' --tests '*PlaceDetailQueryPersistenceAdapterTest' --no-daemon --no-build-cache`
- `./gradlew check --no-daemon --no-build-cache`
