# NOOK-419 장소 미연결 게시물 인앱 안내 서버 지원

## 목적

내 아카이브의 장소 0개 게시물을 찾아 수동 장소 연결로 안내하는 배너에 필요한 서버 API를 제공한다.

## 범위

- `GET /api/v1/me/place-connection-reminder`: `enabled`, `unlinkedPostCount`, `showBanner` 조회.
- `PATCH /api/v1/me/place-connection-reminder`: 필수 boolean `enabled` 변경 후 현재 상태 반환.
- `GET /api/v1/posts/unlinked-places?page=0&size=20`: 기존 게시물 목록 응답 형식으로 대상 목록 조회.
- 배너 대상은 enabled=true이고 대상 수가 1개 이상인 경우다. OFF여도 대상 수·목록은 조회 가능하다.
- 사용자 확인에 따라 최초 기본값은 ON. 설정 행이 없으면 true로 읽고 변경 시에만 저장한다.
- 리마인드 설정은 기존 저장 완료 푸시 설정과 별도 테이블·API로 관리한다.
- 내 소유의 삭제되지 않은 아카이브에 현재 포함된 내 저장 게시물만 집계한다.
- 여러 아카이브에 같은 게시물이 있어도 사용자별 고유 저장 게시물 기준 한 번만 집계한다.
- 실제 사용자별 연결 장소가 0개인 게시물만 포함한다. 다른 사용자의 장소 연결은 영향을 주지 않는다.
- 콘텐츠 수집 대기·처리 중·실패 건은 제외한다. 장소 분석은 종료(COMPLETED/FAILED) 상태만 포함한다.
  이는 기존 화면이 장소 분석 FAILED도 게시물 처리 완료로 다루고 수동 연결을 허용하는 동작을 따른다.
- 독립 저장한 공유 복사본(place_links_frozen)은 원본 재처리 상태와 무관하게 자체 연결 상태로 판단한다.
- 읽기 전용 공유 아카이브에만 있는 게시물, 삭제된 저장 게시물·아카이브·소속 관계는 제외한다.
- 저장 최신순, 동률이면 저장 게시물 ID 내림차순으로 DB에서 필터 후 페이지를 나눈다.
- 조회마다 현재 상태를 읽어 장소 연결/삭제·아카이브 제거·재처리 상태를 즉시 반영한다.
- 기존 API 계약과 응답은 유지하며 새 endpoint만 추가한다.

## 제외 범위

- 푸시 발송, 배너 UI/위치, 닫기·재노출·주간 제한, 알림센터 이력, 게시물별 건너뛰기/영구 제외.
- 미확정 UI 정책에 필요한 표시 이력 테이블이나 API는 추가하지 않는다.

## 프론트 연동

1. 앱 진입·복귀 시 리마인드 GET 호출. showBanner로 배너 대상 여부, unlinkedPostCount로 문구를 구성한다.
2. CTA에서 미연결 목록 조회. 응답 postId는 기존 장소 연결 API에 사용하는 저장 게시물 ID다.
3. 장소 연결·삭제 후 리마인드와 목록을 재조회한다. 대상이 0개면 showBanner=false다.
4. 설정 토글은 리마인드 PATCH로 저장한다. OS 알림 권한·기존 푸시 설정과 연동하지 않는다.
5. 목록 화면 상세 진입·배너 닫기/빈도 정책은 기획·디자인 확정 후 프론트에서 반영한다.

## DDL 및 롤백

- MySQL 8.4 기준 `ddl/up.sql`로 user_place_reminder_preferences 테이블을 신규 생성한다.
- 사용자별 unique 인덱스·감사 칼럼·주석을 포함하며 물리 FK는 없다. 기존 행 backfill은 필요 없다.
- 앱 배포 전에 환경별 DDL 적용이 필요하다. 개발은 아래 기록대로 적용했고 운영은 아직 미적용이다.
- rollback.sql은 앱 롤백 후 실행하며 저장한 설정이 삭제돼 추후 재도입 시 기본 ON으로 돌아간다.

## 성공 기준 및 검증

- 처리 중·공유 전용·삭제·장소 연결 완료 제외 및 다중 아카이브 중복 제거를 실제 H2 SQL로 검증한다.
- 사용자 격리, frozen 복사본, 재처리, 장소 연결/제거 시 목록과 개수의 동시 갱신을 확인한다.
- 설정 초기 ON 및 반복 ON/OFF 저장, 사용자별 분리, 누락/null/잘못된 boolean·페이지 입력 거절을 검증한다.
- 컨테이너를 제외한 check(Detekt·단위/H2/컨트롤러·모듈 의존 검사)를 실행한다.
- MySQL 8.4·앱 UI·실제 환경 DDL 적용은 별도 검증 대상이다.

## 검증 결과

- 2026-11-13: `./gradlew check -I /tmp/nook430-no-containers.gradle
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m' --max-workers=2` 성공.
- 임시 init script는 모든 Test task에서 `**/*MySqlTest*`를 제외한다. 저장소 기본 테스트 설정은 유지했다.
- 단위·H2·컨트롤러·기동 테스트 964건, 실패·오류·건너뜀 0건. Detekt와 모듈 의존 방향 검사 통과.
- 제외: OnboardingMySqlTest, SavedPlaceSearchMySqlTest, PlaceReprocessingMySqlTest,
  EmptyPlaceCompletionMySqlTest, ParsingExecutionMySqlTest, ParsingSummaryMySqlTest, PostProcessingMySqlTest.
- `git diff --check` 통과. MySQL 8.4 실제 실행·앱 UI 및 환경별 DDL 적용/배포는 미실행이다.
- 새 테이블을 Hibernate validate가 요구하므로 develop 자동 배포를 유발하는 푸시 전에 개발 DDL을
  적용해야 한다. 운영도 이 변경을 배포하기 전에 같은 DDL을 선행 적용해야 한다.


## DDL 적용 기록

- 2026-11-13: 백도현 승인으로 Codex가 개발 DB `1.201.120.75:3806/nook`(MySQL 8.4.11)에
  `ddl/up.sql`을 적용했다. 적용 전 대상 테이블이 없음을 확인했다.
- 칼럼·기본값·감사 칼럼·주석·사용자 유니크 인덱스, 물리 FK 0개 및 초기 행 0건을 확인했다.
- 정확한 적용 시각 및 SHOW CREATE 결과는 `ddl/dev-verification.txt`에 기록했다.
- 운영 DDL은 미적용이며 운영 배포 전에 별도 승인이 필요하다.
