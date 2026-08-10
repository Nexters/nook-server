# NOOK-126 비공개 Instagram 게시물 저장 요청 차단

## 목적

Instagram 비공개 공유 토큰이 포함된 게시물 생성 요청을 규칙 기반으로 식별하고 명시적인 오류를 반환한다.

## 배경

현재 게시물 저장 API는 URL을 저장한 뒤 콘텐츠 수집을 비동기로 처리한다. 이 구조에서는 비공개 게시물 공유 URL도
저장 요청 시점에는 성공 응답을 받은 뒤, 이후 콘텐츠 파싱 단계에서 실패 상태로 전환된다.

공유 시점에 사용자에게 즉시 피드백을 주기 위해 Instagram `/p/{id}`, `/reel/{id}` 식별자 길이를 기준으로
비공개 공유 토큰 형태를 선제 차단한다.

## 범위

- Instagram `/p/{id}`, `/reel/{id}`에서 11자를 초과하는 식별자를 비공개 게시물로 판정
- HTTP 400, `PRIVATE_POST`, `비공개 게시물은 저장할 수 없습니다.` 오류 반환
- 저장 및 기존 게시물 조회 전에 비공개 게시물 요청 차단
- URL 판별 규칙, 유스케이스 및 API 오류 응답 테스트
- `PostController.http`에 비공개 게시물 실패 요청 예시 추가

## 제외 범위

- 외부 provider 호출을 통한 실제 공개 범위 확인
- 기존 비공개 게시물 데이터 정리
- 공개 게시물 URL과 `UNSUPPORTED_POST_URL` 계약 변경

## 성공 기준

- 공개 11자 shortcode는 기존과 동일하게 처리된다.
- 11자를 초과하는 Instagram 게시물 식별자는 저장 또는 기존 게시물 조회 없이 `PRIVATE_POST`로 거절된다.
- API 응답은 HTTP 400과 `PRIVATE_POST` 오류 코드를 포함한다.

## 검증

- `./gradlew :nook-api-infrastructure:test --tests org.every.nook.api.infrastructure.instagram.InstagramContentUrlTest :nook-api-application:test --tests org.every.nook.api.application.post.CreatePostUseCaseTest :nook-api-presentation:test --tests org.every.nook.api.presentation.post.PostControllerTest`
- `./gradlew detekt`
- `./gradlew check`

## Rollback

이 PR을 되돌리면 비공개 공유 토큰 형태의 Instagram URL은 다시 저장 요청 시점에 차단되지 않고 기존 비동기
콘텐츠 파싱 실패 흐름을 따른다. DB 스키마 변경이 없어 별도 DDL rollback은 필요 없다.
