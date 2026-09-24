# NOOK-367 장소 카테고리 그룹 제공

## 목적

- 클라이언트가 장소 종류에 맞는 지도 아이콘을 선택할 수 있도록 장소 응답에 `categoryGroup`을 제공한다.
- 기존 `category` 계약은 변경하지 않는다.
- provider가 반환한 전체 카테고리 경로를 보존해 세부 그룹을 안정적으로 분류한다.

## 범위

- `places.provider_category`에 Kakao/Naver의 원본 카테고리 경로를 저장한다.
- 장소 검색, 지도, 최근 장소, 장소 상세, 저장 장소 검색, 그룹 장소 응답에 `categoryGroup`을 추가한다.
- 그룹 값은 `CAFE`, `SHOPPING`, `RESTAURANT`, `BAKERY`, `BAR`, `LODGING`, `TOURISM`, `ETC`다.
- 관리자 등록 등 원본 카테고리로 분류할 수 없는 장소는 `ETC`다.

## 기존 데이터 보강

DDL 적용 시 기존 `category`를 `provider_category`의 초기값으로 복사한다. 이 값만으로 식별 가능한 장소는 즉시
분류된다. 과거 저장 과정에서 `음식점 > 카페`처럼 하위 경로가 소실된 행은 완전한 복원이 불가능하므로,
배포 후 provider 식별자 기준 재조회 작업으로 원본 경로를 순차 보강한다. 재조회 전에는 현재 남아 있는 값으로
보수적으로 분류되며, 알 수 없는 값은 `ETC`로 응답한다.

## API 호환성

- 기존 endpoint, 요청, `category` 필드, 상태 코드는 유지한다.
- `categoryGroup`은 응답에만 추가되는 필드다.

## 검증

- 카테고리 키워드별 그룹 및 우선순위 단위 테스트
- Kakao/Naver 원본 카테고리 보존 테스트
- 장소 응답 매핑 테스트
- `./gradlew check`
