# NOOK-5 프로젝트 초기 구성

## 목적

Nook 서버의 멀티모듈 구조, 실행 환경, DB 연결 및 컨테이너 빌드 기반을 구성합니다.

## 범위

- Java 25, Kotlin, Spring Boot, Gradle 기반 구성
- `nook-api-presentation`, `nook-api-application`, `nook-api-domain`, `nook-api-infrastructure`,
  `nook-api-batch` 모듈 분리
- local, dev, staging, live 프로필
- API 및 배치 Dockerfile
- 프로젝트 문서 정책

## 제외 범위

- Docker Compose
- Flyway 및 자동 DDL 실행
- 실제 업무 도메인과 API

## 검증

- `./gradlew clean test`
- `docker build -t nook-api -f Dockerfile .`
- `docker build -t nook-batch -f Dockerfile.batch .`
