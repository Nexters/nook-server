# NOOK-106 장소 카테고리 최상위 분류만 저장

## 목적

카카오 장소 API의 계층형 카테고리 문자열을 서비스 표시 기준에 맞게 최상위 분류만 저장한다.

## 범위

- 카카오 `category_name`을 `>` 기준으로 분리해 첫 번째 분류만 `PlaceCandidate.category`에 매핑
- 공백/빈 문자열 정규화 유지
- mapper 테스트 보강
- 네이버 지오코딩 API는 현재 카테고리 필드가 없어 기존처럼 `category = null` 유지

## 제외 범위

- 기존 DB 데이터 마이그레이션
- 카테고리 코드/아이콘 매핑
- 네이버 검색 API 기반 카테고리 수집 전환

## 성공 기준

- `음식점 > 간식 > 제과,베이커리`가 `음식점`으로 매핑된다.
- 빈 카테고리는 `null`로 매핑된다.
- 네이버 장소 후보 카테고리 동작은 변경하지 않는다.

## 검증

- `./gradlew :nook-api-infrastructure:test --tests '*KakaoPlaceMapperTest' --tests '*NaverPlaceMapperTest' --no-daemon --no-build-cache`
