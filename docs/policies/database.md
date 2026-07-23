# 데이터베이스 정책

- MySQL 8.4 LTS를 기준으로 합니다.
- 애플리케이션의 Hibernate DDL 정책은 `validate`로 고정합니다.
- 애플리케이션과 배포 과정에서 DDL을 자동 실행하지 않습니다.
- DB 변경은 이슈 디렉터리의 `ddl/up.sql`, `ddl/rollback.sql`로 관리합니다.
- DDL은 반복 실행 가능 여부, 잠금 영향, 데이터 유실 가능성을 리뷰합니다.
- staging 검증 후 live에 적용하며 적용 일시와 실행자를 작업 문서에 기록합니다.
- local/dev와 staging/live가 서버를 공유하더라도 database와 계정은 분리하는 것을 원칙으로 합니다.

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
