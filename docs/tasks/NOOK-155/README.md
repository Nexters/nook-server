# NOOK-155 그룹 게시물 카운트에서 콘텐츠 처리 실패 건 제외

## 목적

그룹 태그의 게시물 수와 그룹 상세에서 실제 조회 가능한 게시물 수가 일치하도록 집계 기준을 통일한다.

## 범위

- 그룹 목록의 `postCount`에서 삭제된 저장 게시물과 콘텐츠 파싱 상태가 `FAILED`인 게시물을 제외한다.
- 그룹 상세 게시물 목록과 `totalElements`에도 동일한 조건을 적용한다.
- 그룹 수정 응답의 `postCount`와 그룹 대표 썸네일 선택 기준도 동일하게 맞춘다.
- 집계 및 상세 조회 조건을 검증하는 테스트를 추가한다.

## 제외 범위

- 콘텐츠 파싱 실패 데이터 삭제 또는 재처리
- 장소 파싱 `FAILED` 게시물 제외
- DB 스키마 변경
- 클라이언트 코드 변경

## 성공 기준

- 모든 그룹에서 `postCount`와 그룹 상세의 `totalElements`가 일치한다.
- 콘텐츠 파싱 `FAILED` 게시물은 두 값과 그룹 대표 썸네일 대상에서 제외된다.
- 콘텐츠가 저장된 뒤 장소 파싱만 실패한 게시물은 기존처럼 조회 및 집계된다.
- 기존 API 요청·응답 형식은 변경되지 않는다.

## 검증

- `./gradlew :nook-api-infrastructure:test --tests '*GroupPersistenceAdapterTest' --tests '*SavedPostQueryPersistenceAdapterTest'`
- `./gradlew check`
