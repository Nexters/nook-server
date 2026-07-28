# NOOK-80 물리 Foreign Key 제거 및 DB 정책 명문화

## 목적

모든 환경에서 테이블 관계를 논리적으로 관리하고 물리적인 foreign key constraint를 사용하지 않도록
저장소 정책과 JPA 매핑을 정리합니다. dev DB에 남은 기존 foreign key를 제거하되, 데이터베이스
`ON DELETE CASCADE`에 의존하던 그룹 삭제 동작은 애플리케이션에서 명시적으로 유지합니다.

## 범위

- 저장소 작업 지침과 데이터베이스 정책에 물리 foreign key 금지 원칙 추가
- 연관 식별자 칼럼의 인덱스와 애플리케이션 참조 무결성 책임 명시
- JPA `@JoinColumn`에 `ConstraintMode.NO_CONSTRAINT` 적용
- `refresh_tokens.replaced_by_token_id` 논리 관계 인덱스 명시
- 그룹 삭제 트랜잭션에서 `group_posts`를 먼저 삭제
- 현재 database의 모든 물리 foreign key를 제거하는 반복 실행 가능한 MySQL 8.4 DDL 추가
- 기존 스키마로 긴급 복구할 수 있는 rollback DDL 추가
- dev DB 적용 전 orphan 점검과 적용 후 foreign key 부재 검증

## 제외 범위

- unique index와 관계 조회에 필요한 일반 인덱스 제거
- staging 및 live DB 적용
- 공개 API endpoint, 요청·응답과 오류 계약 변경
- 과거 완료 이슈 DDL의 역사적 내용 재작성
- 현재 제공되지 않는 회원, 게시물 또는 장소 삭제 기능 추가

## 동작 보완

기존 그룹 삭제는 `fk_group_posts_group`의 `ON DELETE CASCADE`에 의존했습니다. 물리 foreign key를
제거한 뒤에도 같은 API 의미를 유지하도록, 소유권을 확인한 다음 같은 트랜잭션에서
`group_posts` 연결 행과 `user_groups` 행을 순서대로 삭제합니다.

다른 cascade 대상에는 현재 부모 행을 삭제하는 공개 동작이 없습니다. 이후 삭제 기능을 추가할 때는
`docs/policies/database.md`에 따라 하위 행 삭제 또는 보존 정책을 유스케이스와 트랜잭션에 명시해야 합니다.

## DDL

- `ddl/up.sql`은 현재 database의 `FOREIGN KEY` constraint를 `information_schema`에서 조회해 모두
  제거하므로 이미 적용된 환경에서도 반복 실행할 수 있습니다. FK가 자동 생성했던
  `refresh_tokens.replaced_by_token_id` 인덱스는 `idx_replaced_by_token_id`로 변경합니다.
- `ddl/rollback.sql`은 NOOK-80 적용 직전의 알려진 13개 constraint를 복원합니다. 정책까지 되돌리는
  비상 상황에서만 사용하며, 실행 전 orphan 데이터가 없는지 다시 확인해야 합니다.
- foreign key 제거는 InnoDB metadata lock을 획득합니다. dev 트래픽이 적은 시간에 실행하고 장시간
  트랜잭션이 없는지 확인합니다.

## 성공 기준

- dev DB에서 적용 전 모든 foreign key 관계의 orphan 개수가 0입니다.
- dev DB `information_schema.TABLE_CONSTRAINTS` 조회 결과 `FOREIGN KEY` 개수가 0입니다.
- 그룹 삭제가 연결 행을 먼저 삭제하고 기존 성공·실패 의미를 유지합니다.
- 모든 JPA 연관 매핑이 물리 foreign key 생성을 명시적으로 비활성화합니다.
- `./gradlew check`가 통과합니다.

## 검증 및 적용 기록

- 로컬 검증: 2026-07-29 `./gradlew check` 성공
- dev 적용 일시: 실행 후 기록
- 실행자: 권기준
- 적용 전 foreign key: 13개
- 적용 전 orphan 점검: 13개 관계 모두 0건
- 적용 후 foreign key: 실행 후 기록
