# Tasks

작업 문서는 `docs/tasks/{ISSUE-ID}/README.md`에 작성합니다. 디렉터리 이름은 `NOOK-5`와 같은 이슈 번호를 사용합니다.

PR 제목은 `[ISSUE-ID] 요약` 형식을 사용하고, PR 본문은 해당 작업 문서의 내용을 기준으로 작성합니다.

DB 변경이 있을 때만 `ddl/up.sql`, `ddl/rollback.sql`을 추가합니다. 모든 작업 문서는 목적, 범위, 설계, 테스트, 배포 및 롤백 방법을 기록합니다.
