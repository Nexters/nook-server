# NOOK-125 DB 전체 장소 및 내 저장 장소 검색 API

## 목적

서비스 DB에 저장된 장소를 이름 또는 주소의 부분 일치로 검색한다. 전체 장소 검색과 로그인 사용자가 저장한 장소 검색을 별도 API로 제공한다.

## 범위

- `GET /api/v1/places/database/search`: DB 전체 장소 검색
- `GET /api/v1/places/my/search`: 현재 사용자가 저장한 장소 검색
- 장소명 또는 주소에 `LIKE %query%` 조건 적용
- 장소명과 장소 ID 오름차순 정렬
- page/size 기반 slice 응답
- 장소 기본 정보, 썸네일, 대표 태그와 사용자 저장 여부 응답
- Controller HTTP 예제와 계층별 테스트 추가

## 제외 범위

- 기존 외부 provider 장소 검색 API 변경
- 전문 검색 엔진 또는 n-gram 검색
- 데이터베이스 스키마 변경

## 검증

- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`
