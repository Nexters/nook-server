# NOOK-121 로그인 자동 가입 및 마이페이지 API 구현

## 목적

앱 로그인 마무리를 위해 별도 회원가입 상태를 제거하고, 소셜 로그인 시 신규 회원을 자동 생성한다.
마이페이지에서 필요한 내 정보 조회/수정, 로그아웃, 회원 탈퇴 API를 제공한다.

## 범위

- 소셜 로그인 신규 사용자 자동 생성 및 토큰 발급
- `SIGNUP_REQUIRED`/signup token 응답 제거
- 내 정보 조회/수정 API 추가
- 로그아웃 시 활성 refresh token revoke
- 회원 탈퇴 시 회원 상태 변경, 소셜 계정 연결 삭제, 활성 refresh token revoke
- controller `.http` 예시와 테스트 갱신

## 제외 범위

- 기존 저장 게시물, 그룹, 장소 데이터의 물리 삭제
- access token blacklist 저장소 추가
- 닉네임 온보딩 플로우 추가

## 검증

- `./gradlew test`
- `./gradlew check`
