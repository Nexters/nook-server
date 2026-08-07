# NOOK-122 장소 영업시간 및 Google 장소 사진 6장 저장

## 목적

Google Places에서 장소의 정규 영업시간과 사진을 수집해 장소 상세 화면에서 제공한다.

## 범위

- Google Text Search 결과를 기존 장소의 이름과 좌표로 검증한다.
- 정규 영업시간, IANA 시간대와 현지화된 요일별 설명을 저장한다.
- Google 장소 사진을 최대 6장까지 서비스 미디어 저장소에 저장한다.
- 장소 상세 응답에 `photoUrls`, `openingHours`, `openNow`를 추가한다.
- 기존 `thumbnailUrl`은 첫 번째로 저장된 사진을 사용해 호환성을 유지한다.
- Google 조회 및 개별 사진 저장 실패가 장소 확정 흐름을 실패시키지 않게 한다.

## 제외 범위

- 카카오·네이버 장소 상세 페이지 크롤링
- 사용자 사진 업로드·정렬
- 공휴일 등 Google의 비정규 영업시간을 장기간 캐시하는 기능
- 기존 장소 전체를 일괄 보강하는 배치

## 데이터베이스

- `places.opening_hours`: Google 정규 영업시간 JSON. 정보가 없으면 `NULL`이다.
- `places.photo_urls`: 서비스 미디어 저장소에 저장된 장소 사진 URL JSON 배열이다.
- 물리 foreign key는 추가하지 않는다.

적용 SQL은 `ddl/up.sql`, 롤백 SQL은 `ddl/rollback.sql`을 사용한다.

## 성공 기준

- 일치하는 Google 장소의 영업시간과 사진 최대 6장을 저장한다.
- 장소 상세 응답에서 영업시간, 현재 영업 여부와 사진 목록을 조회한다.
- 사진이 6장보다 적거나 일부 저장에 실패하면 성공한 사진만 반환한다.
- Google 장소가 이름과 500m 이내 좌표 조건을 충족하지 않으면 보강 정보를 저장하지 않는다.
- 기존 장소 상세 필드와 `thumbnailUrl` 의미를 유지한다.
- `./gradlew check --no-daemon --no-build-cache`가 통과한다.

## 검증

- `./gradlew detekt --no-daemon --no-build-cache`
- `./gradlew test --no-daemon --no-build-cache`
- `./gradlew check --no-daemon --no-build-cache`
