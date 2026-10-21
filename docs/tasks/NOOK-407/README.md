# NOOK-407 아카이브 소개글

## 목적

아카이브 생성·편집 시 선택 소개글을 저장하고 내 아카이브 및 공유 아카이브 조회에서 원문을 반환한다.
이슈: https://linear.app/nooknook/issue/NOOK-407
요청 코멘트: https://linear.app/nooknook/issue/NOOK-407#comment-58f14ac5

## 범위와 API 계약

- 기존 `POST /api/v1/groups`, `PATCH /api/v1/groups/{groupId}`에 선택 `description: string | null` 추가.
- 생성 시 생략/null/빈 문자열/공백만 입력하면 소개글 없이 생성한다.
- 수정 시 생략/null은 기존 값 유지, 빈 문자열/공백만 입력하면 삭제한다. 기존 이름·색상 필수 계약은 유지한다.
- 앞뒤 Unicode 공백은 trim하며 내부 공백·줄바꿈·이모지는 보존한다.
- 사용자에게 보이는 글자(grapheme cluster, Java 정규식 `\X`) 기준 최대 150자. 내부 줄바꿈도 1자로 센다.
- 초과 입력은 HTTP 400 `INVALID_REQUEST`로 거절하고 저장하지 않는다.
- 생성·수정 응답, 내/저장 가능한 그룹 목록 및 공개 공유 정보의 `GroupResponse`에 `description`을 추가한다.
- 미입력 소개글은 null이다. 기존 직렬화 정책에 따라 null 필드가 생략될 수 있다.
- 공유받은 목록·공유 링크 조회는 원본의 최신 소개글을 읽는다. 기존 소유자 검증을 유지하므로 수신자는 편집 불가다.
- 변경 시 소유 그룹을 잠그는 짧은 트랜잭션에서 이름·색상·소개글을 함께 갱신한다.

## 제외 범위

클라이언트 입력창·글자 수 표시·입력 제한, 실제 렌더링 3줄 말줄임과 더보기·접기,
저장 실패 시 입력값 보존은 클라이언트 구현 범위다. 새로운 상세 API나 탐색 API는 추가하지 않는다.

## 클라이언트 연동 안내

생성·수정의 기존 body에 `description`을 추가한다. 소개글을 지우려면 `"description": ""`을 보낸다.
이름·색상만 수정하는 이전 클라이언트는 필드를 생략해도 기존 소개글을 보존한다.
조회 응답의 소개글은 자르지 않은 원문이므로 화면 너비 기준 3줄 여부와 펼침 상태는 화면에서 처리한다.
글자 수는 UTF-16 길이 대신 grapheme segmentation을 사용하고, trim 후 150자까지 허용한다.
서버 저장 성공 응답으로 화면을 갱신하고 실패 시 기존 소개글과 편집 입력값을 유지한다.

## 성공 기준과 검증

- 선택 소개글의 생성·수정·삭제와 이름·색상만 수정하는 요청의 호환성.
- 150/151자, 가족 이모지·국기·피부색·결합 문자, 내부 줄바꿈 및 trim 검증.
- 소유자 외 수정 차단과 공유 조회의 최신 원문 반환.
- 단위/MockMvc/H2 테스트, Detekt, 모듈 의존 검사.
- Docker/MySQL 컨테이너 테스트·앱 화면 조작은 이번 로컬 검증에서 제외한다.
- 2026-10-21 KST 검증 완료: 아래 Docker 제외 `check` 성공. 테스트 916개, 실패/오류/스킵 0개.
- 모듈별: domain 25, application 327, infrastructure 397, presentation 150, batch 4, worker 13.
- Detekt 자동 수정 없이 통과, `verifyModuleDependencies` 통과, `git diff --check` 통과.
- 실행: `./gradlew check -I /tmp/nook407-no-docker.gradle --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g'`.
- 임시 init script는 `allprojects { tasks.withType(Test).configureEach { exclude '**/*MySqlTest*' } }`이며 저장소 설정은 바꾸지 않았다.
- 제외한 Docker 테스트 클래스: `OnboardingMySqlTest`, `SavedPlaceSearchMySqlTest`, `PlaceReprocessingMySqlTest`,
  `ParsingSummaryMySqlTest`, `ParsingExecutionMySqlTest`, `EmptyPlaceCompletionMySqlTest`, `PostProcessingMySqlTest`.
- MySQL 8.4 DDL 적용·스키마·행 수 검증은 아래 기록과 같이 완료했다. 실제 MySQL을 사용하는 앱 통합 및 화면 동작은 검증하지 않았다.

## DDL 및 적용 순서

`ddl/up.sql`은 `user_groups.description` nullable TEXT(utf8mb4)를 추가한다.
조합 이모지는 코드 포인트/UTF-16 길이가 150을 초과할 수 있어 VARCHAR(150)을 사용하지 않는다.
기존 행은 null로 유지되며 추가 인덱스/foreign key는 없다. INSTANT DDL도 metadata lock을 기다릴 수 있다.
반복 실행할 수 없으므로 환경별 칼럼 유무 확인 → 개발 적용·검증 → 운영 적용·검증 → 앱/워커 배포 순서를 따른다.
정상 구동에 이 칼럼이 필요하므로 DDL 적용 전에 새 앱/워커를 배포하지 않는다.
롤백은 이전 코드 복귀 후 `ddl/rollback.sql`로 수행하며 소개글 데이터가 유실된다.

| 환경 | 적용 일시 | 실행자 | 결과 |
| --- | --- | --- | --- |
| 개발 | 2026-10-21 23:15:00 KST | Codex / `nook` (사용자 승인) | nullable TEXT·utf8mb4·COMMENT 확인, 128→128행, 기존 128행 null |
| 운영 | 2026-10-21 23:15:03 KST | Codex / `nook_admin` (사용자 승인) | nullable TEXT·utf8mb4·COMMENT 확인, 496→496행, 기존 496행 null |

### 실제 DB 적용 시 확인 방법

적용 전후 아래 조회로 nullable TEXT·utf8mb4와 행 수 유지 여부를 확인한다.
이후 테스트 아카이브 생성 → 소개글 수정 → 이름·색상만 수정 → 소개글 삭제 → 공유 조회 순으로 API를 확인한다.
기대 결과는 원문/이모지/줄바꿈 보존, 필드 생략 시 기존 값 유지, 삭제 시 null, 수신자의 수정 시 기존 404다.

```sql
SELECT column_name, data_type, is_nullable, character_set_name, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'user_groups'
  AND column_name = 'description';
SELECT COUNT(*) FROM user_groups;
```

### develop 통합 검증

NOOK-377의 추가한 사람 정보와 소개글을 함께 보존해 충돌을 해결했다.
2026-10-21 KST 동일 Docker 제외 `check` 성공: 935개, 실패/오류 0/0.
