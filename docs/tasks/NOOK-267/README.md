# NOOK-267 Naver Place 사진 fallback provider 추가 및 Google 체인 제거

## 목적

Google Places Photo provider 없이 장소 썸네일을 보강합니다. Apify Google 결과가 비어 있으면 검증된
Naver Place의 업체 공식 사진을 저장하고, 그 결과도 비어 있을 때만 안전 조건을 통과한 게시물 미디어로
fallback합니다.

## 범위

- provider type `APIFY_NAVER_PLACE` 추가
- 권장 runtime chain을 `APIFY_GOOGLE,APIFY_NAVER_PLACE,POST_MEDIA`로 변경
- Naver Map 검색 Actor로 실제 Naver Place ID와 URL 해석
- 이름 일치와 함께 주소 호환 또는 좌표 300m 이내인 후보만 허용
- Naver Place Photo Actor에서 업체 공식 사진만 최대 3장 저장
- Actor 빈 결과, 오류 및 사진 저장 실패 시 기존 runtime chain으로 fallback
- Actor 응답 캐시와 기존 place-thumbnail processing metrics 적용

## Apify 계약

- 검색 Actor: `delicious_zebu/naver-map-search-results-scraper`
- 사진 Actor: `oxygenated_quagmire/naver-place-photos`
- API: `POST /v2/acts/{actorId}/run-sync-get-dataset-items`
- 사진 입력: 검증된 Naver Map URL, `filterBy=business`, `maxPhotos=3`
- 사진 출력: 검색 결과의 Place ID와 같은 `placeId`, `photoType=ibu|business`, 공개 HTTP 이미지 URL

필수 비밀값은 기존 `APIFY_API_TOKEN`을 사용합니다. 다음 배포 설정을 선택적으로 재정의할 수 있습니다.

- `APIFY_NAVER_PLACE_PHOTO_BASE_URL`
- `APIFY_NAVER_PLACE_SEARCH_ACTOR_ID`
- `APIFY_NAVER_PLACE_PHOTO_ACTOR_ID`
- `APIFY_NAVER_PLACE_MAX_RESULTS`
- `APIFY_NAVER_PLACE_BATCH_SIZE`
- `APIFY_NAVER_PLACE_STORAGE_CONCURRENCY`
- `APIFY_NAVER_PLACE_CONNECT_TIMEOUT`
- `APIFY_NAVER_PLACE_READ_TIMEOUT`

## 제외 범위

- Google provider 삭제 또는 Google API key 설정
- 방문자·블로그 사진 사용
- 이름만 일치하는 Naver 후보 허용
- `POST_MEDIA`의 독점 OCR evidence 조건 완화
- 기존 실패 장소 일괄 재처리
- 공개 API 변경

## 성공 기준

- `APIFY_GOOGLE` 사진이 비어 있을 때 `APIFY_NAVER_PLACE`가 실행됩니다.
- 동명 타 지점과 주소·좌표 불일치 후보 사진을 저장하지 않습니다.
- 업체 공식 사진만 Nook media storage URL로 최대 3장 저장합니다.
- Naver 결과가 없거나 실패하면 `POST_MEDIA`로 이어집니다.
- runtime chain에서 `GOOGLE`을 제외해도 정상 동작합니다.
- `./gradlew check`가 성공합니다.

## 운영 DML

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`
- 스키마 변경은 없습니다.
