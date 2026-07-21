# nook-server

취향 기반 장소 아카이빙 서비스, nook의 서버 애플리케이션입니다.

## 기술 스택

- Java 25
- Kotlin 2.4
- Spring Boot 4
- Spring Data JPA
- Kotlin JDSL
- MySQL 8.4 LTS
- Gradle Kotlin DSL

## 모듈

- `nook-api-domain`: 도메인 모델과 규칙
- `nook-api-application`: 유스케이스와 포트
- `nook-api-infrastructure`: JPA, Kotlin JDSL 및 외부 시스템 어댑터
- `nook-api-presentation`: HTTP API와 API 애플리케이션 진입점
- `nook-api-batch`: 배치 작업과 배치 애플리케이션 진입점

의존성 규칙은 [모듈 구조](docs/architectures/module-structure.md)를 참고합니다.

## 실행 준비

다음 환경변수가 필요합니다.

```shell
export DB_URL='jdbc:mysql://localhost:3306/nook?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
export DB_USERNAME='nook'
export DB_PASSWORD='nook'
```

API 애플리케이션을 실행합니다.

```shell
SPRING_PROFILES_ACTIVE=local ./gradlew :nook-api-presentation:bootRun
```

배치 애플리케이션을 실행합니다.

```shell
SPRING_PROFILES_ACTIVE=local ./gradlew :nook-api-batch:bootRun
```

## 검증

```shell
./gradlew clean test
```

## Docker

API 이미지:

```shell
docker build -t nook-api -f Dockerfile .
```

배치 이미지:

```shell
docker build -t nook-batch -f Dockerfile.batch .
```

실행 환경에서 `SPRING_PROFILES_ACTIVE`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 주입합니다.
