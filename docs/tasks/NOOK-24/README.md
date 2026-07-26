# NOOK-24 게시물 공개 API를 URL 기반 Post 계약으로 통합

## 목적

클라이언트가 출처와 내부 저장 모델을 알지 않고 URL 하나로 NOOK 게시물을 생성하도록 공개 API를
`Post` 리소스로 통합합니다.

기존 Instagram 콘텐츠 추출 API와 Saved Post API의 중복을 제거하고, Instagram은 서버 내부 콘텐츠
extractor 구현으로만 유지합니다. 이후 다른 URL 출처가 추가되어도 공개 요청 계약을 변경하지 않는
경계를 구성합니다.

## 범위

- `POST /api/v1/posts` URL 기반 게시물 생성 API
- `GET /api/v1/posts/{postId}/place-parsing` 장소 파싱 polling API
- 요청 필드 `url`, 선택 필드 `memo`와 사용자 소유 `postId` 중심의 request/response
- `PATCH /api/v1/posts/{postId}/places/{placeId}/bookmark` 장소 북마크 API
- 공개 Instagram 콘텐츠 추출 endpoint 제거
- application의 URL별 `PostContentExtractor` 선택 경계
- 게시물 생성 및 polling 유스케이스와 persistence port의 범용 명칭
- Instagram 전용 공개 오류를 게시물 콘텐츠 오류로 변경
- OpenAPI, application, infrastructure 및 MVC 테스트 갱신

## 제외 범위

- Instagram 외 실제 콘텐츠 extractor 구현
- 장소 파싱 worker, OCR, LLM 및 Kakao 연동 변경
- 인증 구현
- S3/CloudFront 실제 미디어 저장
- 저장 전 별도 preview API
- 기존 공개 API 하위 호환성

## API 계약

### 게시물 생성

```http
POST /api/v1/posts
X-Nook-User-Id: 1
Content-Type: application/json

{
  "url": "https://www.instagram.com/p/ABC123/",
  "memo": "주말에 방문"
}
```

성공 시 `201 Created`를 반환합니다.

```json
{
  "resultType": "SUCCESS",
  "success": {
    "postId": 1,
    "placeParsingStatus": "PENDING"
  }
}
```

공개 `postId`는 `user_saved_posts.id`에 대응하는 사용자 소유 NOOK 게시물 식별자입니다. 공용 원본
콘텐츠를 식별하는 내부 `posts.id`는 노출하지 않습니다.

### 장소 파싱 상태 조회

```http
GET /api/v1/posts/{postId}/place-parsing
X-Nook-User-Id: 1
```

`postId`와 사용자 식별자로 소유 경계를 확인합니다. `COMPLETED`이면 연결된 장소를 함께 반환하고,
처리 중이거나 실패한 경우에는 공개 parsing status와 실패 사유를 반환합니다.

### 장소 북마크 변경

```http
PATCH /api/v1/posts/{postId}/places/{placeId}/bookmark
X-Nook-User-Id: 1
Content-Type: application/json

{
  "bookmarked": true
}
```

게시물과 사용자 식별자로 소유 경계를 확인한 뒤 연결된 장소의 북마크 상태를 변경합니다.

## 설계

게시물 생성은 다음 순서로 처리합니다.

1. `ExtractPostContentUseCase`가 등록된 extractor 중 URL을 지원하는 구현을 선택합니다.
2. 출처별 extractor가 URL을 검증·정규화하고 콘텐츠를 공통 `ExtractedPostContent`로 변환합니다.
3. `CreatePostUseCase`가 해시태그와 장소 표시값을 정규화하고 게시물 제목을 생성합니다.
4. 요청 URL과 선택 메모를 보존하고 미디어 storage port를 호출해 영속화할 URL을 확보합니다.
5. `CreatePostPort`가 원본 콘텐츠, 사용자 게시물, 장소 파싱 작업을 단일 트랜잭션으로 저장합니다.
6. 사용자 게시물 식별자와 장소 파싱 초기 상태만 반환합니다.

Bright Data와 미디어 storage 호출은 DB 트랜잭션 밖에서 수행합니다. Instagram 외 출처는
`PostContentExtractor` 구현을 추가하고 Spring에 등록하는 방식으로 확장합니다.

공개 API에서 사용하는 `Post`는 사용자 소유 리소스입니다. 내부 원본 콘텐츠와 사용자 저장 관계는 기존
테이블 구조를 유지하지만, 두 식별자를 클라이언트에 동시에 노출하지 않습니다. NOOK-21에서 정한 정책에
따라 동일 URL을 다시 생성해도 요청마다 별도의 내부 게시물을 만듭니다.

## 호환성

클라이언트가 기존 서버 API를 사용하기 전이므로 다음 계약을 즉시 제거하며 호환 endpoint를 두지 않습니다.

- `POST /api/v1/instagram/contents/extract`
- `POST /api/v1/saved-posts`
- `GET /api/v1/saved-posts/{savedPostId}/place-parsing`
- 요청 필드 `instagramUrl`
- 응답 필드 `savedPostId`와 공용 원본 `postId`
- Instagram 및 Saved Post 전용 공개 오류 코드

## 오류

- 지원하지 않는 URL: `UNSUPPORTED_POST_URL`, `400 Bad Request`
- 콘텐츠 없음: `POST_CONTENT_NOT_FOUND`, `404 Not Found`
- 외부 콘텐츠 provider 오류: `POST_CONTENT_PROVIDER_ERROR`, `502 Bad Gateway`
- 외부 콘텐츠 provider timeout: `POST_CONTENT_PROVIDER_TIMEOUT`, `504 Gateway Timeout`
- 게시물 없음 또는 다른 사용자 소유 게시물: `POST_NOT_FOUND`, `404 Not Found`

## 성공 기준

- 클라이언트가 `POST /api/v1/posts`에 URL 하나를 전달해 게시물을 생성할 수 있습니다.
- 생성 응답에는 사용자 소유 `postId`와 `placeParsingStatus`만 노출됩니다.
- 같은 `postId`로 장소 파싱 결과를 polling할 수 있습니다.
- 같은 `postId`로 연관 장소의 북마크 상태를 변경할 수 있습니다.
- 공개 endpoint, request/response 및 오류 계약에 Instagram 또는 Saved Post 전용 용어가 남지 않습니다.
- Instagram 외 출처는 새로운 extractor 구현을 등록해 수용할 수 있습니다.
- 외부 provider 호출은 DB 트랜잭션 안에서 실행되지 않습니다.
- 제목 생성, 선택 메모, 다중 장소와 북마크를 포함한 NOOK-21 동작이 유지됩니다.
- 동일 URL 요청마다 별도 게시물을 만들고 사용자 소유 경계를 유지합니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew detekt
./gradlew test
./gradlew check
```

다음 항목을 자동 테스트로 검증합니다.

- URL을 지원하는 extractor 선택과 미지원 URL 거절
- 외부 콘텐츠와 미디어 처리 후 persistence 호출 순서
- 해시태그 및 장소 표시값 정규화
- 생성 및 polling 응답의 사용자 소유 `postId` 계약
- 선택 메모, 제목 생성, 원본 요청 URL 보존
- 다중 장소 polling과 북마크 변경
- 공용 원본 콘텐츠 식별자 및 `savedPostId` 비노출
- Instagram Posts/Reels dataset 선택과 provider 오류 변환
- 모든 공개 controller의 OpenAPI 문서화

## DDL

NOOK-24 자체의 스키마 변경은 없습니다. rebase 기준인 NOOK-21의 스키마와 DDL을 그대로 사용하며,
`posts`와 `user_saved_posts`의 내부 관계로 사용자 소유 경계를 유지합니다.

## 배포 및 롤백

클라이언트 연동 전에 새 endpoint와 OpenAPI 계약이 dev 환경에 반영됐는지 확인합니다. 문제가 있으면 이
PR을 되돌려 기존 Instagram 추출 및 Saved Post endpoint를 복구합니다. DB 스키마 변경이 없어 별도
데이터 롤백은 필요하지 않습니다.
