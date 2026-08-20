# NOOK-249 웹 프론트엔드 HTTPS Origin CORS 허용

## 목적

웹 프론트엔드에서 API를 브라우저로 호출할 수 있도록 dev와 live HTTPS Origin을 CORS 허용 목록에
추가한다.

## 범위

- CORS 기본 허용 Origin에 `https://app-dev.everynook.co.kr` 추가
- CORS 기본 허용 Origin에 `https://app.everynook.co.kr` 추가
- 두 HTTPS 웹 Origin의 preflight 회귀 테스트 추가
- 두 호스트의 HTTP Origin이 허용되지 않는 회귀 테스트 추가
- 기존 localhost 및 서비스 웹 Origin 허용 유지

## 제외 범위

- `http://app-dev.everynook.co.kr` 및 `http://app.everynook.co.kr` 허용
- wildcard 서브도메인 허용
- nginx 및 Cloudflare 설정 변경
- 인증/인가 및 API 계약 변경
- S3 CORS 변경

## 성공 기준

- dev와 live HTTPS 웹 Origin의 preflight 요청이 인증 없이 성공한다.
- 같은 호스트의 HTTP Origin에는 CORS 허용 헤더가 반환되지 않는다.
- 기존 인증 정책과 허용 Origin이 유지된다.
- `./gradlew check`가 성공한다.

## 검증

```shell
./gradlew :nook-api-presentation:test --tests '*SecurityConfigTest' --no-daemon --no-build-cache
./gradlew check --no-daemon --no-build-cache
```

## DDL

스키마 변경은 없다.
