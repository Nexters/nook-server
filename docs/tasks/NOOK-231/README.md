# NOOK-231 장소 파싱 provider-chain 및 Apify 전체 파이프라인

## 목적

장소 단서를 실제 장소로 확정하는 provider 순서를 `runtime_configurations`에서 제어하고,
Apify Naver 장소 정보와 사진 전용 Actor를 연계한다.

## Runtime configuration

```text
place.parsing.provider-chain = APIFY_NAVER,LEGACY
```

- `APIFY_NAVER`: Naver Map Actor에서 장소 ID, 주소, 좌표, 카테고리와 연락처를 조회한다.
- `LEGACY`: 기존 Kakao 우선, Naver fallback 검색을 실행한다.
- `DISABLED`: 자동 장소 검색을 중단한다.

설정 누락 또는 전체 오류 시 `LEGACY`를 사용한다. Apify 오류나 빈 결과도 다음 provider로 fallback한다.

사진은 `oxygenated_quagmire/naver-place-photos` Actor에 검증된 Naver URL을 최대 20개씩 전달하고
장소별 원본 이미지 최대 6장을 media storage에 저장한다. 장소 검색 및 사진 Actor 원문은 각각
`NAVER_PLACE_SEARCH`, `NAVER_PLACE_MATCH`, `NAVER_PLACE_PHOTO` source type으로
`scraping_provider_responses`에 기록한다.

## 환경 변수

- `APIFY_API_TOKEN`
- `APIFY_NAVER_PLACE_ACTOR_ID`
- `APIFY_NAVER_PLACE_PHOTO_ACTOR_ID`
- `APIFY_NAVER_PLACE_BATCH_SIZE`
- `APIFY_NAVER_PLACE_READ_TIMEOUT`

## 검증

- runtime provider 선택, 성공 단락 평가 및 fallback 테스트
- Apify 장소 정보 mapping 및 raw response 저장 테스트
- 사진 Actor batch 입력, Place ID mapping 및 최대 6장 저장 테스트
- `./gradlew check`

## 운영 DML

- 적용: `ddl/up.sql`
- 롤백: `ddl/rollback.sql`
- 스키마 변경 없음
