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
