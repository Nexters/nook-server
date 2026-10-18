# NOOK-369 프론트 연동

## 공유 발급

로그인 상태에서 `POST /api/v1/posts/{postId}/share-links`를 호출한다. 요청 본문은 없다.
postId는 공통 원본 ID가 아닌 **내 저장 게시물 ID**이다.

```json
{"success":{"token":"43-character-url-safe-token","sharedAt":"2026-10-18T09:00:00+09:00"}}
```

매 발급마다 새 스냅샷과 토큰을 만든다. 반환된 token으로 앱/웹의 게시물 공유 랜딩 URL을 구성한다.
이 토큰은 아카이브 공유 토큰과 별개이므로 아카이브 구독/조회 API에 사용하지 않는다.
본문/장소 처리 중이면 409 POST_SHARE_NOT_READY, 다른 사람의 저장 건은 404 POST_NOT_FOUND다.
장소 인식 실패여도 콘텐츠 저장이 완료된 게시물은 장소 없이 공유할 수 있다.

## 공개 랜딩 조회

`GET /api/public/v1/post-shares/{token}`은 비로그인으로 호출 가능하다.
로그인 토큰을 첨부하면 `viewerSavedPostId`로 내 저장 여부를 확인할 수 있다.

```json
{
  "success": {
    "sharedAt": "2026-10-18T09:00:00+09:00",
    "viewerSavedPostId": null,
    "post": {
      "title": "제목", "body": "본문", "authorIdentifier": "author",
      "canonicalUrl": "https://www.instagram.com/p/ABC/", "publishedAt": null,
      "media": [], "hashtags": [], "places": []
    }
  }
}
```

places에는 id/provider/externalPlaceId/name/address/latitude/longitude/category/phoneNumber,
thumbnailUrl/thumbnailParsingStatus/tags/sequence가 있다. 배열의 순서는 공유 시점 그대로이며 sequence가
연속된 숫자일 필요는 없다. 개인 메모·북마크·소유자의 아카이브는 응답에 없다.
공유자가 장소를 나중에 추가/삭제해도 이 링크의 장소 목록은 바뀌지 않는다.

- viewerSavedPostId=null: 비로그인이거나 아직 저장하지 않은 상태. `내 아카이브에 저장` CTA 제공.
- 값이 있으면: 기존 내 게시물 안내/이동을 제공하고, 저장을 선택하면 기존 장소와 메모를 유지한 채
  아카이브만 추가한다. 공유 스냅샷으로 기존 내 장소를 덮어쓰는 기능은 제공하지 않는다.
- 비로그인 사용자가 저장하려 하면 로그인 후 token과 저장 선택을 이어간다.

## 내 아카이브에 저장

`POST /api/v1/post-shares/{token}/save` (로그인 필수):

```json
{"groupIds":[9,10]}
```

```json
{"success":{"postId":22,"alreadySaved":false}}
```

신규 저장/삭제했던 내 저장 건 복구면 alreadySaved=false, 기존 활성 저장 건이면 true다.
postId는 **수신자의 내 저장 게시물 ID**이며 저장 완료 후 기존 내 상세로 이동한다.
기존 아카이브 선택 API와 선택 UI를 재사용하고, 요청 중 중복 탭을 막는다.
재요청은 같은 내 저장 ID를 사용하고 아카이브 연결도 중복되지 않는다.
성공 후 내 상세·아카이브 목록·지도 관련 캐시를 갱신한다. 새 수집 작업이나 처리 폴링은 필요 없다.

## 오류

- 404 POST_SHARE_NOT_FOUND: 잘못된 형식/없는 토큰.
- 410 POST_SHARE_EXPIRED: 원본 저장 건 삭제로 만료. 원본을 복구해도 같은 링크는 계속 만료 상태다.
- 404 GROUP_NOT_FOUND: 선택한 아카이브가 없거나 본인 소유가 아님.
- 400: 빈 groupIds, 잘못된 ID 등 입력 오류.
- 401: 저장/발급 시 로그인 필요 또는 전달한 인증 토큰이 유효하지 않음.

공유가 만료되어도 이미 저장한 사용자의 내 상세는 유지된다. 만료 링크에서 개인 저장 내용을
우회 조회하지 말고 기존 내 목록·상세 경로로 접근한다. 아카이브 원본 참조 정책(NOOK-378)은 변경하지 않는다.

## 계약 갱신과 수동 확인

배포 후 OpenAPI를 갱신해 클라이언트를 생성한다. `.http` 파일에 요청 예제가 있다.
공유 버튼/랜딩/CTA와 로그인 복귀, 오류 화면 연결은 앱 저장소의 별도 작업이다.

1. 공유 전 장소 하나를 삭제하고 하나를 직접 추가한 뒤 링크를 발급한다. 해당 목록과 순서만 보여야 한다.
2. 공유 뒤 원본 장소를 편집해도 기존 링크는 바뀌지 않아야 한다.
3. 새 사용자 저장 후 원본과 수신자의 장소를 각각 편집한다. 서로의 연결에 영향을 주면 안 된다.
4. 이미 저장한 사람이 다른 아카이브를 선택한다. 개인 메모·장소는 유지되고 아카이브만 추가돼야 한다.
5. 원본을 삭제·재저장한다. 옛 링크는 계속 410이고 수신자의 내 저장은 유지돼야 한다.
6. 실제 MySQL에서 동일 사용자/게시물에 동시 저장해 중복 행·순서 충돌이 없는지 확인한다.

실제 DB 적용·MySQL 동시성·브라우저 확인은 자동 검증에서 실행하지 않았다.
