# NOOK-90 서비스 웹 호스트 CORS 기본 허용

## 목적

서비스 웹 프론트엔드 도메인에서 API 서버를 호출할 수 있도록 CORS 기본 허용 호스트에 `everynook.co.kr`을 포함한다.

## 범위

- CORS 기본 허용 origin에 서비스 웹 호스트 추가
  - `http://everynook.co.kr`
  - `https://everynook.co.kr`
- 환경변수 override에 의존하지 않고 애플리케이션 기본 코드 설정에 포함
- 서비스 웹 호스트 preflight 회귀 테스트 추가
- 기존 localhost 및 127.0.0.1 로컬 프론트엔드 허용 유지

## 제외 범위

- wildcard 운영 도메인 허용
- nginx CORS 정책 추가
- 인증/인가 정책 변경
- API request/response 계약 변경

## 검증

- `./gradlew :nook-api-presentation:test --tests '*SecurityConfigTest' --no-daemon --no-build-cache`
- `./gradlew check --no-daemon --no-build-cache`
