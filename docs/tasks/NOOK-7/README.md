# NOOK-7 게시물·장소 공용 모델 및 사용자 저장 모델 구현

## 목적

게시물과 장소의 공용 데이터를 사용자별 저장 데이터와 분리해 동일 게시물의 스크래핑 및 분석 결과를
재사용할 수 있는 모델을 구성합니다. 게시물 출처는 Instagram에 한정하지 않고 확장 가능한 식별자로
표현합니다.

## 범위

- 출처와 외부 게시물 식별자로 유일한 공용 게시물 모델
- 위도와 경도를 필수로 갖는 공용 장소 모델
- 게시물과 장소의 다대다 관계
- 사용자별 저장 게시물, 메모, 그룹 및 그룹 게시물 관계
- domain model과 JPA entity 분리
- MySQL 8.4 기준 생성 및 롤백 DDL
- 도메인 불변식과 JPA 매핑 테스트
- `created_at`, `updated_at` 데이터베이스 기본값 컨벤션 적용

## 제외 범위

- Instagram 및 다른 외부 provider 스크래핑
- AI 기반 장소 분석과 장소 후보 모델
- API endpoint 및 application 유스케이스
- spatial index와 거리 기반 검색
- 사용자별 장소 태그와 별칭

## 성공 기준

- 같은 출처의 같은 외부 게시물은 데이터베이스에서 중복 생성되지 않습니다.
- 출처를 문자열 값 객체로 표현해 새로운 게시물 provider를 추가할 수 있습니다.
- 하나의 게시물에 여러 장소를 순서대로 연결할 수 있습니다.
- 공용 게시물·장소와 사용자별 메모·그룹 데이터가 분리됩니다.
- 동일 사용자가 같은 공용 게시물을 여러 번 저장할 수 있습니다.
- 동일 사용자는 같은 이름의 그룹을 중복 생성할 수 없습니다.
- 장소는 유효한 위도와 경도를 필수로 가집니다.
- JPA 매핑과 DDL의 테이블명, 칼럼명 및 유니크 제약이 일치합니다.
- `./gradlew check`가 성공합니다.

## 설계

### 공용 데이터

- `posts`는 `(source_type, external_post_id)`로 외부 게시물을 유일하게 식별합니다.
- `places`는 `(provider, external_place_id)`로 지도 provider의 장소를 유일하게 식별합니다.
- `post_media`는 게시물의 미디어와 노출 순서를 관리합니다.
- `post_places`는 게시물과 장소의 다대다 관계 및 게시물 내 장소 노출 순서를 관리합니다.

게시물 출처와 장소 provider는 enum이 아닌 문자열로 저장합니다. 새로운 provider를 추가할 때 기존 공용
모델과 스키마를 변경하지 않기 위한 선택입니다. 서로 다른 provider의 장소를 하나로 병합하는 기능은
이번 범위에서 다루지 않습니다. 출처와 provider 코드는 대문자와 밑줄 형식으로 정규화하고, 외부 ID는
대소문자를 구분하는 binary collation으로 비교합니다.

### 사용자 데이터

- `user_saved_posts`는 사용자가 공용 게시물을 저장한 사실과 개인 메모를 관리합니다.
- `user_groups`는 사용자가 만든 게시물 그룹을 관리합니다.
- `group_posts`는 공용 게시물이 아니라 `user_saved_posts`를 참조해 사용자 소유 경계를 유지합니다.

동일 사용자는 같은 공용 게시물을 여러 번 저장할 수 있습니다. 각 저장 건은 독립적인 메모와 그룹
구성을 가지며 하나의 저장 게시물을 여러 그룹에 포함할 수 있습니다. 그룹 이름은 사용자 안에서
유일합니다.

### 좌표

확정된 `Place`는 `DECIMAL(10, 7)` 위도와 경도를 필수로 가집니다. 위도는 -90부터 90, 경도는
-180부터 180 사이여야 하며 domain과 DDL에서 모두 검증합니다. 좌표를 찾지 못한 장소 후보는 확정
장소와 분리하며 이번 범위에 포함하지 않습니다.

## 검증

```shell
./gradlew detekt
./gradlew test
./gradlew check
```

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`

모든 테이블은 다음 감사 칼럼 정의를 사용합니다.

```sql
created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
```

## 배포 및 롤백

1. staging에서 `ddl/up.sql`을 적용합니다.
2. 테이블, 인덱스, 외래 키, 감사 칼럼 기본값을 확인합니다.
3. 애플리케이션의 Hibernate `validate`가 성공하는지 확인합니다.
4. live 적용 일시와 실행자를 이 문서에 기록한 뒤 `ddl/up.sql`을 적용합니다.

아직 데이터가 없는 초기 스키마를 전제로 합니다. 롤백이 필요하면 `ddl/rollback.sql`을 실행하며,
이 스크립트는 생성된 7개 테이블과 그 안의 데이터를 모두 삭제합니다.

### 적용 기록

- staging: 미적용
- live: 미적용
