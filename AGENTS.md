# Nook Server Agent Guide

이 문서는 저장소 전체에 적용되는 작업 계약이다. 명시적인 사용자 요구가 이 문서보다 우선한다.
하위 디렉터리에 별도 `AGENTS.md`가 있으면 해당 범위에서는 더 가까운 문서가 우선한다.

## Project Structure

의존성은 항상 안쪽을 향해야 한다.

```text
nook-api-presentation -> nook-api-application -> nook-api-domain
nook-api-batch --------> nook-api-application -> nook-api-domain
nook-api-infrastructure -> nook-api-application, nook-api-domain
```

- `nook-api-domain`은 다른 프로젝트 모듈과 Spring, JPA, HTTP 타입에 의존하지 않는다.
- `nook-api-application`은 `nook-api-domain`에만 의존하며 유스케이스와 포트를 소유한다.
- `nook-api-infrastructure`는 application 포트를 구현하고 JPA 및 외부 provider 연동을 소유한다.
- `nook-api-presentation`과 `nook-api-batch`는 application을 호출한다. infrastructure는 런타임 조립에만 사용한다.
- 상세 기준은 `docs/architectures/module-structure.md`와
  `docs/adrs/ADR-0001-multi-module-architecture.md`를 따른다.

## Implementation Rules

### Use cases

- 유스케이스는 하나의 사용자 의도만 표현하고, 가능하면 공개 진입점을 `operator fun invoke` 하나로 제한한다.
- 하나의 유스케이스에 독립적인 공개 동작이 여러 개 필요하면 유스케이스를 분리한다.
- 프레임워크 타입을 유스케이스 입력, 출력 또는 application 포트에 노출하지 않는다.

### Persistence

- JPA entity는 `nook-api-infrastructure`에만 둔다.
- 모든 JPA entity는 `BaseEntity`를 상속해 `created_at`, `updated_at` 감사를 일관되게 적용한다.
- `@Table`에 테이블 이름을 명시하고, 필요한 인덱스와 유니크 제약을 이름까지 명시한다.
- 모든 영속 필드는 `@Column`으로 칼럼 이름과 `nullable`, 길이 또는 정밀도 등 적용 가능한 제약을 명시한다.
- ORM 기본 이름이나 자동 DDL에 의존하지 않는다. DB 변경은 `docs/policies/database.md`를 따른다.
- 스키마 변경이 있는 작업 이슈 디렉터리에는 `ddl/up.sql`, `ddl/rollback.sql`을 둔다.
- DDL은 MySQL 8.4 LTS 기준으로 작성한다.
- 테이블과 칼럼에는 `COMMENT`를 반드시 작성한다.
- 일반 인덱스 이름은 `idx_칼럼명`, 유니크 인덱스 이름은 `idx_u_칼럼명` 형식을 따른다. 여러 칼럼이면 칼럼명을 순서대로 이어 붙인다.

### Transactions and providers

- 외부 provider API 호출을 DB 트랜잭션 안에서 실행하지 않는다.
- provider 응답을 먼저 확보한 뒤, DB 변경만 담당하는 짧은 트랜잭션 경계에 전달한다.
- 원자성이 필요하면 긴 트랜잭션 대신 상태 저장, 멱등성, 재시도 또는 보상 동작을 설계한다.

### API compatibility

- 기존 endpoint, method, request/response 필드, 타입, 필수 여부, 상태 코드와 오류 의미를 유지한다.
- 응답 필드 추가는 기존 클라이언트가 무시할 수 있어야 하며, 요청 필드 추가는 선택값 또는 안전한 기본값이어야 한다.
- 호환성을 깨야 한다면 구현 전에 영향 범위와 migration/versioning 방안을 드러내고 사용자 결정을 받는다.
- 상세 기준은 `docs/policies/api.md`를 따른다.

## Working Agreement

1. 작업 전에 관련 코드, 테스트, 문서를 읽고 현재 동작을 근거로 삼는다.
2. 새 작업 이슈는 GitHub가 아니라 Linear에 생성하고, Linear 이슈 식별자를 브랜치/PR 맥락에 사용한다.
3. 구현 전에 사용자에게 작업 계획을 먼저 공유하고 확인을 받는다.
4. 구현 후 PR을 올리기 전에 변경 내용과 검증 결과를 사용자에게 먼저 리뷰받는다.
5. 요구사항을 추측해 메우지 않는다. 모호함이 결과나 공개 계약을 바꾸면 선택지와 영향을 명시하고 확인한다.
6. 시작 전에 관찰 가능한 성공 기준과 검증 방법을 정의한다.
7. 요구 범위를 충족하는 최소 코드와 최소 기능만 구현한다.
8. 필요한 파일과 라인만 수정한다. drive-by 리팩터링, 포맷 전용 변경, 무관한 이름 변경을 하지 않는다.
9. 변경 위험에 맞는 테스트를 추가하고 실패 원인을 수정한 뒤 성공 기준을 다시 검증한다.
10. 검증이 끝날 때까지 구현과 테스트를 반복한다. 검증하지 못한 항목은 완료로 표현하지 않는다.

## Verification

- 정적 분석과 포맷 검사: `./gradlew detekt`
- 테스트: `./gradlew test`
- 최종 검증: `./gradlew check` (Detekt, 테스트, 모듈 의존 방향)
- 커밋 전 hook 설치: `git config core.hooksPath .githooks`

pre-commit hook은 모든 커밋 전에 `./gradlew detekt`를 실행하며 실패한 커밋을 중단한다.
규칙을 우회하거나 baseline에 추가하는 대신 원인을 수정한다. 불가피한 예외는 범위를 최소화하고 이유를 코드에 남긴다.

## Pull Requests

- PR 제목은 `[ISSUE-ID] 요약` 형식을 사용한다. 예: `[NOOK-5] 프로젝트 초기 구성`.
- PR 본문은 `docs/tasks/{ISSUE-ID}/README.md`의 목적, 범위, 제외 범위와 검증 내용을 기준으로 작성한다.
- 하나의 PR은 하나의 이슈 범위만 다루며, 관련 없는 변경을 포함하지 않는다.
