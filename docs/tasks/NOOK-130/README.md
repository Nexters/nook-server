# NOOK-130 장소 지역 응답과 그룹 장소 목록 API 추가

## 목적

공간 리스트 화면과 그룹 상세 장소 탭에서 필요한 지역명과 장소 목록 및 개수를 API로 제공한다.

## 범위

- `GET /api/v1/places/map` 응답 항목에 `city` 추가
- `GET /api/v1/places/recent` 응답 항목에 `city` 추가
- `GET /api/v1/groups/{groupId}/places?page=0&size=20` 추가
- 그룹 장소 목록은 그룹에 저장된 게시물의 연결 장소를 중복 제거해 반환
- 그룹 장소 목록 응답에 `ownerNickname`, `items`, `totalElements`, 페이지 정보를 포함
- HTTP Client 요청 및 controller, application, infrastructure 테스트 보강

## 제외 범위

- DB 스키마 변경 및 도시명 backfill
- 도시별 검색 또는 필터 API
- 공개 공유 그룹 조회 계약

## 성공 기준

- 최근 저장 공간 및 지도 장소 응답에서 `city` 필드로 `places.city` 값이 내려간다.
- 그룹 상세 장소 탭은 새 API의 `items`와 `totalElements`로 장소 목록과 개수를 렌더링할 수 있다.
- 다른 사용자의 그룹은 기존 그룹 게시물 목록과 동일하게 not found로 처리한다.
- `./gradlew check`가 통과한다.

## 검증

- `./gradlew check`
