# NOOK-70 장소 파싱 API 비동기 이벤트 처리 전환

## 목적

별도 batch worker 없이 API 애플리케이션이 게시물 저장 후 장소 파싱 작업을 비동기로 처리하고,
일시 장애나 프로세스 재시작에도 작업이 영구 정체되지 않게 한다.

## 범위

- 게시물과 장소 파싱 작업이 저장된 트랜잭션의 커밋 후 비동기 이벤트 처리
- API 시작 시 남아 있는 `PENDING` 및 timeout된 `PROCESSING` 작업 복구
- 최초 처리 실패 후 5초, 15초, 45초 간격으로 최대 3회 재시도
- `attempt_count`, `next_attempt_at` 영속화
- `mu.KotlinLogging` 기반 파이프라인 진행 로그
- batch 모듈의 장소 파싱 scheduler 제거

## 제외 범위

- 별도 batch 애플리케이션 배포
- 메시지 브로커 도입
- 공개 API 계약 변경
- OCR 또는 다른 장소 provider 추가

## 성공 기준

- API 프로세스만 실행해도 신규 장소 파싱 작업이 비동기로 처리된다.
- 외부 provider 호출은 게시물 저장 트랜잭션 밖에서 실행된다.
- 최초 시도를 포함해 최대 4번 처리한 뒤 성공하거나 `FAILED`로 확정된다.
- API 재시작 후 남은 작업이 backoff 또는 processing timeout 시점부터 재개된다.
- post ID, attempt, OpenAI 장소 단서, Kakao 후보 수, 확정 장소와 최종 상태를 로그로 추적할 수 있다.
- 기존 endpoint, request/response와 상태 의미가 유지된다.

## 설정

```text
PLACE_PARSING_RETRY_BACKOFFS=5s,15s,45s
PLACE_PARSING_PROCESSING_TIMEOUT=1m
```

## 검증

```shell
./gradlew detekt
./gradlew test
./gradlew check
```

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`

MySQL 8.4 LTS 기준이며 dev DB에 적용한 뒤 애플리케이션을 배포한다.

## 적용 기록

- dev: 2026-07-28, Codex가 사용자 승인하에 `ddl/up.sql` 적용 및 칼럼·인덱스 검증
