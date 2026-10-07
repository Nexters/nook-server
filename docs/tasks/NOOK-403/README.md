# NOOK-403 게시물 저장·파싱 Discord 운영 알림 3종 제거

## 목적

장소 없이 저장됨, 처리 완료 — 일부 실패, 게시물 저장 요청 실패의 Discord 운영 알림을 제거한다.

## 범위와 성공 기준

- `post.parsing.summary_warning`, `post.parsing.summary_failed`, `post.save.failed`는 Discord로 발송하지 않는다.
- 로그 수집 단계와 직접 발송 단계 모두에서 세 이벤트를 차단해 일반 ERROR 채널로의 우회 발송을 막는다.
- 게시물 저장 요청의 ERROR 로그는 기존 POST `/api/v1/posts`, POST `/api/v1/shared-posts/{id}/save`
  경로 인식으로 차단한다. HTTP 컨텍스트 키의 기존 별칭도 유지한다.
- 파싱 알림 메시지 구성과 전용 웹훅 라우팅을 제거한다.
- 기존 `ERROR_LOG_PARSING_ONLY=true` 전달기는 어떤 로그도 발송하지 않는다.
- 일반 전달기의 나머지 ERROR 로그, 요청 컨텍스트, Grafana 링크, Discord 전송 재시도는 유지한다.

## 제외 범위

사용자 FCM 푸시, 관리자의 장소 없음 경고, 파싱·재시도 동작, API 계약, DB 스키마는 변경하지 않는다.
요약 로그 수집과 fingerprint 기록도 유지한다. 이번 변경은 Discord 전달기 경계에 한정한다.
기존 Compose 서비스 및 웹훅 환경변수의 운영 정리는 포함하지 않는다.

## 검증

- Python unittest: 19건 통과. dev/live와 일반/파싱 전용 모드에서 세 이벤트의 HTTP 미발송 검증.
- 저장 API의 두 경로 및 HTTP 컨텍스트 키 별칭별 미발송 검증.
- 일반 오류 발송 유지, 실제 로그 수집 루프에서 제거한 이벤트 사이의 일반 오류 발송 검증.
- `git diff --check`: 통과.
- 전체 Gradle check: Detekt, 모듈 의존 방향, 테스트 통과.
  실행 명령: `TZ=UTC ./gradlew check --no-daemon -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' --max-workers=2`.
  최초 실행은 Gradle 데몬 메모리 부족으로 중단됐다. 메모리 증설 후 로컬 시간대에서는 기존
  `ParsingSummaryMySqlTest`의 legacy fingerprint 테스트가 실패했고, UTC 실행에서는 통과했다.
  이 테스트는 UTC 고정 시각과 DB timestamp를 비교한다. 관련 JVM 코드 및 테스트는 변경하지 않았다.

## 운영 적용

dev/live API ERROR 전달기와 worker의 파싱 전용 전달기에 새 `forward_error_logs.py`를 적용하고
전달기를 재시작해야 반영된다. 애플리케이션 배포나 DDL은 필요 없다.
원격 배포와 실제 Discord 발송은 실행하지 않았다.
