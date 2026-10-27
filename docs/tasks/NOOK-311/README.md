# NOOK-311 지도 아카이브 선택 필터

## 목적

지도 핀과 최근 저장 공간 목록을 선택한 내·공유 아카이브로 제한한다.
Linear 댓글의 '최근 조회한 공간'은 기존 `GET /api/v1/places/recent`의 최근 저장 공간 목록을 대상으로 한다.

## 범위

- `/api/v1/places/map`, `/api/v1/places/recent`에 선택적 `groupIds` 쿼리 파라미터 추가
- 선택 아카이브의 합집합으로 조회하고 장소 중복 제거
- 조회 시 소유권·공유 구독·공유 링크 유효성·삭제 여부 확인
- 기존 지도 경계, 최근 저장 정렬 및 커서 형식 유지
- OpenAPI, HTTP 예제와 프런트 연동 문서 갱신

## 제외 범위

- 아카이브 선택 UI, 그룹별 선택 개수, 선택 상태의 세션 관리
- 아카이브 목록 API의 정렬·응답 변경
- 공유 정책, 장소 색상 정책 변경
- 푸시 알림·실시간 갱신 채널 도입
- DB 스키마 변경 (DDL 없음)

## 성공 기준 및 검증

- 미지정 시 기존 전체 조회 결과를 유지한다.
- 빈 선택은 지도 빈 목록과 최근 공간 빈 페이지를 반환한다.
- 내·공유 아카이브 다중 선택은 OR 조건이고 중복 장소를 한 번만 반환한다.
- 다른 사용자의 비공개 아카이브 및 삭제·공유 해제·만료·구독 해제된 아카이브는 제외한다.
- 필터 적용 후 최근 저장 순서로 페이지를 나누며 커서 다음 페이지의 누락·중복이 없다.
- MockMvc로 미지정·빈 값·다중 ID와 잘못된 입력의 HTTP 계약을 검증한다.
- H2 MySQL 모드에서 repository의 실제 조회 SQL로 권한·중복·경계·커서 동작을 검증한다.
- 단위 테스트, Detekt, 모듈 의존 검사를 수행한다. Docker/MySQL 컨테이너 테스트는 제외한다.

## 검증 결과

- 2026-10-21 KST: 관련 테스트 34개와 `./gradlew detekt` 통과.
- 컨테이너 테스트를 제외한 `check`: 테스트 913개, 실패 0개, 오류 0개.
  Detekt와 모듈 의존 방향 검사도 통과했다. 전체 무필터 `check` 통과를 의미하지 않는다.
- 실행 명령: `./gradlew check -I /tmp/nook-311-check.gradle --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m'`
- 임시 Gradle init script에서 모든 `Test` task에 `exclude '**/*MySqlTest*'`를 적용했다.
  저장소의 테스트 소스나 기본 Gradle 설정에는 제외 설정을 추가하지 않았다.
- 제외한 컨테이너 테스트: `OnboardingMySqlTest`, `PlaceReprocessingMySqlTest`,
  `SavedPlaceSearchMySqlTest`, `EmptyPlaceCompletionMySqlTest`, `ParsingSummaryMySqlTest`,
  `ParsingExecutionMySqlTest`, `PostProcessingMySqlTest`.
- 초기 실행은 Gradle 기본 메모리 부족으로 중단됐으며, 위 실행 옵션으로 해결했다.
- 실제 MySQL 및 브라우저 검증은 수행하지 않았다. 개발 서버에서 내·공유 아카이브 필터 요청을
  실행해 합집합·전체 해제·공유 해제 후 제외 동작을 확인하는 절차는 `client-handoff.md`에 기록했다.
- `git diff --check` 통과.

## 전체 장소 수 후속 요청 (2026-10-27)

Linear의 프런트 코멘트 요청에 따라 `GET /api/v1/places/recent` 응답에 `totalCount: Long`을 추가한다.
기존 items·hasNext·nextCursor와 입력·권한 정책은 유지하며 모든 페이지에 전체 수를 제공한다.

- 같은 groupIds 조건과 권한으로 접근 가능한 장소를 한 번씩 집계한다.
- 필터 생략 시 미분류 저장 장소도 포함한다. 빈 선택·조회 가능한 장소가 없으면 0이다.
- 커서·size·지도 bbox에 영향받지 않는다. 목록과 집계의 접근 조건 SQL을 공유한다.
- 기존 H2 SQL 회귀에서 선택 조합, 중복, 비공개·삭제·공유 해제·만료·구독 해제 제외와 집계를 함께 검증한다.
- 커서 다음 페이지와 지도 영역 밖 장소의 전체 수, 북마크·저장 접근 제거를 검증한다.
- 유스케이스와 MockMvc에서 전체 수 전달 및 기존 커서·페이지 계약 유지를 확인한다.

DDL·프런트 UI·실제 DB 변경은 없다. 단위·H2와 정적 검사로 검증하며 MySQL 컨테이너·브라우저는 실행하지 않는다.
배포 후 API 클라이언트를 갱신하고 실제 내·공유 아카이브로 totalCount와 페이지 간 값을 확인해야 한다.

### 후속 요청 검증 결과

- 단위·H2 테스트 951개 통과, 실패·오류·skip 0. 기존 회귀에 집계 검증을 추가하고 테스트 2개를 추가했다.
- Detekt, 모듈 의존 방향 검사, `git diff --check` 통과.
- 공유 조건을 추출한 최근 목록 SQL은 기존 쿼리와 공백 외 내용이 동일함을 확인했다.
- 위 7개 MySQL 컨테이너 클래스는 임시 init script로 제외했다. 실제 MySQL·브라우저·배포는 미검증이다.

```bash
./gradlew check --continue --init-script /tmp/nook-311-count-check.gradle --no-daemon \
  --max-workers=2 -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m'
```

초기 검증에서 테스트 fixture의 생성자 필드 위치와 저장소 함수 개수 제한을 수정했다.
기존 CRUD 저장소에 기능을 더 쌓는 대신 집계 전용 읽기 저장소를 두었으며 baseline이나 검사 규칙은 변경하지 않았다.
