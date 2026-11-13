# NOOK-430 장소 목록 정렬

## 목적

지도 바텀시트의 장소 목록과 저장 장소 검색에서 최신순·오래된순·가까운순을 지원한다.

## 범위

- `GET /api/v1/places/recent`: 선택 정렬 `sort=LATEST|OLDEST|NEAREST`. 기본값은 기존 최신순.
- `GET /api/v1/places/saved/search`: 선택 정렬 추가. 미지정 시 기존 관련도순을 유지한다.
- NEAREST는 사용자 GPS `latitude`·`longitude`가 모두 필수다. 좌표 범위와 짝을 검증하고,
  누락·잘못된 좌표 또는 커서와 다른 정렬·GPS에는 `PLACE_SEARCH_INVALID_REQUEST`를 반환한다.
- DB에서 기존 접근 권한·아카이브·검색 필터를 적용한 전체 집합을 정렬한 뒤 페이지를 나눈다.
- 최근 목록은 기존 cursor·totalCount·응답 필드를 유지한다. 최신순의 기존 3요소 커서는 계속 허용한다.
- 가까운순은 구면 거리와 단조 관계인 Haversine 값을 정렬 키로 사용한다. 같은 거리면 마지막 저장 시각
  내림차순, 같은 시각이면 북마크 ID 내림차순이다. 오래된순도 시각 동률이면 ID 내림차순이다.
- 가까운순 커서는 거리 정밀도를 보존하고 정렬·GPS 기준을 포함한다.
- 기존 `last_saved_at` 및 게시물 저장·장소 연결 시 갱신 로직을 재사용한다. DDL 변경 없음.
- 작업 착수 시 Linear 담당자·진행 상태를 변경하고 확인하는 규칙을 사용자 요청으로 AGENTS.md에 추가한다.

## 제외 범위

- 지도 핀 조회 범위·지도 중심·일반 지도 핀 정책 변경
- 검색의 기존 단일 소유 아카이브 필터를 다중·공유 필터로 확장
- 위치 권한 요청, GPS 획득, 토스트, 정렬 선택 UI
- 저장 시각 정책 및 공유 구독 북마크 생성 정책 변경

## 클라이언트 연동

- 정렬 UI의 기본값은 LATEST로 사용하고 검색에도 선택된 정렬을 명시한다.
- GPS는 가까운순 선택/시트 진입 시 확보하고 다음 페이지에도 같은 값을 전달한다.
- 위치 획득 실패/권한 거부 시 기존 정렬·목록을 유지하며 NEAREST 요청을 보내지 않는다.
- 정렬·GPS·아카이브·검색어 변경 시 cursor/page를 초기화한다.
- 목록은 지도 표시 영역과 무관한 기존 recent API 범위를 유지한다.

## 성공 기준 및 검증

- 전체 결과 기준 세 정렬, 같은 거리·시각의 안정적인 순서, 다음 페이지 중복·누락 방지.
- 아카이브/검색 조건, 공유 접근, 기존 최신순 커서 및 검색 관련도순 유지.
- GPS 누락·범위 오류·커서 기준 변경 거절.
- 기존 저장 시각 갱신·메모 보존 및 삭제·수정 경로 확인.
- 단위·H2 SQL·컨트롤러 테스트, Detekt, 모듈 의존 방향 검사.
- Docker/MySQL 컨테이너 테스트는 개인 검증 지침에 따라 제외한다. 검증 결과는 실행 후 기록한다.

## 검증 결과

- 2026-11-13: `./gradlew check -I /tmp/nook430-no-containers.gradle
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m' --max-workers=2` 성공.
- 임시 init script는 모든 Test task에서 `**/*MySqlTest*`를 제외한다. 저장소 기본 테스트 설정은 변경하지 않았다.
- 단위·H2·컨트롤러·기동 테스트 총 961건, 실패/오류/건너뜀 0건. Detekt와 모듈 의존 방향 검사 통과.
- 제외한 컨테이너 테스트: OnboardingMySqlTest, SavedPlaceSearchMySqlTest, PlaceReprocessingMySqlTest,
  EmptyPlaceCompletionMySqlTest, ParsingExecutionMySqlTest, ParsingSummaryMySqlTest, PostProcessingMySqlTest.
- MySQL 8.4 실실행, 실제 GPS/앱 UI 및 운영 배포는 검증하지 않았다.
- MySQL 검증이 가능한 환경에서는 `./gradlew :nook-api-infrastructure:test --tests '*SavedPlaceSearchMySqlTest'`
  및 세 정렬의 실제 API 페이지 연속 조회를 확인한다. 정렬·필터 기준의 전체 순서와 중복/누락 없음이 기대 결과다.
- `git diff --check` 통과.
