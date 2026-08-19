# NOOK-227 장소 썸네일 provider-chain 런타임 전환 및 Apify 네이버 플레이스 연동

## 목적

장소 썸네일 provider 실행 순서를 MySQL runtime configuration으로 제어하고, 게시물 원본 이미지,
Apify 네이버 플레이스, Google Places, 고정 이미지 사이에서 배포 없이 전환 및 fallback합니다.

## Provider chain

설정 key는 `place.thumbnail.provider-chain`이며 쉼표로 실행 순서를 표현합니다.

```text
APIFY_NAVER,GOOGLE
```

지원값은 다음과 같습니다.

- `POST_MEDIA`: 장소 단서가 발견된 원본 게시물 이미지를 사용합니다.
- `APIFY_NAVER`: Apify Naver Map Actor로 장소를 재검증하고 네이버 이미지를 저장합니다.
- `GOOGLE`: 기존 Google Places 보강 provider를 사용합니다.
- `FIXED`: 설정된 고정 URL을 사용합니다.
- `DISABLED`: 썸네일 provider 호출을 중단합니다.

각 provider가 사진을 반환하면 이후 provider는 호출하지 않습니다. 사진 없이 영업시간 같은 보강 정보만
반환하면 다음 provider를 호출하고 두 결과를 병합합니다. provider 오류는 전체 처리를 실패시키지 않고
다음 provider로 넘어갑니다.

알 수 없는 값은 제외하며, 유효한 값이 하나도 없거나 설정 행이 없으면 배포 호환성을 위해 기존
`PLACE_THUMBNAIL_PROVIDER` 값을 사용합니다. 운영 DML 적용 후에는 runtime configuration이 우선합니다.

## Apify

- Actor: `delicious_zebu/naver-map-search-results-scraper`
- API: `POST /v2/acts/{actorId}/run-sync-get-dataset-items`
- 입력: 장소명과 주소 keyword, 상세 조회 활성, 최대 5건
- 후보 검증: 정규화된 장소명 일치와 주소 호환 또는 좌표 300m 이내
- 저장: 검증된 후보 이미지 중 최대 6장을 Nook media storage에 복사

필수 비밀값은 `APIFY_API_TOKEN`이며 DB에 저장하지 않습니다. 다음 배포 설정을 선택적으로 재정의합니다.

- `APIFY_NAVER_PLACE_BASE_URL`
- `APIFY_NAVER_PLACE_ACTOR_ID`
- `APIFY_NAVER_PLACE_MAX_RESULTS`
- `APIFY_NAVER_PLACE_CONNECT_TIMEOUT`
- `APIFY_NAVER_PLACE_READ_TIMEOUT`

Google은 chain에 `GOOGLE`이 포함되고 `GOOGLE_MAPS_API_KEY`가 설정된 경우 활성화됩니다. 기존
`GOOGLE_PLACE_PHOTO_ENABLED` 이중 스위치는 사용하지 않습니다.

## 범위

- runtime provider-chain 파싱과 순차 routing
- 기존 `POST_MEDIA`, `GOOGLE`, `FIXED` provider 조합
- Apify 네이버 플레이스 provider와 사진 저장
- provider 선택 및 fallback 구조화 로그
- API와 batch의 동일 설정
- 운영 설정 DML과 회귀 테스트

## 제외 범위

- 공개 API 계약 변경
- runtime configuration Admin API/UI
- provider 병렬 호출
- Apify 비동기 polling
- 장소 검색 provider 교체
- 외부 API 비용 원장
- 기존 장소 일괄 재파싱

## 성공 기준

- DB 설정 변경만으로 provider 순서가 변경됩니다.
- 사진을 반환한 provider 뒤의 provider는 호출하지 않습니다.
- 빈 결과와 provider 오류는 다음 provider로 fallback합니다.
- Google 영업시간 같은 사진 외 보강 정보가 fallback 중 유실되지 않습니다.
- Apify 후보 검증을 통과한 이미지 최대 6장만 Nook storage URL로 저장됩니다.
- 설정 DML 적용 전에는 기존 환경 변수 동작을 유지합니다.
- API 및 batch 애플리케이션이 같은 chain을 사용합니다.
- 기존 API와 오류 의미를 유지합니다.
- `./gradlew check`가 성공합니다.

## 검증

- provider-chain 순서, 단락 평가, 오류 및 빈 결과 단위 테스트
- Apify 요청, 응답 mapping, 장소 불일치, 사진 제한 및 저장 테스트
- application context 테스트
- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`

## 운영 DML

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`
- 스키마 변경은 없습니다.
