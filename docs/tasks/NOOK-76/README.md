# NOOK-76 Instagram 게시물 저장 전체 파이프라인 비동기 처리

## 목적

Instagram 게시물 저장 요청에서 외부 provider와 미디어 저장 호출을 제거하고, 사용자의 저장 의도와
그룹 연결을 먼저 영속화한 뒤 게시물 콘텐츠와 장소를 복구 가능한 백그라운드 작업으로 처리한다.

## 범위

- 공용 게시물 placeholder, 사용자 저장 관계, 그룹 관계와 콘텐츠 파싱 작업을 짧은 트랜잭션으로 저장
- Bright Data 콘텐츠 추출, 제목 생성과 S3 미디어 저장을 비동기 콘텐츠 파싱 작업으로 이동
- 콘텐츠 파싱 작업의 상태, 시도 횟수, 다음 시도 시각과 실패 사유 영속화
- 콘텐츠 파싱 완료 후 기존 장소 파싱 작업 생성 및 실행
- API 시작 시 미완료 작업 복구와 주기적 DB dispatcher
- 동일 Instagram 원본의 콘텐츠 작업 공유와 사용자별 저장 멱등성 유지
- 생성, 저장 게시물 목록·상세와 그룹 게시물 목록에 통합 처리 상태 추가
- 기존 장소 파싱 endpoint와 상태 의미 유지

## 제외 범위

- 메시지 브로커
- 모바일 앱 UI, polling과 푸시 알림
- Instagram 이외의 신규 게시물 provider
- 기존 endpoint 또는 요청 필드 제거

## API 계약

`POST /api/v1/posts`는 기존 `201 Created`를 유지한다. 응답받은 `postId`는 콘텐츠 파싱 중에도
목록과 상세 조회에 사용할 수 있다.

생성, 저장 게시물 목록·상세와 그룹 게시물 목록 응답에 다음 필드를 추가한다.

- `processingStatus`: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`
- `processingStage`: `CONTENT`, `PLACE` 또는 전체 완료 시 `null`

기존 `placeParsingStatus`는 콘텐츠 처리 중이거나 장소 작업 생성 전에는 `PENDING`으로 반환한다.
장소 처리 상태와 결과 조회 endpoint는 그대로 유지한다.

## 상태 전이

```text
CONTENT/PENDING -> CONTENT/PROCESSING -> PLACE/PENDING
                                      -> CONTENT/FAILED
PLACE/PENDING   -> PLACE/PROCESSING   -> COMPLETED
                                      -> PLACE/FAILED
```

콘텐츠 파싱 작업과 장소 파싱 작업은 각각 최초 시도를 포함해 최대 4번 실행한다. API 프로세스가
재시작되거나 이벤트가 유실돼도 DB에 남은 `PENDING` 및 timeout된 `PROCESSING` 작업을 복구한다.

## 성공 기준

- 게시물 생성 응답 전에 Bright Data, OpenAI, S3와 장소 provider를 호출하지 않는다.
- 콘텐츠 파싱 중에도 저장 게시물 ID로 목록과 상세를 조회할 수 있다.
- 콘텐츠 완료 전에는 장소 파싱이 실행되지 않는다.
- 프로세스 재시작, 이벤트 유실과 처리 timeout 후 작업이 복구된다.
- 동일 Instagram 원본은 하나의 공용 콘텐츠 작업만 실행한다.
- 장소 파싱만 실패한 경우 완성된 게시물 콘텐츠는 정상 조회된다.
- 외부 provider 호출은 DB 트랜잭션 안에서 실행되지 않는다.
- `./gradlew check`가 성공한다.

## 설정

```text
POST_CONTENT_PARSING_RETRY_BACKOFFS=3s,3s,3s
POST_CONTENT_PARSING_PROCESSING_TIMEOUT=15m
PARSING_DISPATCHER_INTERVAL=10s
```

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`

MySQL 8.4 LTS 기준이다. 코드 배포 전에 dev DB에 적용하고 테이블, 칼럼과 인덱스를 검증한다.

## 적용 기록

- dev: 2026-07-29, Codex가 `nook` 데이터베이스에 `ddl/up.sql` 적용
- 검증: 기존 게시물 10건의 콘텐츠 작업이 모두 `COMPLETED`로 이관됐고 누락된 작업은 0건

## 검증

```shell
./gradlew detekt
./gradlew test
./gradlew check
```
