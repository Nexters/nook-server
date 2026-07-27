# nook-server

취향 기반 장소 아카이빙 서비스, nook의 서버 애플리케이션입니다.

## 기술 스택

- Java 25
- Kotlin 2.4
- Spring Boot 4
- Spring Data JPA
- MySQL 8.4 LTS
- Gradle Kotlin DSL

## 모듈

- `nook-api-domain`: 도메인 모델과 규칙
- `nook-api-application`: 유스케이스와 포트
- `nook-api-infrastructure`: JPA 및 외부 시스템 어댑터
- `nook-api-presentation`: HTTP API와 API 애플리케이션 진입점
- `nook-api-batch`: 배치 작업과 배치 애플리케이션 진입점

의존성 규칙은 [모듈 구조](docs/architectures/module-structure.md)를 참고합니다.

## 실행 준비

다음 환경변수가 필요합니다.

```shell
export DB_URL='jdbc:mysql://localhost:3306/nook?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
export DB_USERNAME='nook'
export DB_PASSWORD='nook'
export KAKAO_REST_API_KEY='your-kakao-rest-api-key'
export BRIGHT_DATA_API_TOKEN='your-bright-data-api-token'
export JWT_ACCESS_SECRET='replace-with-at-least-32-byte-secret'
export JWT_REFRESH_SECRET='replace-with-at-least-32-byte-secret'
export KAKAO_APP_ID='your-kakao-app-id'
export GOOGLE_CLIENT_ID='your-google-web-client-id'
export APPLE_CLIENT_ID='your-apple-service-or-bundle-id'
export APPLE_TEAM_ID='your-apple-team-id'
export APPLE_KEY_ID='your-apple-key-id'
export APPLE_PRIVATE_KEY='your-pkcs8-private-key'
```

장소 후보 검색에는 카카오 디벨로퍼스 애플리케이션의 REST API 키를 사용합니다.

Instagram 게시물·릴스 수집 dataset ID는 Bright Data 공식 기본값을 사용합니다. 계정에서 별도 dataset을
사용하는 경우 `BRIGHT_DATA_POSTS_DATASET_ID`, `BRIGHT_DATA_REELS_DATASET_ID`로 재정의합니다.

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

실행 환경에서 `SPRING_PROFILES_ACTIVE`, DB 설정, JWT 비밀키와 사용하는 소셜 provider 설정을 주입합니다.
