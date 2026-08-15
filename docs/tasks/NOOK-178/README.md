# NOOK-178 게시물 장소별 사용자 메모

## 목적

- 사용자가 저장한 게시물의 각 장소에 개인 메모를 남길 수 있게 한다.
- 장소 메모의 초기값은 저장 게시물 메모를 복사해 만들고, 이후에는 장소별 메모로 독립 수정/삭제한다.

## 범위

- 저장 게시물 장소 메모 수정 API 추가
  - `PATCH /api/v1/posts/{postId}/places/{placeId}/memo`
  - 요청 본문은 기존 게시물 메모와 같은 `{ "memo": string | null }`
- 저장 게시물 상세와 장소 파싱 결과의 장소 응답에 `memo` 필드 추가
- 사용자별 저장 게시물 장소 메모 테이블 추가
- 장소 연결이 처음 생기는 시점에 저장 게시물 메모가 있으면 장소 메모로 복사
  - 기존 파싱 완료 게시물을 새로 저장/재사용하는 경우
  - 수동으로 장소를 연결하는 경우

## 제외 범위

- 장소 공용 메모
- 장소 검색/파싱 흐름 변경
- 그룹/지도/장소 상세 화면의 메모 정책 변경

## 검증

- `./gradlew :nook-api-application:test --tests '*UpdatePostPlaceMemoUseCaseTest'`
- `./gradlew :nook-api-infrastructure:test --tests '*PostPlaceMemoPersistenceAdapterTest' --tests '*SavedPostQueryPersistenceAdapterTest' --tests '*PostPersistenceAdapterTest' --tests '*PersistenceEntityMetadataTest'`
- `./gradlew :nook-api-presentation:test --tests '*PostControllerTest'`
