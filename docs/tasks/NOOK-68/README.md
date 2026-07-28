# NOOK-68 Swagger UI HTTPS 서버 URL 및 프록시 스킴 인식 보정

## 목적

dev 환경 Swagger UI의 Try it out 요청이 `http://api-dev.everynook.co.kr`로 생성되어 nginx 301 redirect 및
브라우저 CORS 실패로 이어지는 문제를 수정합니다.

## 범위

- Spring Boot가 reverse proxy의 `X-Forwarded-*` 헤더를 신뢰하도록 설정
- OpenAPI 문서에 환경별 HTTPS server URL 명시
- dev, staging, live 환경의 공개 API base URL을 HTTPS로 고정
- OpenAPI 설정 회귀 테스트 갱신

## 제외 범위

- API endpoint 계약 변경
- CORS origin 정책 변경
- nginx 및 배포 인프라 설정 변경

## 성공 기준

- dev OpenAPI 문서와 Swagger UI 요청 URL이 `https://api-dev.everynook.co.kr` 기준으로 생성됩니다.
- staging/live도 각각 HTTPS server URL을 문서화합니다.
- 관련 테스트가 통과합니다.

## 검증

- `./gradlew :nook-api-presentation:test`
- `./gradlew check`
