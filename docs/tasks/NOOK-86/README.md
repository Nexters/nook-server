# NOOK-86 지도 바운더리 및 최근 저장 공간 조회 API

## 목적

지도 화면에서 현재 viewport 안의 북마크 장소를 빠르게 표시하고, 바텀시트에서 현재 사용자가 최근
저장한 공간을 계속 탐색할 수 있도록 한다.

## 범위

- 지도 바운더리 내 북마크 장소 경량 목록
- 최근 저장 공간 cursor pagination
- 최근 연관 저장 게시물의 첫 이미지 기반 공간 썸네일
- 사용자 북마크와 사용자 저장 게시물 관계를 모두 만족하는 장소만 반환
- 조회 인덱스, OpenAPI, HTTP Client 예제와 계층별 테스트

## 제외 범위

- 장소 직접 검색 및 게시물 수동 연결
- 지도 클러스터링
- 날짜변경선을 가로지르는 바운더리
- 장소 상세 API 변경

## API

```http
GET /api/v1/places/map
    ?northLatitude=37.60
    &westLongitude=126.80
    &southLatitude=37.40
    &eastLongitude=127.20
```

지도 응답은 마커 표시와 상세 진입에 필요한 `id`, `latitude`, `longitude`만 반환한다.

```http
GET /api/v1/places/recent?cursor={opaqueCursor}&size=20
```

최근 공간은 `user_place_bookmarks.created_at DESC, id DESC`로 정렬한다. cursor에는 마지막 항목의
두 정렬 키를 URL-safe Base64로 인코딩하며 클라이언트는 내부 값을 해석하지 않는다.

## 조회 정책

- `user_place_bookmarks`가 존재해야 한다.
- 현재 사용자의 `user_saved_posts`와 `post_places`를 통해 장소가 연결되어 있어야 한다.
- 여러 저장 게시물에 포함된 같은 장소도 한 번만 반환한다.
- 최근 공간 썸네일은 해당 사용자의 가장 최근 연관 게시물 중 첫 번째 이미지다.
- 이미지가 없으면 `thumbnailUrl`은 `null`이다.

## 스키마

최신순 cursor 조회를 위해 `user_place_bookmarks(user_id, created_at, id)` 인덱스를 추가한다. 물리적인
foreign key는 추가하지 않는다.

## 성공 기준

- 다른 사용자의 장소와 북마크 해제 장소가 노출되지 않는다.
- 저장 게시물과 관계없는 북마크 행이 노출되지 않는다.
- 바운더리 경계 좌표를 포함하고 잘못된 사각형은 `400 Bad Request`다.
- 최근 공간 pagination에서 누락과 중복 없이 안정적인 다음 cursor를 제공한다.
- 목록 조회에서 엔티티 전체 로딩과 N+1이 발생하지 않는다.
- `./gradlew check`가 통과한다.

## 검증

- domain 바운더리 불변식 테스트
- application 목록 및 cursor slice 테스트
- infrastructure projection mapping 테스트
- controller 계약 및 cursor codec 테스트
- OpenAPI 문서화 정책 테스트
- `./gradlew detekt`, `./gradlew test`, `./gradlew check`

## 배포 및 롤백

`ddl/up.sql`로 조회 인덱스를 먼저 추가한 뒤 애플리케이션을 배포한다. 롤백 시 애플리케이션을 이전
버전으로 되돌린 후 `ddl/rollback.sql`로 인덱스를 제거한다. 인덱스 생성 시 운영 테이블의 잠금 영향을
사전에 확인한다.
