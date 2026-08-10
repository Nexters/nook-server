# NOOK-128 회원 조회 지연 로딩 오류 및 소셜 로그인 안정화

## 목적

회원 프로필 조회와 소셜 로그인 과정에서 발생하는 JPA 지연 로딩 오류 및 간헐적 로그인 실패를 제거한다.

## 범위

- 소셜 계정 조회 시 세션 밖에서 `MemberEntity` 지연 로딩 프록시에 접근하지 않도록 조회 구조 수정
- 내 정보 조회의 `Could not initialize proxy ... no session` 오류 해결
- 로그인 회원 조회의 동일한 잠재 오류 해결
- 카카오 로그인 요청 경로와 토큰 검증 실패 응답 점검
- 활성·탈퇴·미존재 회원 및 카카오 인증 관련 테스트 보완

## 제외 범위

- 소셜 로그인 API 공개 계약 변경
- 카카오 OAuth/모바일 SDK 클라이언트 구현
- DB 스키마 변경

## 성공 기준

- `GET /api/v1/members/me`가 활성 회원에 대해 provider를 포함해 정상 응답한다.
- `POST /api/v1/auth/social`에서 기존 회원과 신규 회원 로그인이 정상 처리된다.
- 비정상 카카오 인증 정보는 일관된 인증 오류로 응답한다.
- JPA 세션 종료 후 프록시 초기화 예외가 발생하지 않는다.
- `./gradlew check`가 통과한다.

## 검증

- `./gradlew :nook-api-infrastructure:test --tests '*MemberRepositoryAdapterTest'`
- `./gradlew :nook-api-infrastructure:test --tests '*KakaoSocialIdentityProviderTest'`
- `./gradlew :nook-api-presentation:test --tests '*AuthControllerTest' --tests '*MemberControllerTest'`
- `./gradlew check`
