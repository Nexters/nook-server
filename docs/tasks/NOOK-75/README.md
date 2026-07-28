# NOOK-75 로컬 프론트엔드 CORS 호출 실패 수정

## 목적

프론트엔드 로컬 개발 서버에서 API 서버 호출 시 CORS preflight가 실패해 게시물 생성 등 인증 API
호출을 진행할 수 없는 문제를 수정한다.

## 범위

- Spring Security 레벨에서 CORS 설정 활성화
- `http://localhost:*`, `http://127.0.0.1:*` 로컬 프론트엔드 origin 허용
- 인증이 필요한 API의 preflight 요청을 인증 없이 허용
- 허용 origin 패턴을 환경변수로 조정할 수 있도록 설정
- CORS preflight 회귀 테스트 추가

## 제외 범위

- 운영 도메인 CORS 정책 확장
- nginx 또는 인프라 설정 변경
- API 인증 및 인가 정책 변경
- 공개 API 요청과 응답 계약 변경

## 성공 기준

- 허용된 로컬 origin의 preflight 요청이 성공한다.
- 허용되지 않은 origin에는 CORS 허용 헤더를 반환하지 않는다.
- 보호 API의 인증 정책은 유지된다.
- `./gradlew check`가 성공한다.

## 검증

```shell
./gradlew :nook-api-presentation:test --tests org.every.nook.api.config.SecurityConfigTest
./gradlew check
```

## DDL

스키마 변경은 없다.
