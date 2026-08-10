# NOOK-131 프로필 이미지 부분 수정 및 업로드 API 추가

## 목적

앱에서 프로필 이미지만 변경할 수 있게 하고, 클라이언트가 사진 파일을 직접 업로드할 수 있는 URL을 서버에서 발급한다.

## 범위

- `PATCH /api/v1/members/me`에서 `nickname` 또는 `profileImageUrl` 중 필요한 필드만 수정할 수 있게 변경
- `POST /api/v1/members/me/profile-image-upload` API 추가
- S3 presigned PUT URL과 CloudFront 공개 URL 발급
- 미디어 저장 비활성화 환경에서는 업로드 URL 발급 실패를 명확한 API 오류로 반환
- HTTP client 예시와 테스트 갱신

## 제외 범위

- 기존 프로필 이미지 객체 삭제
- 이미지 리사이징, 크롭, 검수 파이프라인
- 클라이언트 UI 변경

## 검증

- `./gradlew test`
- `./gradlew check`
