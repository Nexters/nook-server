# NOOK-178 장소별 사용자 메모

## 목적

- 사용자가 자기 장소에 개인 메모를 남길 수 있게 한다.
- 게시물을 저장하면 그 장소가 자동으로 북마크되므로, 메모의 주체를 `(사용자, 장소)`로 둔다.
- 장소 메모의 초기값은 저장 게시물 메모를 복사해 만들고, 이후에는 장소 메모로 독립 수정/삭제한다.

## 범위

- 장소 메모 수정 API 추가
  - `PATCH /api/v1/places/{placeId}/memo`
  - 요청 본문은 기존 게시물 메모와 같은 `{ "memo": string | null }`
  - 해당 장소를 북마크하지 않은 사용자는 `PLACE_NOT_FOUND`
- `user_place_bookmarks.memo` 컬럼 추가
- 장소 상세 응답의 `memo`를 장소 단위 필드로 이동
- 저장 게시물 상세와 장소 파싱 결과의 장소 응답 `memo`를 북마크 메모에서 조회
- 북마크가 처음 생기는 시점에 저장 게시물 메모가 있으면 장소 메모로 복사
  - 장소 파싱이 완료되는 경우
  - 기존 파싱 완료 게시물을 새로 저장/재사용하는 경우
  - 수동으로 장소를 연결하는 경우

## 설계 결정

- 메모를 북마크 행에 둔다. 별도 테이블을 두지 않으므로 **북마크를 해제하면 메모도 함께 사라진다.**
- 장소 상세는 게시물 메모로 폴백하지 않는다. 장소 메모가 없으면 `null`이다.
- 장소 상세의 `memo`는 `posts[]` 안이 아니라 최상위에 있다. 메모가 `(사용자, 장소)` 단위라
  같은 장소의 모든 게시물이 같은 값을 갖기 때문이다.

## 제외 범위

- 장소 공용 메모
- 장소 검색/파싱 흐름 변경
- 게시물별 장소 연결 삭제 API

## 마이그레이션

`up.sql` → `up-2.sql` 순서로 적용한다. `up-2.sql`은 `user_saved_post_place_memos`의 메모를
`(사용자, 장소)` 기준으로 합쳐 북마크로 옮긴 뒤 옛 테이블을 삭제한다. 같은 `(사용자, 장소)`에
게시물별 메모가 여러 건이면 가장 최근 것만 남는다.

## 검증

- `./gradlew :nook-api-infrastructure:test --tests '*PlaceMemoPersistenceAdapterTest' --tests '*PlaceDetailQueryPersistenceAdapterTest' --tests '*PlaceParsingPersistenceAdapterTest' --tests '*ConnectPostPlacePersistenceAdapterTest' --tests '*SavedPostQueryPersistenceAdapterTest' --tests '*PostPersistenceAdapterTest'`
- `./gradlew :nook-api-presentation:test --tests '*PlaceControllerTest' --tests '*PostControllerTest'`
