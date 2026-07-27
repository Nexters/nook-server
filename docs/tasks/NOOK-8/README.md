# NOOK-8 모바일 SDK 기반 소셜 회원가입 및 로그인

## 목적

카카오 SDK, Android Google 로그인, iOS Apple 로그인으로 사용자를 인증하고 신규 사용자가 고유한
닉네임과 선택적인 프로필 이미지 URL을 입력해 가입할 수 있도록 합니다. 이메일과 비밀번호는
수집하지 않습니다.

## 범위

- 카카오·Google·Apple 사용자 인증과 신규/기존 회원 판별
- 닉네임 중복 금지와 선택적인 HTTPS 프로필 이미지 URL
- 가입용 단기 토큰과 JWT access/refresh token 발급
- refresh token rotation과 재사용 차단
- 회원, 소셜 계정, refresh token 영속화
- 첫 API 공통 오류 응답 계약
- Spring Boot 4.0.7과 호환되지 않는 미사용 Kotlin JDSL 자동 설정 제거

## 제외 범위

- 모바일 앱 SDK와 로그인 화면
- 이메일·비밀번호 및 다른 소셜 provider
- 서로 다른 provider 계정 연결
- 이미지 업로드와 외부 저장소
- 프로필 수정, 회원 탈퇴, provider 연결 해제

## 설계

외부 provider 호출은 DB 트랜잭션 밖에서 완료합니다. 신규 사용자에게는 provider와 subject만 담은
10분 만료 가입 토큰을 발급하고, 회원 생성 트랜잭션에서 닉네임과 소셜 계정 고유성을 검증합니다.
access token은 30분, refresh token은 30일 동안 유효하며 갱신할 때마다 기존 refresh token을
폐기합니다. 원문 refresh token은 저장하지 않고 SHA-256 해시만 저장합니다. 카카오 access token은
token-info의 `app_id`가 서비스의 `KAKAO_APP_ID`와 일치하는지 확인합니다. Google ID token은
공개키 서명, 발급자, 만료 시간과 서버용 Web client ID audience를 검증합니다. Apple은 앱이 전달한
identity token과 authorization code 교환 결과를 각각 검증하고 동일 사용자 여부를 확인합니다.

## API

- `POST /api/v1/auth/social`: provider 인증 및 로그인/가입 필요 여부 반환
- `POST /api/v1/members`: 가입 토큰으로 회원 생성
- `POST /api/v1/auth/token/refresh`: refresh token 회전

## 검증

- 도메인 규칙과 유스케이스 단위 테스트
- MySQL Testcontainers 기반 persistence 통합 테스트
- 카카오 API 응답과 Google·Apple 토큰 오류 매핑 테스트
- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`

## 배포 및 롤백

staging에 `ddl/up.sql`을 먼저 적용하고 환경변수를 주입한 뒤 카카오·Apple 가입과 토큰 갱신을
검증합니다. 롤백 시 애플리케이션을 이전 버전으로 되돌립니다. 가입 데이터가 존재하면 백업과
승인 없이 `ddl/rollback.sql`을 실행하지 않습니다.

## 후속 작업

현재 회원 저장소는 Spring Data JPA의 파생 쿼리만 사용합니다. Kotlin JDSL 쿼리가 처음 필요한
작업에서 Spring Boot 4.0.7 호환 버전을 검증한 뒤 JDSL support 모듈을 다시 도입합니다.
