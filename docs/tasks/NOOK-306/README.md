# NOOK-306 아카이브 최근 저장순 정렬

## 목적

아카이브 목록에서 최근 게시물을 저장한 아카이브를 먼저 노출한다.

## 범위

- `GET /api/v1/groups`의 내 아카이브 정렬을 최근 저장순으로 변경한다.
- 저장 이력이 없는 아카이브는 최신 생성순으로 반환한다.
- 응답에 최근 저장 시각 `lastSavedAt`을 추가한다.

## 제외 범위

- 새 아카이브 생성 후 자동 선택 UI
- 사용자가 직접 아카이브 순서를 변경하거나 고정하는 기능
- DB 스키마 변경

## 검증

- `GroupJpaRepositoryQueryTest`로 최근 저장 시각과 생성 시각 기반 정렬 조건을 검증한다.
- `./gradlew :nook-api-infrastructure:test --tests 'org.every.nook.api.infrastructure.persistence.group.GroupJpaRepositoryQueryTest'`
- `./gradlew check`
