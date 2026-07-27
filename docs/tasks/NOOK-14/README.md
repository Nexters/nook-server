# NOOK-14 카카오 Local API 기반 장소 후보 검색 연동

## 목적

NOOK-10에서 추출한 Instagram 장소 단서나 이후 OCR/LLM이 생성할 검색어를 입력받아 카카오 Local
API에서 실제 장소 후보를 검색하고 공통 형식으로 정규화한다.

## 범위

- 검색어 목록과 선택적인 중심 좌표·반경을 받는 내부 장소 후보 검색 유스케이스
- application 계층의 장소 검색 provider port와 유스케이스
- Kakao Local 키워드 장소 검색 adapter
- 장소 ID, 이름, 주소, 좌표, 카테고리, 전화번호, 카카오맵 URL 정규화
- 도로명 주소가 없는 경우 지번 주소 사용
- 여러 검색어 결과를 `(provider, externalPlaceId)` 기준으로 중복 제거
- 후보 없음은 정상 빈 목록으로 반환
- provider 오류와 timeout 공통 API 오류 변환

## 제외 범위

- OCR
- OpenAI 또는 기타 LLM 기반 장소 추론
- 영상 프레임 추출
- 장소 후보 confidence 계산 및 자동 확정
- `Post`, `Place`, `PostPlace` 영속화
- 네이버·Google 등 추가 지도 provider

## 내부 계약

장소 후보 검색은 외부 HTTP API로 노출하지 않습니다. 장소 파싱 worker가
`SearchPlaceCandidatesUseCase`를 호출하며 다음 검색 조건을 전달합니다.

```json
{
  "queries": ["Nook Cafe", "성수 카페"],
  "longitude": 127.1,
  "latitude": 37.1,
  "radius": 1000
}
```

- `queries`: 장소 검색어, 최대 10개
- `longitude`, `latitude`: 선택값이지만 둘 중 하나만 입력할 수 없다.
- `radius`: 중심 좌표와 함께 입력하며 1~20,000m 범위다.

검색 결과:

```json
[
  {
    "provider": "KAKAO",
    "externalPlaceId": "26338954",
    "name": "Nook Cafe",
    "address": "서울 성동구 아차산로 1",
    "latitude": 37.5120741,
    "longitude": 127.0590297,
    "category": "음식점 > 카페",
    "phoneNumber": "02-1234-5678",
    "providerUrl": "https://place.map.kakao.com/26338954"
  }
]
```

## Mock scraping data 연결

첫 버전에서는 NOOK-10 응답에 포함된 `locationDetails.name`, `locationNames` 등 명시적인 장소 단서를
mock data로 준비해 `queries`에 전달한다. 후속 OCR/LLM 단계도 검색어만 생성해 같은 장소 검색
유스케이스를 사용한다.

## Kakao Local

키워드 장소 검색 endpoint를 사용한다.

```text
GET https://dapi.kakao.com/v2/local/search/keyword.json
Authorization: KakaoAK {KAKAO_REST_API_KEY}
```

로그인용 Native App Key나 앱 ID가 아니라 카카오 디벨로퍼스의 REST API 키가 필요하다.

## 환경변수

- `KAKAO_REST_API_KEY`
- `KAKAO_LOCAL_BASE_URL` (선택)
- `KAKAO_LOCAL_CONNECT_TIMEOUT` (선택, 기본 `3s`)
- `KAKAO_LOCAL_READ_TIMEOUT` (선택, 기본 `5s`)

## 오류

- 잘못된 검색 조건: `PlaceSearchInvalidRequestException`
- 카카오 Local API 오류 또는 응답 형식 오류: `PlaceSearchProviderException`
- timeout: `PlaceSearchProviderTimeoutException`

## 검증

```shell
./gradlew check
```

테스트에서는 다음을 검증한다.

- 중복 검색어와 중복 장소 후보 제거
- 중심 좌표와 반경 검증
- REST API 키 인증과 카카오 query parameter
- 카카오 응답 필드와 도로명·지번 주소 fallback 매핑
- 빈 후보 정상 응답
- provider 오류 변환
- 장소 파싱 worker의 내부 유스케이스 호출

## DDL

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`

NOOK-14는 장소 후보를 저장하지 않으므로 데이터베이스 스키마를 변경하지 않는다.
