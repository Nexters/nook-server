# NOOK-215 공유 장소 상세 조회의 native EXISTS 반환 타입 오류 수정

## 목적

공개 공유 장소 상세 조회 시 MySQL 네이티브 쿼리 결과와 repository 반환 타입을 일치시켜
`Long`을 `Boolean`으로 변환하는 과정에서 발생하는 `ClassCastException`을 제거한다.

## 원인

`SELECT EXISTS (...)`는 MySQL Connector/J를 통해 `0` 또는 `1`인 `Long`으로 반환된다. 기존
`SharedGroupContentJpaRepository.existsPlaceInGroup`은 반환 타입을 `Boolean`으로 선언해 Spring Data
repository proxy가 실제 `Long` 결과를 `Boolean`으로 캐스팅하다 실패했다.

## 범위

- `existsPlaceInGroup` 반환 타입을 실제 네이티브 쿼리 결과인 `Long`으로 변경
- persistence adapter에서 양수를 장소 포함으로 명시적으로 판정
- 장소 포함 및 미포함 분기 회귀 테스트

## 제외 범위

- 공개 endpoint, request, response 및 오류 계약 변경
- 공유 장소 접근 정책 변경
- 데이터베이스 스키마와 DDL 변경

## 성공 기준

- 공유 그룹에 포함된 장소는 정상적으로 상세 조회할 수 있다.
- 공유 그룹에 포함되지 않은 장소는 기존 오류 계약에 따라 처리된다.
- 네이티브 쿼리 결과에 대한 `Long`에서 `Boolean` 캐스팅이 발생하지 않는다.
- `./gradlew check`가 통과한다.

## 검증

- `GroupSharePersistenceAdapterTest`
- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`

## 배포 및 롤백

스키마와 공개 API 계약 변경은 없다. 애플리케이션 배포만 필요하며 문제가 있으면 이전 버전으로
되돌린다.
