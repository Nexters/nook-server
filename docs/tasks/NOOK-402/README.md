# NOOK-402 장소 사진·태그 재처리

## 목적

사진 제공자가 사진을 반환하지 않으면 장소의 사진 처리 상태는 실패지만 게시글 후속 작업은 완료될 수 있다.
관리자가 게시글 전체를 다시 처리하지 않고 사진 없는 장소를 찾아 사진과 태그를 재처리할 수 있게 한다.

## 범위

- 장소 목록의 `사진 없음` 필터: 대표 이미지가 null/빈 문자열이고 추가 사진 배열도 비어 있는 장소.
- 장소 상세의 사진·태그 재처리 예약과 5초 간격 상태 조회. 예약 실패 시 입력한 사유를 유지한다.
- 기존 장소 정보 수정 화면에서 활성 대표 태그를 최대 4개 수동 선택한다. 재처리 후 편집창을 새로 열면 최신 값으로 초기화한다.
- 연결 게시글의 장소별 본문을 이용해 선택한 장소의 태그만 추출한다. 다른 장소의 문맥은 본문 구분에만 사용한다.
- 사진과 태그는 각각 결과를 표시한다. 수동 수정 감사 로그 및 실행 중 변경된 값을 보호한다.
- 요청 사유·관리자·request ID 감사 로그, 중복 예약 거부, 실행 회차를 통한 오래된 결과 차단.

## 제외 범위

영업시간 재조회·변경, 운영 서버 배포·DDL 적용, 게시글 전체 재파싱.
실제 외부 제공자를 호출하는 운영 검증은 수행하지 않았다.

## API와 처리

- `GET /api/admin/v1/places/manage?withoutPhotos=true`: 선택 필터, 기존 기본 조회 계약 유지.
- `POST /api/admin/v1/places/{placeId}/reprocessing`: 필수 `reason`(최대 500자), 비동기 예약.
- `GET /api/admin/v1/places/{placeId}/reprocessing`: `job`이 null이면 처리 이력이 없으며 존재하지 않는 장소는 404.
- 대기/처리 중 재요청은 409. 처리 상태는 PENDING, PROCESSING, COMPLETED, PARTIAL, FAILED.
- 사진·태그 결과는 PENDING, UPDATED, PRESERVED, NO_DATA, NO_SOURCE, FAILED.
- 사진 없음·태그 근거 없음은 PARTIAL로 표시한다. 사진 제공자 실패와 태그 추출 실패를 각각 표시한다.
- 워커는 5초마다 작업 한 건을 처리한다. 외부 호출은 DB 트랜잭션 밖에서 실행하며 결과 저장만 짧은 트랜잭션으로 수행한다.
- 15분 이상 멈춘 작업은 실패로 종료하고 관리자가 다시 예약할 수 있다. 이전 실행 회차의 결과는 저장하지 않는다.
- 수동 수정한 대표 태그는 재처리로 덮어쓰지 않는다. 태그 수동 수정 기능은 기존 API/선택 UI를 사용한다.

## 데이터베이스

`ddl/up.sql`은 MySQL 8.4용 `place_reprocessing_jobs` 테이블을 추가한다.
새 서버/워커가 실행되기 전에 DDL을 적용해야 한다. 기존 장소 데이터 backfill과 물리 FK는 없다.
`ddl/rollback.sql`은 실행 중인 워커를 중단한 뒤 사용하며 작업 이력만 삭제한다.
장소 사진·태그 및 감사 로그는 rollback 대상이 아니다. 운영 DB에는 적용하지 않았다.

## 검증 결과

- 전체 `./gradlew check`: 통과(Detekt, 테스트, 모듈 의존 방향 검사).
- 실행 환경: `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`, `--max-workers=2`, JVM `-Xmx2g -XX:MaxMetaspaceSize=1g`.
  기존 ParsingSummaryMySqlTest가 SQL 시각을 UTC로 기대하므로 UTC로 실행했다.
- MySQL 8.4 Testcontainers: 실제 up.sql, 동시 최초 예약, 중복 실행 차단, 만료 회차 차단, 원자적 rollback, 사진 없음 조회/총 건수 검증.
- 애플리케이션/API: 장소별 태그 본문 선택, 다른 장소 변경 차단, 제공자 실패, 수동 태그 보존하면서 사진 갱신, 요청 검증/404/409 검증.
- `pnpm --dir nook-admin-web build`: 통과. 기존 대형 번들 경고 있음.
- Chrome + 로컬 테스트 API: 사진 없음 필터, 재처리 예약/중복 버튼 비활성화/결과 표시, 최신 태그 편집 초기화와 수동 태그 저장 확인.
  실제 provider 호출 대신 테스트 응답으로 UI를 확인했다. 기존 UI 라이브러리의 DOM prop 경고(InputProps/TransitionProps/ContentProps)가 관찰됐다.
- `git diff --check`: 통과.

## 리뷰 상태

2026-10-08 사용자 검토 및 PR 진행 승인을 받았다. PR base는 main이며 NOOK-402 변경만 포함한다.
