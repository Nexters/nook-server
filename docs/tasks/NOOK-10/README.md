# NOOK-10 지도 등록용 Instagram 게시물·릴스 정보 추출

## 목적

Instagram 게시물 또는 릴스 URL을 받아 Bright Data Instagram Scraper API로 수집하고, Nook 지도 등록
흐름에서 사용할 콘텐츠와 장소 메타데이터로 정규화한다.

## 범위

- `/p/{shortcode}`, `/reel/{shortcode}` URL 검증 및 canonical URL 변환
- Bright Data Posts/Reels 동기 수집 API 연동
- 설명, 해시태그, 썸네일, 사진·영상과 carousel 순서 정규화
- Bright Data가 제공한 장소 표시값과 위치 상세 정보 보존
- 빈 결과, provider 오류 및 timeout의 공통 API 오류 변환
- Swagger/OpenAPI endpoint 제공

## 제외 범위

- Instagram 장소 정보 추론
- `Post`, `Place`, `PostPlace` 영속화
- 미디어 파일 다운로드
- 비공개 콘텐츠 우회 수집
- 비동기 snapshot polling

## API

`POST /api/v1/instagram/contents/extract`

```json
{
  "url": "https://www.instagram.com/reel/SHORTCODE/"
}
```

응답은 공통 `ApiResponse` 형식이며 다음 정보를 포함한다.

- canonical URL과 shortcode
- 콘텐츠 유형
- 설명과 해시태그
- 썸네일
- 순서가 보존된 이미지·영상 목록
- 선택적인 장소 표시값 목록
- 선택적인 장소 ID, 이름, 위도, 경도, 이미지 URL

장소 정보가 없어도 콘텐츠 추출은 성공한다. Instagram 장소 정보는 주소나 좌표가 불완전할 수 있으므로
이 단계에서 NOOK-7의 확정 `Place`로 변환하지 않는다.

## Bright Data

단건 실시간 요청에 적합한 동기 endpoint를 사용한다.

```text
POST https://api.brightdata.com/datasets/v3/scrape
```

- Posts dataset: `gd_lk5ns7kz21pck8jpis`
- Reels dataset: `gd_lyclm20il4r5helnj`
- 인증: `Authorization: Bearer {BRIGHT_DATA_API_TOKEN}`

동기 호출이 snapshot 응답으로 전환되면 요청을 장시간 유지하지 않고 provider timeout으로 처리한다.

## 환경변수

- `BRIGHT_DATA_API_TOKEN`
- `BRIGHT_DATA_POSTS_DATASET_ID` (선택)
- `BRIGHT_DATA_REELS_DATASET_ID` (선택)
- `BRIGHT_DATA_BASE_URL` (선택)
- `BRIGHT_DATA_CONNECT_TIMEOUT` (선택, 기본 `3s`)
- `BRIGHT_DATA_READ_TIMEOUT` (선택, 기본 `60s`)

## 오류

- 잘못된 Instagram URL: `400 Bad Request`
- 콘텐츠 없음 또는 빈 결과: `404 Not Found`
- Bright Data 오류 또는 응답 형식 오류: `502 Bad Gateway`
- timeout 또는 snapshot 전환: `504 Gateway Timeout`

## 검증

```shell
./gradlew check
```

테스트에서는 다음을 검증한다.

- canonical URL 변환과 위장 호스트 차단
- Posts/Reels dataset 선택 및 Bearer 인증
- carousel 미디어 순서
- 게시물 장소 상세 매핑
- 장소가 없는 릴스 매핑
- 빈 결과, 404, snapshot 응답 및 API 토큰 누락
- API 성공 응답과 요청 검증

2026-07-24에 실제 Bright Data API 키와 공개 Instagram 게시물 URL로 live 호출을 확인했다. carousel
미디어 4건과 설명, 해시태그, 장소 ID, 장소명, 위도 및 경도가 정상적으로 정규화됐다.

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`

NOOK-10은 수집 결과를 저장하지 않으므로 데이터베이스 스키마를 변경하지 않는다. 두 SQL 파일은 배포
절차에서 이 작업이 schema no-op임을 명시한다.
