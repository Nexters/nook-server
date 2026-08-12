# NOOK-171 장소 검색·선택 및 Google 보충정보 파이프라인 개편

## 목적

게시물에서 추출한 장소 단서를 카카오 우선으로 검색하고, 결과 신뢰도가 낮을 때만 네이버 지역검색으로 보강한다. 확정된 장소는 Google Places의 이름·주소·거리 복합 점수로 다시 식별해 Place ID, 사진, 영업시간을 저장한다.

## 범위

- NAVER API Hub 지역검색 연동
- 카카오 우선 후보 점수화와 조건부 네이버 폴백
- Google Places 위치 편향 검색 및 이름·주소·거리 복합 매칭
- Google Place ID 저장과 재조회 시 재사용
- 임시 DEBUG 추적 로그 추가

## 제외 범위

- 기존 공개 API 계약 변경
- 장소 병합 및 관리자 보정 기능
- Prometheus 지표 변경

## 검증

- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`

## 배포 전 작업

`ddl/up.sql`을 적용하고 애플리케이션을 배포한다. 롤백 시 애플리케이션을 이전 버전으로 되돌린 뒤 `ddl/rollback.sql`을 적용한다.
