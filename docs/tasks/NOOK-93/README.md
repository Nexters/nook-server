# NOOK-93 장소 북마크 접근성 조회 타입 불일치 수정

## 목적

장소 북마크 변경 시 접근성을 확인하는 MySQL 네이티브 쿼리 결과와 repository 반환 타입을 일치시켜
`Long`을 `Boolean`으로 변환하는 과정에서 발생하는 `ClassCastException`을 제거한다.

## 원인

`SELECT EXISTS (...)`는 MySQL Connector/J를 통해 `0` 또는 `1`인 `Long`으로 반환된다. 기존
`UserPlaceBookmarkJpaRepository.isAccessible`은 반환 타입을 `Boolean`으로 선언해 Spring Data
repository proxy가 실제 `Long` 결과를 `Boolean`으로 캐스팅하다 실패했다.

## 범위

- `isAccessible` 반환 타입을 실제 네이티브 쿼리 결과인 `Long`으로 변경
- persistence adapter에서 `0L`을 접근 불가로 명시적으로 판정
- 접근 가능 및 접근 불가능 분기 회귀 테스트
- infrastructure의 다른 네이티브 Boolean 반환 쿼리 점검

## 제외 범위

- 공개 endpoint, request, response 및 오류 계약 변경
- 장소 접근 정책 변경
- 데이터베이스 스키마와 DDL 변경

## 성공 기준

- 접근 가능한 장소의 북마크 설정과 해제가 정상 처리된다.
- 접근 불가능한 장소는 변경하지 않는다.
- 네이티브 쿼리 결과에 대한 `Long`에서 `Boolean` 캐스팅이 발생하지 않는다.
- infrastructure에 같은 형태의 네이티브 Boolean 반환 쿼리가 남아 있지 않다.
- `./gradlew check`가 통과한다.

## 검증

- `PlaceBookmarkPersistenceAdapterTest`
- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`

## 배포 및 롤백

스키마와 공개 API 계약 변경은 없다. 애플리케이션 배포만 필요하며 문제가 있으면 이전 버전으로
되돌린다.
