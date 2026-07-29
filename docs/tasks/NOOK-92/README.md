# NOOK-92 내 그룹 목록 최신 게시글 썸네일

## 목적

내 그룹 목록 화면에서 각 그룹의 최근 저장 게시글을 미리 볼 수 있도록 그룹 목록 응답에 대표 이미지
URL을 제공한다.

## 범위

- `GET /api/v1/groups`의 각 그룹 응답에 `thumbnailUrls` 추가
- 현재 사용자 그룹 전체의 썸네일을 N+1 없이 조회
- application view, persistence projection, presentation 응답 확장
- controller 계약, projection mapping, OpenAPI 문서화 테스트

## 제외 범위

- 그룹 생성, 수정, 삭제 요청 계약 변경
- 동영상 전용 썸네일 생성
- DB 스키마 변경
- 기존 응답 필드, 상태 코드 또는 오류 의미 변경

## API 계약

```json
{
  "id": 17,
  "name": "카페",
  "color": "YELLOW",
  "postCount": 112,
  "thumbnailUrls": [
    "https://cdn.example.com/posts/1.jpg",
    "https://cdn.example.com/posts/2.jpg",
    "https://cdn.example.com/posts/3.jpg"
  ]
}
```

- `thumbnailUrls`는 항상 배열이며 이미지가 없으면 빈 배열이다.
- 저장 시각 최신순인 이미지 보유 게시글을 최대 3개 반환한다.
- 최신순 정렬은 `user_saved_posts.created_at DESC, id DESC`로 안정화한다.
- 게시글마다 `display_order`가 가장 빠른 `IMAGE` 미디어 하나를 대표 이미지로 사용한다.
- 그룹과 저장 게시물이 모두 현재 사용자 소유인 경우에만 노출한다.

## 구현

MySQL 8.4의 `ROW_NUMBER()`로 그룹별 이미지 보유 게시글을 최신순으로 순위화하고 상위 3개만
조회한다. 그룹별 반복 조회 대신 그룹 요약 조회와 썸네일 조회를 각각 한 번씩 실행한다.

기존 `group_posts(group_id, user_saved_post_id)`, `user_saved_posts` 기본 키,
`post_media(post_id, display_order)` 인덱스로 조인할 수 있어 스키마 변경은 없다.

## 성공 기준

- 각 그룹의 `thumbnailUrls`가 최신순이며 최대 3개다.
- 이미지 없는 게시글과 다른 사용자의 데이터가 노출되지 않는다.
- 기존 그룹 목록 응답 필드와 의미가 유지된다.
- 그룹이 없을 때 불필요한 썸네일 조회를 실행하지 않는다.
- `./gradlew check`가 통과한다.

## 검증

- application 모델과 presentation 응답 매핑
- infrastructure projection 순서 및 그룹별 매핑
- 빈 그룹 목록 조회 최적화
- OpenAPI 응답 필드 설명 정책
- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`
