# NOOK-250 직접 장소 연결 썸네일 수집 비동기 전환

## 목적

저장 게시물에 장소를 직접 연결할 때 외부 썸네일 provider와 이미지 저장을 요청 스레드에서 기다리지
않도록 분리해 장소 연결 API의 응답 지연을 제거한다.

## 원인

`POST /api/v1/posts/{postId}/places`가 장소 연결 전에 Apify Google Maps actor를 동기 실행하고
반환 이미지를 스토리지에 저장한다. dev 요청 `e7fa91d2a8f84986`은 전체 21.763초 중 약 21.54초를
이 단계에서 사용했다.

## 범위

- 선택 토큰 검증 후 장소와 저장 게시물 연결을 짧은 DB 트랜잭션에서 저장
- 연결된 장소의 `thumbnail_parsing_status`를 `PENDING`으로 저장
- 같은 트랜잭션에서 기존 `PlaceThumbnailsRequestedEvent` 발행
- 커밋 후 기존 비동기 listener와 `StorePlaceThumbnailUseCase`로 썸네일 처리
- 성공 시 `COMPLETED`, 빈 결과 또는 실패 시 `FAILED` 상태 저장
- 직접 연결 application 및 persistence 회귀 테스트 갱신

## 제외 범위

- 공개 endpoint, 요청·응답 필드, 상태 enum, HTTP 상태 코드 변경
- 클라이언트 폴링 변경
- 썸네일 provider chain 정책 변경
- 신규 메시지 브로커 또는 재시도 큐 도입
- 데이터베이스 스키마 변경

## 설계

외부 provider 호출은 application의 장소 연결 유스케이스에서 제거한다. persistence adapter는 장소와
연결 정보를 저장하면서 썸네일 상태를 `PENDING`으로 만들고 이벤트를 발행한다. 기존
`@TransactionalEventListener(AFTER_COMMIT)`와 전용 executor가 이벤트를 처리하므로 provider 호출은
DB 트랜잭션 밖에서 실행된다.

클라이언트는 기존처럼 `GET /api/v1/posts/{postId}/place-parsing`을 폴링한다. 따라서 서버의 상태가
`PENDING → PROCESSING → COMPLETED/FAILED`로 수렴하면 추가 변경 없이 썸네일이 반영된다.

## 성공 기준

- 장소 연결 요청 스레드에서 썸네일 provider를 호출하지 않는다.
- 장소 연결과 `placeId` 반환은 외부 provider 완료를 기다리지 않는다.
- provider 호출은 트랜잭션 커밋 후 비동기 executor에서 실행된다.
- 썸네일 상태가 종료 상태로 수렴해 기존 클라이언트 폴링이 멈춘다.
- 기존 공개 API 계약을 유지한다.
- `./gradlew check`가 성공한다.

## 검증

- `./gradlew :nook-api-application:test --tests '*SearchPlacesUseCaseTest'`
- `./gradlew :nook-api-infrastructure:test --tests '*ConnectPostPlacePersistenceAdapterTest'`
- `./gradlew :nook-api-presentation:test --tests '*PlaceParsingEventListenerTest'`
- `./gradlew check`

## 배포 및 롤백

스키마와 공개 API 변경은 없다. 애플리케이션만 배포하며 문제가 생기면 이전 버전으로 롤백한다.
