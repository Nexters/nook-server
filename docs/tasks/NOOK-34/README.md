# NOOK-34 그룹 색상 모델 및 그룹 CRUD API

## 목적

현재 사용자가 저장 게시물을 분류할 그룹을 생성하고 목록 조회·수정·삭제할 수 있게 합니다.

## 관찰 가능한 성공 기준

- 그룹명은 Figma `새 그룹 생성` 화면의 `n/20` 계약과 동일하게 1~20자로 저장됩니다.
- Figma에 표시된 8개 색상은 `YELLOW`, `CORAL`, `PINK`, `PURPLE`, `BLUE`, `MINT`, `GREEN`,
  `GRAY` 코드로 저장되고 응답됩니다.
- 같은 사용자는 같은 이름의 그룹을 만들 수 없고 다른 사용자의 그룹은 수정하거나 삭제할 수 없습니다.
- 목록과 생성·수정 응답은 그룹별 저장 게시물 수 `postCount`를 포함합니다.
- 그룹 삭제 시 `group_posts`는 기존 외래 키의 `ON DELETE CASCADE` 정책으로 함께 삭제됩니다.
- validation과 예상 가능한 오류는 공통 API 오류 계약으로 반환됩니다.
- 스키마와 JPA metadata가 일치하고 `./gradlew check`가 성공합니다.

## 범위

- `GET /api/v1/groups`
- `POST /api/v1/groups`
- `PATCH /api/v1/groups/{groupId}`
- `DELETE /api/v1/groups/{groupId}`
- 그룹 색상과 이름 길이 도메인 계약
- application use case와 persistence port
- JPA repository와 adapter
- MySQL 8.4 migration과 rollback DDL
- HTTP Client 예시와 OpenAPI 문서 및 테스트

## 제외 범위

- 그룹에 게시물 추가·제거 또는 그룹 간 이동
- 대표 미디어 조회
- 외부 공유 링크와 공동 편집

## 계약 결정

- 색상 hex는 UI 디자인 토큰이며 서버 저장 계약은 의미가 유지되는 대문자 색상 코드입니다. Figma에서
  확인한 첫 색상 `Yellow`의 현재 렌더 값은 `#FFD34E`이지만, 디자인 토큰 값 변경이 API/DB migration을
  유발하지 않도록 hex를 저장하지 않습니다.
- 대표 미디어는 미디어 저장소와 선택 정책에 의존하므로 이 이슈에서는 결정적인 집계값인 `postCount`만
  제공합니다.
- 그룹 목록은 생성 순서를 안정적으로 유지하기 위해 `user_groups.id ASC`로 정렬합니다.
- 존재하지 않거나 다른 사용자가 소유한 그룹은 소유 정보 노출을 막기 위해 모두 `GROUP_NOT_FOUND`로
  응답합니다.
- 이름은 앞뒤 공백을 제거한 뒤 저장하며 데이터베이스의 사용자별 유니크 제약을 최종 동시성 경계로
  사용합니다.

## DDL 적용

- MySQL 8.4 LTS에서 `ddl/up.sql`을 적용합니다.
- 기존 그룹은 `YELLOW` 기본값으로 안전하게 이행됩니다.
- 그룹명이 20자를 초과하는 기존 행이 있으면 `MODIFY COLUMN` 전에 정리해야 합니다.
- rollback은 색상 체크 제약과 칼럼을 제거하고 이름 길이를 100자로 복원합니다.

## 검증

- 도메인·application·persistence·controller 단위 테스트
- JPA entity metadata 테스트
- OpenAPI 문서화 정책 테스트
- `./gradlew check`
