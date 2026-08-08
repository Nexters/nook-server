# NOOK-126 비공개 Instagram 게시물 저장 요청 차단

## 목적

Instagram 비공개 공유 토큰이 포함된 게시물 생성 요청을 규칙 기반으로 식별하고 명시적인 오류를 반환한다.

## 범위

- Instagram `/p/{id}`, `/reel/{id}`에서 11자를 초과하는 식별자를 비공개 게시물로 판정한다.
- 비공개 게시물 요청에 HTTP 400, `PRIVATE_POST` 오류를 반환한다.
- 기존 게시물 조회와 신규 저장 전에 비공개 게시물 요청을 차단한다.
- URL 판별 규칙과 API 오류 계약을 테스트한다.

## 제외 범위

- 외부 provider 호출을 통한 실제 공개 범위 확인
- 기존 비공개 게시물 데이터 정리
- 공개 게시물 URL과 `UNSUPPORTED_POST_URL` 계약 변경

## 검증

- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`
