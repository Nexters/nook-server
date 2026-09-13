# NOOK-350 관리자 후속 작업 복구

## 목적과 범위

파싱 파이프라인 화면에서 미디어·썸네일·태그 작업의 상태와 최근 실패 사유를 조회하고 실패한 작업만 재시도한다.

- GET `/api/admin/v1/parsing-jobs`: 게시글/상태 필터, ID 내림차순 커서, 최대 100건.
- POST `/api/admin/v1/parsing-jobs/{jobId}/retry`: 사유 필수, FAILED 상태만 허용, 중복 요청 409.
- 기존 관리자 인증을 사용하고 감사 로그를 예약 변경과 같은 트랜잭션에 기록한다.
- 총 실행 번호를 유지하면서 현재 재시도 예산만 초기화한다.
- 화면에서 대상과 영향 범위 확인, 제출 중 중복 클릭 방지, 오류 시 사유 보존 및 성공 안내.

## 제외 범위

콘텐츠/장소 본체 재파싱, 전체 재분석, 외부 호출 비용 환급, 자동 일괄 복구는 포함하지 않는다.
썸네일 공급자가 오류 없이 빈 결과를 반환한 경우 기존 완료 의미를 유지하므로 작업 실패 목록에는 나타나지 않는다.

## DDL 및 배포

MySQL 8.4 기준 `ddl/up.sql`은 일회용이다. 반복 실행하면 칼럼 중복 오류가 발생한다.
새 칼럼은 기존 worker와 호환되지만 backfill 중 변경과 충돌하지 않도록 worker를 중지한 뒤 적용한다.

1. dev에서 worker를 중지하고 현재 처리 중인 작업을 확인한다.
2. `up.sql` 적용 및 칼럼/건수 검증. ALTER는 metadata lock, UPDATE는 대상 행 잠금 및 로그 증가를 유발한다.
3. API와 worker를 새 버전으로 배포하고 worker 재개. 관리자 조회·실패 작업 재시도·감사 기록 확인.
4. staging 검증 후 live에 같은 순서로 적용한다. 실행자·적용 시각을 아래 기록한다.

롤백: worker 중지 → 이전 API/worker 배포 → `rollback.sql` 적용 → worker 재개.
칼럼 삭제는 현재 재시도 예산 정보를 잃지만 작업 payload와 총 실행 횟수는 유지한다.
dev DDL 적용: 2026-09-13 14:25 KST, 실행자 Codex (사용자 요청). dev worker 중지 후 nook-dev-mysql의 nook DB에서 up.sql 실행. 칼럼 타입/기본값/COMMENT 및 기존 53건의 backfill 일치 확인. staging/live 미적용.

## 검증

MySQL 테스트에서 실패 작업 재시도, 중복 거절, 소유권 번호 유지, 감사 실패 시 롤백을 확인한다.
관리자 UI 빌드 통과. 사용자 요청으로 내장 브라우저를 사용해 로컬 모의 API의 빈 사유 차단, 실패 시 사유 유지, 성공 후 목록 갱신, 상태 필터 및 좁은 화면을 검증했다. 지연 응답 중 중복 제출 차단과 다음 페이지/처음으로 복귀도 확인했다. 실제 서버와 연결한 브라우저 통합은 미검증이다.

## live DDL 선행 적용

- 2026-09-13, 사용자 요청으로 live MySQL 8.4.8의 `nook.parsing_follow_up_jobs`에 적용.
- 기존 worker 중지 후 PROCESSING 0건 확인. `ddl/up.sql` 실행 후 2,427건 모두 두 실행 횟수가 일치하고 NULL 0건임을 확인.
- 칼럼 int / NOT NULL / 기본값 0 / COMMENT 확인. 기존 worker `prod-426-7ece8dde` 재개. 앱 배포 및 PR 병합은 수행하지 않음.
- **새 버전 배포 직전** 기존 worker를 중지하고 PROCESSING 상태를 확인한 뒤, 구버전 실행 중 증가분을 `UPDATE parsing_follow_up_jobs SET retry_attempt_count = attempt_count WHERE retry_attempt_count <> attempt_count;`로 맞추고 새 API/worker를 배포한다. ALTER는 다시 실행하지 않는다.
- 이 보정 UPDATE는 새 버전 최초 배포 전에만 사용한다. 새 버전에서 수동 재시도한 이후 실행하면 복구 이력을 지우므로 실행하지 않는다.
