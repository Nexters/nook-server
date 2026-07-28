# 데이터베이스 정책

- MySQL 8.4 LTS를 기준으로 합니다.
- 애플리케이션의 Hibernate DDL 정책은 `validate`로 고정합니다.
- 애플리케이션과 배포 과정에서 DDL을 자동 실행하지 않습니다.
- DB 변경은 이슈 디렉터리의 `ddl/up.sql`, `ddl/rollback.sql`로 관리합니다.
- DDL은 반복 실행 가능 여부, 잠금 영향, 데이터 유실 가능성을 리뷰합니다.
- staging 검증 후 live에 적용하며 적용 일시와 실행자를 작업 문서에 기록합니다.
- local/dev와 staging/live가 서버를 공유하더라도 database와 계정은 분리하는 것을 원칙으로 합니다.

## 테이블 관계와 참조 무결성

- local, dev, staging, live를 포함한 모든 환경에서 물리적인 foreign key constraint를 사용하지 않습니다.
- 신규 테이블과 스키마 변경 DDL에 `FOREIGN KEY` 또는 참조 constraint를 작성하지 않습니다.
  과거 완료 이슈의 DDL은 당시 변경 이력으로 보존하며 새 정책에 맞추기 위해 다시 작성하지 않습니다.
- 테이블 관계는 `post_id`, `member_id`처럼 참조 대상이 드러나는 식별자 칼럼으로 표현합니다.
- 연관 식별자 칼럼에는 조회, 명시적 하위 데이터 삭제와 정합성 점검에 필요한 일반 인덱스를 둡니다.
  기존 unique 또는 복합 인덱스의 선두 칼럼이 같은 접근 경로를 제공하면 중복 인덱스를 만들지 않습니다.
- 애플리케이션은 하위 데이터의 존재 검증과 삭제 순서를 짧은 단일 DB 트랜잭션 안에서 명시적으로
  처리합니다. 데이터베이스 cascade 또는 constraint 오류를 업무 규칙으로 사용하지 않습니다.
- 비동기 처리나 외부 시스템 때문에 단일 트랜잭션으로 보장할 수 없는 관계는 멱등성, 재시도,
  상태 저장과 orphan 점검·정리 절차를 함께 설계합니다.
- JPA 연관 매핑에는 `foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT)`를 명시합니다.
  Hibernate DDL 정책이 `validate`여도 schema export나 테스트 도구가 물리 constraint를 만들지 않도록
  매핑 자체에 의도를 남깁니다.

## 감사 칼럼

모든 테이블은 `created_at`, `updated_at` 감사 칼럼을 포함합니다.

```sql
created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
```

- JPA entity는 `BaseEntity`를 상속합니다.
- `created_at`은 생성 이후 애플리케이션에서 변경하지 않습니다.
- `updated_at`은 JPA auditing과 데이터베이스의 `ON UPDATE CURRENT_TIMESTAMP(6)`를 함께 적용해
  애플리케이션 외부에서 변경된 경우에도 갱신합니다.
- DDL의 두 감사 칼럼에는 용도를 설명하는 `COMMENT`를 작성합니다.
