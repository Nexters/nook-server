# NOOK-109 Bright Data·이미지 캐시 분리 및 게시물·그룹 soft delete

## 목적

Bright Data 원문과 원본 미디어 URL의 저장 결과를 게시물 생명주기와 분리해 재사용하고, 저장 게시물과
그룹 삭제를 복구 가능한 soft delete로 변경한다.

## 범위

- Instagram 원본 식별자별 Bright Data 성공 응답 원문 저장 및 재사용
- 원본 미디어 URL별 CloudFront URL 저장 및 다운로드·업로드 재사용
- `DELETE /api/v1/posts/{postId}` 저장 게시물 soft delete API
- 기존 그룹 삭제와 `group_posts` 연결의 soft delete
- 동일 원본 재저장 시 저장 게시물과 요청 그룹 연결 재활성화
- 활성 조회와 소유 검증에서 삭제 행 제외

## 제외 범위

- 공용 `posts`, Bright Data 응답, 미디어 캐시 및 S3 객체 삭제
- 캐시 TTL, 강제 갱신 및 정리 배치
- 삭제 그룹 복구 API

## 저장 및 삭제 정책

Bright Data 호출 전에 `(source_type, external_post_id)` 캐시를 조회한다. 캐시가 없을 때만 provider를
호출하고 유효하게 매핑된 성공 응답을 저장한다. 미디어 저장도 원본 URL 캐시를 먼저 조회하며 hit이면
다운로드와 S3 호출 없이 기존 URL을 반환한다.

게시물 삭제 API의 `postId`는 `user_saved_posts.id`이다. 저장 게시물과 그 `group_posts` 연결만 같은 짧은
트랜잭션에서 soft delete하고 공용 게시물 및 캐시는 유지한다. 동일 사용자가 같은 원본을 다시 저장하면
기존 행을 재활성화한다. 그룹 삭제는 그룹 및 소속 연결만 soft delete하며 저장 게시물은 유지한다.

## 성공 기준

- 같은 Instagram 원본은 Bright Data를 한 번만 호출하고 저장 응답을 재사용한다.
- 같은 원본 미디어 URL은 저장된 CloudFront URL을 재사용한다.
- 삭제한 저장 게시물과 그룹은 모든 활성 조회 및 소유 검증에서 제외된다.
- 저장 게시물 재생성 시 provider와 미디어 업로드 없이 기존 데이터가 재활성화된다.
- `./gradlew check`가 성공한다.

## 검증

```shell
./gradlew detekt
./gradlew test
./gradlew check
```

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`
