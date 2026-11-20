# NOOK-394 장소 카드·상세 정보 영업시간 미노출

## 목적

Apify Google Maps 수집 결과의 영업시간이 항상 null로 버려지는 문제를 수정해 기존 장소 상세 API의
`openingHours`, `openNow`를 복구한다.

## 확인한 원인

- `ApifyGoogleMapsPhotoProvider`가 Actor의 `openingHours`를 읽지 않고 항상 null을 반환했다.
- 네이버 사진 provider도 영업시간을 수집하지 않는다. 사진 fallback 체인은 기존 정책을 유지한다.
- DB `places.opening_hours` 및 상세 응답 매핑·현재 영업 여부 계산 경로는 이미 있다.
- nook-client를 읽기 전용으로 확인했다. `toPlaceDetail`은 두 필드를 보존하고 `PlaceDetail`의 `PlaceInfo`가
  영업 상태와 시간을 표시한다. 이번 작업에서는 클라이언트 코드를 변경하지 않는다.
- 실제 제보 장소의 DB·실행 환경·provider 응답은 조회하지 않았다. 운영 데이터 전체의 원인을 확정한 것은 아니다.

## 범위

- 기존 Actor 요청의 결과에 있는 요일별 `day`, `hours`를 구조화된 주간 영업 구간으로 변환한다.
- 국가 코드가 KR인 결과에만 `Asia/Seoul`을 적용한다. 다른 국가의 시간대는 임의로 추정하지 않는다.
- 7개 요일이 모두 있고 중복이 없으며 모든 시간이 해석 가능한 경우만 저장한다.
- 한국어 오전·오후, 영어 AM·PM, 24시간제, 휴무, 24시간 영업, 복수 영업 구간, 자정 넘김을 처리한다.
- 모호한 시간·누락된 요일·미지원 형식은 null을 유지한다. 현재 영업 상태를 추정하지 않는다.
- 사진이 없어도 영업시간을 반환하고, 이후 사진 fallback에서 영업시간이 유지되는 기존 경로를 사용한다.
- API 필드·타입·null 의미, DB 스키마, provider 호출 횟수와 설정을 유지한다.

## 제외 범위

- 외부 provider 실호출, Docker/MySQL 테스트, 브라우저 조작
- 네이버 영업시간 신규 수집, 해외 장소 시간대 추론, 공휴일·임시 영업시간 정책
- 기존 장소 일괄 재처리, DB 수동 수정, 배포 및 이슈 완료 처리

## 성공 기준

- 지원되는 주간 영업시간이 Actor 응답에서 보강 결과·장소 저장·상세 조회로 보존된다.
- 현지 시각 기준 영업 중/종료, 휴무, 브레이크타임, 자정 및 주 경계가 올바르게 계산된다.
- 정보가 없는 장소는 기존처럼 `openingHours=null`, `openNow=null`을 유지한다.
- 사진 수집·fallback은 회귀하지 않는다.

## 검증

- 수정 전: 영업시간을 null로 반환하는 기존 동작에서 신규 provider 회귀 테스트 실패 확인.
- 수정 후: 단위·H2 테스트 981개, 실패/오류/skip 0. 신규 회귀 테스트 7개 포함.
- Detekt(포맷 포함), 모듈 의존 방향 검사 통과. `git diff --check` 통과.
- 임시 Gradle init script로 `**/*MySqlTest.class` 7개 클래스를 제외한 `check` 성공.
  저장소 테스트 설정은 변경하지 않았다. 전체 MySQL 포함 check 통과로 해석하지 않는다.
- 제외 클래스: OnboardingMySqlTest, SavedPlaceSearchMySqlTest, PlaceReprocessingMySqlTest,
  EmptyPlaceCompletionMySqlTest, ParsingExecutionMySqlTest, ParsingSummaryMySqlTest, PostProcessingMySqlTest.
- 실행 명령: `./gradlew check -I /tmp/nook-394-no-containers.gradle --no-daemon --no-build-cache
  --max-workers=2 -Dorg.gradle.parallel=false '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m'`.
  init script는 모든 Test task에 `exclude '**/*MySqlTest.class'`만 적용했다.
- 초기 통합 실행은 Gradle 기본 512MiB heap의 GC thrashing으로 중단됐다.
  실행 옵션에서만 2GiB heap과 worker 2개를 사용해 재검증했다.
- 실제 provider 호출·개발/운영 DB·배포·브라우저 화면은 미검증.

## 배포 후 확인 절차

기존에 사진 수집이 완료된 장소는 자동 보강 대상이 아니므로 이번 코드 배포만으로 과거 null 값이 채워지지 않는다.
대표 제보 장소에 대해 개발 환경에서 기존 장소 보강/재처리 절차를 실행한 뒤
`GET /api/v1/places/{placeId}`의 두 필드 및 화면 표시를 확인해야 한다.
실행 전 대상 장소와 재처리 범위를 확인한다. 신규 수집에서도 같은 필드를 확인한다.
운영 재처리·배포는 별도 승인 후 수행한다.

## 참고

- [Linear NOOK-394](https://linear.app/nooknook/issue/NOOK-394)
- [Apify Actor 공식 출력 예시](https://apify.com/compass/crawler-google-places) (2026-11-20 확인)
