# NOOK-25 장소 단위 사용자 북마크 정책

## 목적

게시글과 장소의 연결에 저장되던 북마크를 사용자와 전역 장소 사이의 정보로 분리한다. 같은 사용자가
동일 장소를 포함한 여러 게시글을 저장하더라도 하나의 북마크 상태를 공유하며, 사용자 지도에는
`user_place_bookmarks`에 포함된 장소만 표시한다.

## 범위

- `user_place_bookmarks` 사용자–장소 매핑 추가
- `PATCH /api/v1/places/{placeId}/bookmark` API 제공
- 장소 파싱 완료 시 게시물을 저장한 모든 사용자의 북마크를 기본 ON으로 생성
- 게시물 장소 파싱 결과의 `bookmarked`를 사용자–장소 매핑 기준으로 반환
- 기존 `post_places.bookmarked = TRUE` 데이터를 사용자–장소 매핑으로 이관

## 제외 범위

- 현재 저장소에 없는 지도 장소 목록 API
- 장소 파싱 및 provider 연동 정책 변경
- 게시물과 장소의 연결 순서 변경

## 공개 API 변경

기존 endpoint는 제거한다.

```http
PATCH /api/v1/posts/{postId}/places/{placeId}/bookmark
```

장소 리소스 기준 endpoint로 대체한다.

```http
PATCH /api/v1/places/{placeId}/bookmark
X-Nook-User-Id: 7
Content-Type: application/json

{
  "bookmarked": false
}
```

요청 본문과 성공 응답 형식은 유지한다. 사용자가 저장한 게시글에 연결되지 않은 장소는
`PLACE_NOT_FOUND`를 반환한다.

## 데이터 모델

`user_place_bookmarks`의 행이 존재하면 북마크 ON, 존재하지 않으면 OFF이다.
`(user_id, place_id)` 유니크 제약으로 여러 게시글에 연결된 같은 장소의 상태를 하나로 공유한다.

장소 파싱이 완료되면 해당 원본 게시물을 저장한 사용자마다 새 장소의 북마크 행을
`INSERT IGNORE`로 생성한다. API에서 ON 요청도 같은 방식으로 멱등 처리하고, OFF 요청은 행을 삭제한다.

## 기존 데이터 이관

`ddl/up.sql`은 다음 순서로 실행한다.

1. `user_place_bookmarks` 테이블 생성
2. `user_saved_posts`, `post_places`를 조인해 기존 ON 상태를 사용자–장소 행으로 이관

동일 사용자·장소가 여러 게시글에서 서로 다른 상태였다면 하나라도 ON인 경우 ON으로 이관한다.
이는 새 장소의 기본 상태가 ON인 정책을 우선한 결정이다.

기존 애플리케이션이 배포 중에도 동작하도록 `post_places.bookmarked` 칼럼은 이번 DDL에서 즉시 제거하지
않는다. 새 버전은 이 칼럼을 읽거나 쓰지 않는다. 새 버전 배포가 완료되고 구버전 인스턴스가 모두
종료된 뒤 별도 정리 DDL로 제거한다.

## 성공 기준

- 북마크 변경 API path에 `postId`가 없다.
- 한 사용자의 동일 장소는 게시글 수와 무관하게 하나의 북마크 상태를 가진다.
- 장소 파싱 완료 시 북마크가 기본 ON이다.
- OFF인 장소는 사용자 북마크 집합에 존재하지 않는다.
- 기존 ON 데이터가 사용자–장소 매핑으로 이관된다.
- `./gradlew check`가 통과한다.

## 검증

- application 유스케이스 단위 테스트
- presentation controller 계약 테스트
- infrastructure 북마크 및 장소 파싱 persistence 테스트
- JPA entity 메타데이터 테스트
- `./gradlew check`
