# NOOK-353 게시물별 실패 복구와 단계별 알림

## 목적과 범위

DB 유지보수로 바뀌는 수정 시각을 실패 시각으로 오인하지 않도록 실제 실패 시각을 별도로 저장한다.
같은 게시물의 본문·장소·미디어·썸네일·태그 작업을 한 묶음으로 보고 실패한 작업을 함께 재시도한다.
게시물 저장 요청 오류와 파싱 최종 실패를 환경별 alert-dev / alert-live에 전달한다.

- GET `/api/admin/v1/parsing-posts`: 게시물 ID 기준 커서 페이지. 상태 필터에 맞는 게시물을 고르고 그 게시물의 모든 단계를 반환한다.
- GET `/{postId}`: 단일 게시물 전체 작업과 실제 실패 시각/단계. 기존 후속 작업 API는 유지한다.
- POST `/{postId}/retry`: 사유 필수. FAILED 단계만 PENDING으로 바꾸고 감사 기록을 같은 트랜잭션에 저장한다. 중복 요청은 409.
- 본문/장소도 총 실행 번호는 보존하고 수동 재시도 예산만 초기화한다. 이전 실행의 결과 저장 보호는 유지한다.
- 화면은 실패 건수/완료 건수와 단계별 결과를 묶어 표시한다. 재시도 후 5초마다 게시물 전체를 조회하며 완료/재실패 시 조회를 종료한다. URL에 추적 게시물 ID를 보존한다.
- `last_failed_at`은 실제 실패 처리에서만 갱신한다. 수동 재시도·완료·DDL 변경에는 덮어쓰지 않는다. 과거 미기록은 NULL로 유지한다.
- 본문/장소 `execution_stage`는 진행률과 독립적으로 현재 실행 단계를 저장하고, 실패 시 `last_failure_stage`로 복사한다.
- DB 최종 실패 상태가 커밋된 뒤 구조화된 알림 로그 1건을 생성한다. stale 실행·롤백·자동 재시도 예약에는 최종 실패 알림을 생성하지 않는다.
- 워커 전용 전달기는 최종 실패 이벤트만 처리한다. API 저장 POST의 ERROR는 저장 요청 실패로 분류한다. 일반 API ERROR 전달은 기존 채널을 유지한다.
- 알림에는 환경, 게시물 ID, 단계, 실행 횟수, 실제 실패 시각, 복구 화면 링크가 포함된다. 저장 전 실패는 게시물 ID 미생성으로 표시한다. 원문 URL·본문·provider 응답은 전용 알림에 넣지 않는다.
- Discord 일시 오류/429는 최대 3회 시도하고 Retry-After를 최대 30초까지 반영한다. 메모리/로그 기반 전달이므로 장기 전송 장애·전달기 재시작 시 영구 전달 보장은 하지 않는다.

## 제외 범위

완료된 게시물 전체 재분석, 만료 원문 URL 재수집, 과거 실패 시각 추정, 상세 실패 이력 복원,
정상 빈 결과로 처리되는 provider 응답의 실패 의미 변경, 영속 알림 outbox는 포함하지 않는다.
통합 재시도는 기존 저장된 원문/장소 단서로 실패 단계만 다시 실행한다.

## 검증

- MySQL 8.4: 게시물별 페이지 경계, 상태 필터와 전체 단계 반환, 실패 단계 일괄 예약, 완료 단계 유지,
  감사 실패 전체 롤백, 중복 요청 거절, 실패 시각 보존 및 stale 실패 차단.
- 최종 실패 로그의 commit 전 미발송/rollback 미발송/commit 후 구조화된 단계 기록.
- API: 그룹 조회/ID·페이지·상태 검증, 인증 운영자와 사유 전달.
- 내장 브라우저 + 모의 API: 실패 7건 통합 대상 확인, 빈 사유 차단, 409 후 사유 유지,
  FAILED 필터에서 추적 유지, 8개 단계 완료/복구 구분, 새로고침 후 추적 유지. 390px에서 문서 가로 넘침 없음.
- `./gradlew check --no-parallel --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g` 통과.
- `pnpm --dir nook-admin-web build` 통과.
- `python3 -m unittest discover -s ops/error-log-forwarder -p 'test_*.py'` 통과.

## DDL과 배포 순서

1. `ddl/up.sql`은 MySQL 8.4 일회용이다. 구버전 worker 중지와 처리 중 작업 확인 후 적용한다.
2. 기존 실패 시각은 backfill하지 않는다. 본문/장소 retry_attempt_count만 기존 attempt_count로 채우며 updated_at을 보존한다.
3. 구버전 worker를 재개했다면 새 버전 최초 전환 직전 worker를 중지하고 본문/장소 두 테이블만
   `SET retry_attempt_count=attempt_count, updated_at=updated_at WHERE retry_attempt_count<>attempt_count`로 보정한다.
   새 버전 수동 재시도 이후 보정 UPDATE를 재실행하면 복구 이력을 지우므로 금지한다.
4. worker `.env`에 `PARSING_ALERT_DISCORD_WEBHOOK_URL`을 해당 환경의 기존 Discord alert webhook으로 설정한다.
   API exporters `.env`에도 같은 키를 설정한다. 기존 ERROR_LOG webhook은 유지한다.
5. worker 배포 스크립트는 공용 전달기 파일을 배포하고 전달기를 먼저 가동한 뒤 worker를 교체한다.
   API exporters의 공용 전달기/compose 변경도 별도 배포하고 전달기만 재생성한다.
6. dev 검증 후 live DDL 및 main 배포를 별도로 진행한다. 알림 테스트는 모의 실패/연결 테스트임을 명시한다.

롤백: worker 중지 → 이전 API/worker 배포 → 필요 시 `ddl/rollback.sql` 적용 → worker 재개.
신규 칼럼을 유지한 코드 롤백도 가능하지만 다시 신버전으로 올릴 때 수동 재시도 이력을 보존해야 한다.

## 적용 기록

- 2026-09-13 dev DDL 적용: 처리 중 0건 확인 후 worker 잠시 중지, up.sql 적용, 기존 worker 재개.
  본문 337건/장소 328건 retry 예산 일치, 과거 실패 시각 기록 0건(NULL), 신규 칼럼 확인.
- dev/live 기존 Discord alert webhook 연결을 확인하고 worker/exporters 환경에 설정했다. 토큰은 저장소에 포함하지 않는다.
- dev/live API 오류 전달기 코드·compose 갱신 및 전달기만 재생성 완료. 일반 ERROR 경로는 유지하고 저장 POST 오류를 alert 채널로 분기한다.
- live 파싱 worker 신버전 및 live DDL은 아직 미적용이다.
- dev 최종 배포: develop `8a64e159`, API/worker `dev-434-8a64e159`, [Actions 34744043331](https://github.com/Nexters/nook-server/actions/runs/34744043331) 성공.
  관리자 화면은 선행 [Actions 34743816968](https://github.com/Nexters/nook-server/actions/runs/34743816968)에서 배포했다.
- dev API/worker healthy 및 actuator UP, 재시작 0, Prometheus worker up=1, 파싱 전달기/관리자 running, 최근 5분 JSON ERROR 0건 확인.
- dev 최초 신버전 전환 직전 본문/장소 실행 횟수 보정: 수정 각각 0건. 이후 보정 UPDATE는 재실행하지 않는다.
- alert-dev / alert-live에 실제 장애가 아닌 연결 테스트 메시지를 각각 1건 전송, Discord HTTP 200 및 대상 채널 일치 확인.
- 최종 검증: JVM 테스트 741건 실패/오류/건너뜀 0건, Python 전달기 테스트 18건 통과. 실제 인증된 dev 관리자 세션에서 통합 재시도는 미실행이다.
- 장소 검색 실패 후 대체 제목 생성 성공이 실패 단계를 가리지 않도록 회귀 테스트로 확인했다.
