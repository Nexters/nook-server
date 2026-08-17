# NOOK-201 Google Places 사진·장소 조회 비용 최적화

## 목적

Google Places API의 장소 검색·상세·사진 요청 횟수를 줄여 과금을 통제하되, 기존 영업시간 수집 기능과 API 계약은 유지한다.

## 범위

- Google 장소 사진을 장소당 최대 3장으로 제한한다.
- Google Text Search를 장소당 최대 1회로 제한한다.
- Nearby Search에서 신뢰 가능한 후보를 찾으면 Text Search를 생략한다.
- 검색 단계에서 영업시간 필드를 제외하고 최종 확정 장소에서만 상세 조회한다.
- 저장된 Google Place ID가 있으면 장소 검색을 생략한다.
- NOOK-193의 Google 임시 차단을 유지한다.
  - 장소 썸네일 provider 기본값은 `fixed`다.
  - 이미지 텍스트 provider 기본값은 `openai`다.
  - Google 장소 사진 활성화 기본값은 `false`다.

## 제외 범위

- Google Places·Maps 및 Cloud Vision 연동의 운영 재활성화
- 기존 장소 데이터 일괄 재처리
- API 응답 계약 또는 DB 스키마 변경
- Google Cloud 프로젝트의 API·결제 설정 변경

## 성공 기준

- 장소당 Google 사진 미디어 요청은 최대 3건이다.
- Text Search는 장소당 최대 1건이며 Nearby 매칭 성공 시 호출하지 않는다.
- 검색 요청에 `regularOpeningHours`와 `timeZone`을 포함하지 않는다.
- 영업시간은 최종 확정 장소의 상세 조회에서만 요청한다.
- 저장된 Google Place ID가 있으면 검색 API를 호출하지 않는다.
- 기본 설정에서 Google Places·Maps와 Cloud Vision API를 호출하지 않는다.
- 기존 API 계약을 유지한다.
- `./gradlew check`가 통과한다.

## 검증

- `./gradlew :nook-api-infrastructure:test --tests '*GooglePlacePhotoProviderTest' --no-daemon --no-build-cache`
- `./gradlew check --no-daemon --no-build-cache`
