# NOOK-231 장소 파싱 원복 및 Apify Google Maps 사진 파이프라인

## 목적

장소 파싱을 기존 Kakao 우선, Naver 조건부 보강 구조로 복구한다. 확정된 장소의 사진만
Apify Google Maps Scraper로 batch 조회하고, 실패하거나 사진이 없으면 기존 Google Places로 fallback한다.

## 범위

- `place.parsing.provider-chain`과 장소 파싱용 Apify provider 제거
- 기존 `PrioritizedPlaceSearchProvider` 직접 연결 복구
- `APIFY_GOOGLE,GOOGLE` 장소 썸네일 provider-chain 지원
- Google Place ID가 있으면 직접 조회하고, 없으면 장소명과 주소로 검색
- 최대 20개 장소를 Actor 한 번에 전달하고 장소별 사진 최대 6장 저장
- Place ID 우선, 이름·주소·거리 보조 검증
- Actor 원문 응답을 `GOOGLE_MAPS_PHOTO` source type으로 저장

## Runtime configuration

```text
place.thumbnail.provider-chain = APIFY_GOOGLE,GOOGLE
```

`place.parsing.provider-chain` 설정은 삭제한다.

## 환경 변수

- `APIFY_API_TOKEN`
- `APIFY_GOOGLE_MAPS_ACTOR_ID` (기본값 `compass~crawler-google-places`)
- `APIFY_GOOGLE_MAPS_BATCH_SIZE` (기본값 및 최대값 20)
- `APIFY_GOOGLE_MAPS_CONNECT_TIMEOUT`
- `APIFY_GOOGLE_MAPS_READ_TIMEOUT` (기본값 300초)

## 제외 범위

- 공개 API 계약 변경
- 장소 검색·선택 로직 변경
- 리뷰와 연락처 등 Google Maps 부가정보 저장
- 기존 장소 전체 재처리

## 검증

- Place ID/검색어 혼합 batch 입력과 결과 mapping
- 장소당 최대 6장 저장
- 장소 불일치·빈 결과·Actor 실패 시 Google fallback
- API/batch application context
- `./gradlew check`

## 운영 DML

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`
- 스키마 변경 없음
