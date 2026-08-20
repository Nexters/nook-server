# NOOK-264 API 컨테이너 이미지 빌드 시간 단축

## 목적

API 컨테이너 이미지 빌드에서 Gradle 캐시를 재사용하고 불필요한 Docker 빌더 캐시 업로드를 제거해 배포 피드백 주기를 단축한다.

## 현재 측정값

2026-08-21 직전 개발 배포 기준 `Build and Push API` 작업은 약 4분 41초가 소요됐다.

- Docker 내부 Gradle `bootJar` 빌드: 약 2분 35초
- GitHub Actions Docker 빌드 캐시 내보내기: 약 1분 46초
- 이미지 푸시: 약 14초

## 범위

- GitHub Actions의 Gradle 캐시를 사용하는 `bootJar` 빌드
- 빌드된 JAR만 포함하는 경량 런타임 이미지 생성
- 런타임 이미지 레이어만 저장하는 Docker 캐시
- 기존 로컬 멀티 스테이지 Docker 빌드 흐름 유지

## 제외 범위

- Slack 배포 알림
- 배포 및 롤백 스크립트 변경
- 애플리케이션 기능과 API 계약 변경

## 성공 기준

- 생성된 이미지의 사용자, 포트, JVM 옵션과 실행 방식이 기존 이미지와 동일하다.
- 후속 GitHub Actions 실행에서 Gradle 및 Docker 캐시가 재사용된다.
- 일반적인 소스 변경 시 `Build and Push API` 실행 시간이 기준값인 4분 41초보다 단축된다.

## 검증

- `./gradlew check`
- `./gradlew :nook-api-presentation:bootJar`
- `docker build -f Dockerfile.runtime nook-api-presentation/build/libs`
- 컨테이너 기동 후 `/actuator/health` 확인
- 적용 전후 GitHub Actions 실행 시간 비교
