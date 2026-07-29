# NOOK-87 카카오 장소 검색 및 게시물 직접 공간 연결 API

## 목적

자동 장소 파싱이 장소를 찾지 못했을 때 사용자가 장소명으로 후보를 검색하고, 본인이 저장한 게시물에
선택한 공간을 직접 연결할 수 있도록 한다.

## provider 결정

공개 직접 검색은 기존 카카오 Local provider를 확장해 사용한다.

- 카카오 키워드 장소 검색은 provider 장소 ID, 주소, 좌표, 거리와 page 정보를 제공한다.
- 한 페이지는 최대 15건이며 1~45 provider page를 지원한다.
- 네이버 지역 검색은 최대 5건과 시작 위치 1로 제한돼 요구한 pagination을 제공할 수 없다.
- 기존 네이버 연동은 자동 파싱용 주소 geocoding fallback으로 유지하며 공개 직접 검색에는 사용하지 않는다.

## 범위

- 카카오 키워드 장소 검색 공개 API와 page pagination
- 선택적 현재 좌표 기반 거리 반환
- 사용자 ID와 만료 시각을 포함한 서명된 장소 선택 토큰
- 사용자 소유 저장 게시물 검증
- provider 장소 멱등 생성
- 전역 원본 게시물–장소 멱등 연결
- 요청 사용자 장소 북마크 기본 ON
- 실패한 장소 파싱 상태를 완료로 전환
- OpenAPI, HTTP Client 예제와 계층별 테스트

## 제외 범위

- 장소 연결 해제와 장소 정보 수정
- 네이버 지역 검색 공개 API
- 검색 결과 이미지
- 사용자별 별도 게시물–장소 연결 테이블

## API

```http
GET /api/v1/places/search
    ?query=퍼머넌트해비탯
    &page=0
    &size=15
    &latitude=37.5665
    &longitude=126.9780
```

공개 page는 0부터 시작하고 provider 호출 시 1을 더한다. 위도와 경도는 함께 생략하거나 함께 전달한다.
응답은 `items`, `page`, `size`, `hasNext`를 제공한다.

각 항목의 `selectionToken`은 사용자 ID, provider 장소 정보와 10분 만료 시각을 HS256으로 서명한
토큰이다. 서버는 JWT access secret을 서명 키로 재사용하지만 token type을 `place_selection`으로
분리한다.

```http
POST /api/v1/posts/{postId}/places
Content-Type: application/json

{
  "selectionToken": "..."
}
```

`postId`는 현재 사용자의 `user_saved_posts.id`다. 동일 요청을 재시도하면 기존 장소, 게시물 연결과
북마크를 재사용하고 같은 `placeId`를 반환한다.

## 연결 정책

- `post_places`의 기존 전역 원본 게시물–장소 관계를 유지한다.
- 같은 원본 게시물을 저장한 사용자는 직접 추가된 연관 장소를 볼 수 있다.
- 북마크는 요청 사용자에게만 생성하므로 다른 사용자의 지도에는 자동 표시되지 않는다.
- 장소 파싱이 `PENDING` 또는 `PROCESSING`이면 worker 경합을 막기 위해 `409 Conflict`를 반환한다.
- 장소 파싱이 `FAILED`이면 연결 후 `COMPLETED`로 전환하고 실패 사유를 제거한다.
- provider 호출은 검색 API에서만 수행하고 연결 DB 트랜잭션 안에서는 외부 호출을 실행하지 않는다.

## 오류

- 잘못된 검색 조건: `PLACE_SEARCH_INVALID_REQUEST`, 400
- provider 실패: `PLACE_SEARCH_PROVIDER_ERROR`, 502
- provider timeout: `PLACE_SEARCH_PROVIDER_TIMEOUT`, 504
- 조작·만료·다른 사용자 토큰: `PLACE_SELECTION_INVALID`, 400
- 게시물 없음 또는 다른 사용자 소유: `POST_NOT_FOUND`, 404
- 자동 장소 파싱 진행 중: `PLACE_PARSING_IN_PROGRESS`, 409

## 성공 기준

- 장소 검색이 카카오 page와 최대 15개 후보를 반환한다.
- 검색 좌표가 있으면 거리(m)를 반환한다.
- 다른 사용자의 저장 게시물에는 장소를 연결할 수 없다.
- 조작·만료·다른 사용자 선택 토큰을 거부한다.
- 같은 provider 장소와 게시물–장소 관계를 재사용한다.
- 연결 성공 후 요청 사용자의 장소 북마크가 존재한다.
- `./gradlew check`가 통과한다.

## 검증

- application 검색 조건, page 변환과 연결 결과 테스트
- 카카오 provider page, mapping, timeout/error 테스트
- 선택 토큰 서명, 만료와 사용자 바인딩 테스트
- persistence 소유권, 상태 전환과 멱등 연결 테스트
- controller 계약 및 OpenAPI 문서화 테스트
- `./gradlew detekt`, `./gradlew test`, `./gradlew check`

## 배포 및 롤백

스키마 변경은 없다. 기존 카카오 REST API 키와 JWT access secret을 사용한다. 문제가 있으면
애플리케이션을 이전 버전으로 되돌리며 별도 데이터베이스 롤백은 필요하지 않다. 이미 사용자가 직접
연결한 장소 관계와 북마크는 사용자 데이터이므로 자동 삭제하지 않는다.
