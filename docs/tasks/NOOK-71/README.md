# NOOK-71 Instagram 게시물 중복 저장 및 장소 파싱 실패 상태 개선

## 목적

같은 Instagram 원본을 전역 및 사용자별로 한 번만 저장하고, 장소를 확정하지 못한 작업이 빈 결과로
완료되지 않도록 저장 파이프라인의 멱등성과 진단 가능성을 개선한다.

## 범위

- Instagram URL의 shortcode를 외부 호출 전에 식별
- `(source_type, external_post_id)`가 같은 공용 게시물 재사용
- `(user_id, post_id)`가 같은 사용자 저장 게시물 재사용
- 재저장 시 기존 메모를 보존하고 새 그룹 연결만 추가
- 기존 게시물의 Bright Data, 제목 생성 및 미디어 업로드 생략
- 기존 장소 파싱 상태가 `COMPLETED`, `PENDING`, `PROCESSING`이면 기존 결과 재사용
- 기존 장소 파싱 상태가 `FAILED`이면 저장된 게시물 메타데이터로 작업 재시작
- 장소 단서가 비어 있거나 카카오 후보를 하나로 확정하지 못하면 실패 처리
- 최초 시도 후 3초 간격으로 최대 3회 재시도
- 재시도 중 마지막 실패 사유 영속화와 최종 실패 단계 구분
- 기존 중복 게시물과 사용자 저장 게시물 병합

## 제외 범위

- 공개 endpoint 및 request/response 필드 변경
- 카카오 외 장소 provider 도입
- 사용자 장소 후보 선택 기능
- 재저장 요청의 메모로 기존 메모를 덮어쓰는 동작

## 저장 정책

Instagram URL은 `p` 또는 `reel` 경로의 shortcode를 로컬에서 파싱한다. 기존 공용 게시물이 있으면
Bright Data를 호출하지 않고 저장된 메타데이터, 미디어, 장소 관계와 파싱 상태를 재사용한다.

같은 사용자의 저장 건이 이미 있으면 공개 `postId`도 기존 `user_saved_posts.id`를 유지한다. 요청한 그룹 중
아직 연결되지 않은 그룹만 추가하며 기존 메모는 변경하지 않는다.

`FAILED` 작업을 가진 게시물을 다시 저장하면 `attempt_count`와 실패 사유를 초기화하고 즉시 장소 파싱
이벤트를 발행한다. 나머지 상태에서는 중복 이벤트를 발행하지 않는다.

## 장소 파싱 정책

- 장소 단서가 없으면 `No place clue was extracted` 실패로 처리한다.
- 이름과 지역이 일치하는 카카오 후보가 없으면 `No place candidate matched`로 처리한다.
- 일치 후보가 여러 개면 `Multiple place candidates matched`로 처리한다.
- 재시도 시에도 마지막 실패 사유를 DB에 보존한다.
- 공개 API에는 최종 `FAILED` 상태에서만 실패 사유를 노출한다.
- 최초 시도를 포함해 최대 4회 실패하면 `FAILED`로 확정한다.

## 데이터 이관

`ddl/up.sql`은 같은 출처 식별자의 게시물 중 장소 파싱이 완료된 행, 연결 장소가 많은 행, ID가 작은 행
순서로 대표 게시물을 선택한다. 사용자 저장 건은 사용자와 대표 게시물별로 가장 작은 ID를 유지하고,
가장 먼저 작성된 비어 있지 않은 메모와 모든 그룹 연결을 보존한다. 중복 게시물의 장소 관계는 대표
게시물에 추가한 뒤 중복 행을 제거한다.

병합은 되돌릴 수 없으므로 `ddl/rollback.sql`은 유니크 인덱스만 제거한다. 운영 적용 전 백업이 필요하다.

## 성공 기준

- 같은 Instagram shortcode는 하나의 `posts` 행으로 유지된다.
- 같은 사용자의 동일 원본 저장은 하나의 `user_saved_posts` 행과 공개 `postId`를 유지한다.
- 기존 게시물 재저장 시 Bright Data, 제목 생성과 미디어 업로드가 실행되지 않는다.
- 기존 `FAILED` 장소 파싱 작업만 다시 시작된다.
- 장소가 한 건도 없는 파싱 작업은 `COMPLETED`가 되지 않는다.
- 최초 1회와 3초 간격 재시도 3회 후에도 실패하면 `FAILED`가 된다.
- `./gradlew check`가 성공한다.

## 검증

```shell
./gradlew detekt
./gradlew test
./gradlew check
```

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`
