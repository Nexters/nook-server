# NOOK-127 내 정보 조회 API에 소셜 provider 응답 추가

## 목적

클라이언트가 사용자의 가입/로그인 소셜 provider를 판별할 수 있도록 내 정보 조회 API 응답에 `provider`를 포함합니다.

## 범위

- `GET /api/v1/members/me` 응답에 `provider` 추가
- 회원 기본 정보와 소셜 provider를 application 모델로 조회
- `social_accounts.provider` 기반 persistence adapter 조회 연결
- controller, use case, persistence adapter 테스트 추가
- HTTP Client 예시 갱신

## 제외 범위

- 소셜 계정 연결/해제 정책
- 다중 소셜 계정 지원 정책
- DB 스키마 변경

## 검증

- `./gradlew check`
