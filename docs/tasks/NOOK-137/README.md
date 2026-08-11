# NOOK-137 프로필 이미지 presigned URL S3 권한 및 CORS 수정

## 목적

프로필 이미지 업로드용 presigned PUT URL이 실제 브라우저와 S3에서 성공하도록 S3 권한, CORS, 응답 계약을 정리한다.

## 확인된 문제

- 발급된 presigned URL의 object key는 `profile-images/*`인데 media writer IAM policy는 `post-media/*`만 허용한다.
- media bucket에 CORS 설정이 없어 브라우저 preflight가 실패한다.
- presigned PUT 요청에 포함해야 하는 서명 헤더를 클라이언트가 응답만 보고 알기 어렵다.

## 범위

- media bucket CORS 설정 추가
- media writer IAM policy에 `profile-images/*` 권한 추가
- 프로필 이미지 업로드 URL 발급 응답에 PUT 요청용 `headers` 추가
- HTTP client 예시와 테스트 갱신

## 제외 범위

- 기존 S3 객체 정리
- 이미지 리사이징, 크롭, 검수 파이프라인
- 클라이언트 구현 변경

## 검증

- `./gradlew check`
