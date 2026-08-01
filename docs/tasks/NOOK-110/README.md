# NOOK-110 Instagram scraping provider 전환 및 Apify fallback

## 목적

Bright Data 장애 시 Instagram 게시물·릴스 파싱을 Apify로 복구하고, 배포 없이 MySQL 설정값으로
provider를 강제 전환한다. provider별 성공 응답 원문은 캐시에 독립적으로 저장해 이후 요청에서 재사용한다.

## 범위

- 범용 `runtime_configurations` key/value 설정 테이블
- Instagram provider mode 조회 및 요청별 routing
- Bright Data timeout/provider 오류 시 Apify 1회 fallback
- Apify Instagram Scraper 동기 API 연동과 기존 콘텐츠 모델 mapping
- NOOK-109의 Bright Data 원문 캐시를 provider 공용 원문 캐시로 확장
- 기존 API와 오류 응답 계약 유지

## Provider mode

설정 key는 `instagram.scraping.provider-mode`이다.

| 값 | 동작 |
| --- | --- |
| `BRIGHT_DATA_ONLY` | Bright Data만 호출한다. |
| `APIFY_ONLY` | Apify만 호출한다. |
| `BRIGHT_DATA_WITH_APIFY_FALLBACK` | Bright Data를 우선 호출하고 timeout 또는 provider 오류에만 Apify를 한 번 호출한다. |
| `APIFY_BRIGHT_WITH_DATA_FALLBACK` | Apify를 우선 호출하고 timeout 또는 provider 오류에만 Bright Data를 한 번 호출한다. |

설정 행이 없거나 값이 올바르지 않으면 `BRIGHT_DATA_WITH_APIFY_FALLBACK`을 안전한 기본값으로 사용한다.
잘못된 URL과 콘텐츠 없음·비공개 오류에는 어느 방향에서도 fallback하지 않는다.

운영 전환은 다음 SQL로 수행한다.

```sql
UPDATE runtime_configurations
SET configuration_value = 'APIFY_ONLY'
WHERE configuration_key = 'instagram.scraping.provider-mode';
```

## Apify

- Actor: `apify/instagram-scraper` (`shu8hvrXbJbY3Eb9W`)
- API: `POST /v2/acts/{actorId}/run-sync-get-dataset-items`
- 게시물 입력: `resultsType=posts`, `directUrls=[canonicalUrl]`, `resultsLimit=1`
- 릴스 입력: `resultsType=reels`, `directUrls=[canonicalUrl]`, `resultsLimit=1`
- 인증: `Authorization: Bearer {APIFY_API_TOKEN}`

필요한 환경 변수는 `APIFY_API_TOKEN`이다. 다음 값은 선택적으로 재정의한다.

- `APIFY_BASE_URL`
- `APIFY_ACTOR_ID`
- `APIFY_CONNECT_TIMEOUT`
- `APIFY_READ_TIMEOUT`

Apify 공식 [입력 스키마](https://apify.com/apify/instagram-scraper/input-schema),
[출력 스키마](https://apify.com/apify/instagram-scraper/output-schema),
[API 안내](https://apify.com/apify/instagram-scraper/api)를 기준으로 구현한다.

## 원문 캐시

`scraping_provider_responses`를 추가하고 NOOK-109의 `bright_data_responses`에 저장된 성공 원문을
`BRIGHT_DATA` provider 행으로 복사한다. 기존 테이블은 구버전 애플리케이션과의 배포 호환성을 위해
유지한다. 새 캐시 식별자는 `(provider, source_type, external_post_id)`이며 Bright Data와 Apify 응답
JSON을 서로 다른 행으로 보존한다. provider 응답이 기존 `ExtractedPostContent`로 정상 mapping된 경우에만
저장한다.

provider API 호출과 cache 조회·저장은 하나의 DB 트랜잭션으로 묶지 않는다.

## 제외 범위

- Admin API 또는 설정 UI
- 요청 단위 provider 선택
- 두 provider 동시 호출
- Apify 비동기 run polling
- 기존 공개 endpoint, request/response 또는 상태 코드 변경

## 성공 기준

- 기본 모드에서 Bright Data 성공 시 Apify를 호출하지 않는다.
- Bright Data timeout/provider 오류에만 Apify를 최대 한 번 호출한다.
- DB 설정 변경으로 Bright Data only와 Apify only를 배포 없이 전환한다.
- provider별 성공 응답 JSON을 독립적으로 저장하고 재사용한다.
- Apify 응답의 본문, 작성자, 해시태그, 장소 태그, 미디어와 게시 시각을 기존 모델로 mapping한다.
- 기존 API 및 오류 의미를 유지한다.
- `./gradlew check`가 성공한다.

## 검증

- provider mode routing 및 fallback 대상·비대상 단위 테스트
- Apify HTTP 요청, 오류 변환, cache 및 mapping 테스트
- runtime configuration persistence adapter 테스트
- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`
- 선행 조건: NOOK-109의 `bright_data_responses` 테이블이 적용되어 있어야 한다.
- 롤백 시 공용 provider 원문 캐시와 runtime configuration 값은 삭제되며 기존 `bright_data_responses`는
  유지된다.
