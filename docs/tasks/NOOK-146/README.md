# NOOK-146 Spring Boot 4 Jackson ObjectMapper 타입 불일치 수정

## 목적

Spring Boot 4가 자동 구성하는 Jackson 3 `ObjectMapper`와 infrastructure 컴포넌트가 요구하는
Jackson 2 `ObjectMapper`의 타입 불일치로 API가 기동하지 못하는 문제를 해결한다.

## 원인

Spring Boot 4.0.7의 기본 JSON 자동 구성은 `tools.jackson.databind.ObjectMapper`를 제공한다.
그러나 장소 및 게시물 파싱 persistence adapter를 포함한 infrastructure JSON 처리 코드는
`com.fasterxml.jackson.databind.ObjectMapper`를 사용했다. 두 타입은 서로 호환되지 않아
Spring ApplicationContext가 컴포넌트 생성 단계에서 실패했다.

## 범위

- infrastructure의 Jackson databind 및 Kotlin module 사용을 Jackson 3 패키지로 통일
- infrastructure와 presentation의 직접 Jackson Kotlin module 의존성을 Jackson 3 좌표로 변경
- Jackson 3의 `JsonNode.map` 멤버와 Kotlin 컬렉션 확장 함수 충돌을 명시적인 리스트 변환으로 해소
- API 전체 ApplicationContext가 Jackson 3 mapper와 파싱 persistence adapter를 함께 생성하는 회귀 테스트 추가
- 기존 persistence 및 외부 provider JSON 처리 테스트 갱신

## 제외 범위

- 공개 endpoint, request, response 및 오류 계약 변경
- DB 스키마와 저장 JSON 형식 변경
- Springdoc이 내부적으로 사용하는 Jackson 2 전이 의존성 제거

## 성공 기준

- API 전체 ApplicationContext가 정상 기동한다.
- `PlaceParsingPersistenceAdapter`와 `PostContentParsingPersistenceAdapter`가 자동 구성된 Jackson 3
  `ObjectMapper`를 주입받는다.
- application 코드에 Jackson 2 databind 또는 Kotlin module 직접 사용이 남지 않는다.
- 기존 JSON 직렬화 및 역직렬화 테스트와 `./gradlew check`가 통과한다.

## 검증

- `./gradlew :nook-api-presentation:test --tests '*NookApiApplicationContextTest' --no-daemon --no-build-cache`
- `./gradlew :nook-api-infrastructure:test --no-daemon --no-build-cache`
- `./gradlew clean test --no-daemon --no-build-cache`
- `./gradlew check --no-daemon --no-build-cache`

## 배포 및 롤백

DB 및 공개 API 변경 없이 애플리케이션 이미지만 교체한다. 문제가 발생하면 이전 애플리케이션 이미지로
롤백한다.
