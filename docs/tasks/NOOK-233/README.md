# NOOK-233 FCM Admin SDK credential runtime 연결

## 목적

Firebase Admin SDK 서비스 계정 키를 dev/live API 런타임에 안전하게 마운트하고,
서버가 Application Default Credentials로 FCM을 사용할 수 있는 운영 설정을 준비한다.

## 범위

- dev API Docker Compose에 Firebase 서비스 계정 secret read-only mount 추가
- live Blue/Green Docker Compose 공통 설정에 Firebase 서비스 계정 secret read-only mount 추가
- 컨테이너 내부 credential 경로를 `/run/secrets/firebase-service-account.json`로 통일
- live 환경 변수 예시 문서에 `GOOGLE_APPLICATION_CREDENTIALS` 추가

## 제외 범위

- Firebase Admin SDK 의존성 추가 및 애플리케이션 코드 구현
- 푸시 토큰 등록 API 구현
- DB 스키마 변경
- Firebase 서비스 계정 JSON 커밋

## 성공 기준

- dev 컨테이너에서 `/run/secrets/firebase-service-account.json` 경로로 Firebase 서비스 계정 파일을 읽을 수 있다.
- live blue/green 컨테이너에서 동일 경로로 Firebase 서비스 계정 파일을 읽을 수 있다.
- 저장소에는 Firebase 서비스 계정 JSON 내용이 포함되지 않는다.
- dev/live compose config 검증이 통과한다.
- `./gradlew check`가 통과한다.

## 운영 메모

- dev VM secret: `/opt/nook/api/secrets/firebase-service-account.json`
- live VM secret: `/opt/nook/live/secrets/firebase-service-account.json`
- 컨테이너 내부 경로: `/run/secrets/firebase-service-account.json`

## 검증

- `docker compose -f ops/dev-runtime/api/compose.yml config`
- `docker compose -f ops/live-runtime/compose.yml --env-file <temp-env> config`
- `./gradlew check`
