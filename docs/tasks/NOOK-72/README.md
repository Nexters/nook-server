# NOOK-72 네이버 장소 검색 provider 및 카카오 병렬 fallback 연동

## 목적

Instagram 게시물 저장 후 장소 파싱 과정에서 카카오 Local API만으로 장소 후보를 찾지 못하거나 응답이 느린 경우를 보완하기 위해 네이버 클라우드 지도 Geocoding API를 추가 provider로 연동한다.

## 범위

- 네이버 클라우드 지도 Geocoding API provider 구현
- 네이버 응답을 Nook `PlaceCandidate` 형식으로 정규화
- 카카오와 네이버를 함께 사용하는 복합 `PlaceSearchProvider` 구현
- 먼저 성공한 유효 후보 결과를 사용하되, 먼저 온 결과가 빈 목록이면 다른 provider 결과 확인
- 한 provider 실패 시 다른 provider 성공 결과로 장소 검색 진행
- 네이버 API 설정값 및 `.env.example` 문서화
- provider 선택 정책과 mapper 테스트 추가

## 제외 범위

- 장소 확정 랭킹 알고리즘 고도화
- OCR/이미지 기반 장소 추론
- 카카오/네이버 결과 병합 및 중복 장소 통합 고도화
- 외부 공개 API 계약 변경
- DB 스키마 변경

## 성공 기준

- 카카오와 네이버가 병렬 호출되고, 유효 후보가 먼저 반환된 provider 결과가 사용된다.
- 먼저 반환된 결과가 빈 목록이면 다른 provider의 유효 후보를 사용할 수 있다.
- 한 provider 실패 시 다른 provider 성공 결과로 검색이 성공한다.
- 네이버 API 응답이 `PlaceCandidate(provider = "NAVER", ...)`로 매핑된다.
- `./gradlew check`가 성공한다.

## 검증

- `./gradlew check`
