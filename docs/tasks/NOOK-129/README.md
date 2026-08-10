# NOOK-129 장소 대표 도시 정보 추출 및 저장

## 목적

카카오·네이버에서 확정한 장소 주소로부터 지도 필터와 지역 표시에 사용할 대표 도시명을 추출해 장소에 저장한다.

## 범위

- `places.city` 컬럼 추가
- 광역자치단체와 시·군 주소를 대표 도시명으로 정규화
- 자동 장소 확정과 사용자 수동 장소 연결 모두 신규 장소 저장 시 도시명 적용
- 기존 장소 주소 기반 데이터 보정 DDL과 rollback 작성
- 국내 주요 주소 형식 및 파싱 실패 테스트 추가

## 제외 범위

- LLM을 이용한 지역 추론
- 해외 주소 정규화
- 장소 API 응답 필드 추가
- 도시별 검색·필터 API 추가

## 성공 기준

- 신규 국내 장소에 정규화된 대표 도시명이 저장된다.
- 기존 장소의 도시명이 주소 기준으로 보정된다.
- 파싱할 수 없는 주소는 장소 저장을 막지 않고 `NULL`로 저장된다.
- DDL/rollback과 애플리케이션 구현이 일치한다.
- `./gradlew check`가 통과한다.

## 검증

- `./gradlew :nook-api-domain:test --tests '*KoreanCityNameExtractorTest'`
- `./gradlew :nook-api-infrastructure:test --tests '*PlaceParsingPersistenceAdapterTest' --tests '*ConnectPostPlacePersistenceAdapterTest'`
- `./gradlew check`
