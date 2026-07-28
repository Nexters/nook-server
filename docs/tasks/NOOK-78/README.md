# NOOK-78 Instagram 장소 태그 우선 장소 단서 추출 개선

## 목적

Instagram 응답에 명시적인 장소 태그가 있으면 본문의 수식어나 문맥을 상호명에 섞지 않고,
provider 장소명을 최우선 근거로 사용해 장소 검색 정확도를 높인다.

## 범위

- `sourceLocationTag`가 상호명이면 본문과 해시태그보다 우선해 장소명으로 사용
- 장소 태그 원문을 첫 번째 검색어로 유지
- 본문에서 확인되는 한글·영문 표기와 지역 조합 검색어 생성
- 장소별 검색어 최대 개수를 3개에서 4개로 확대
- 장소 태그와 본문이 같은 장소를 가리키면 하나의 장소로 병합
- 장소 태그가 없거나 일반 지역명이면 기존 본문·해시태그 기반 추출 유지
- OpenAI structured output 요청 회귀 테스트 추가

## 제외 범위

- `location_details.lat`, `location_details.lng`, `location_details.pk` 활용
- 장소 검색 provider의 후보 랭킹 또는 유사도 알고리즘 변경
- DB 스키마와 API 계약 변경

## 성공 기준

- `location_details.name`이 `Lodge190`이고 본문이 `연희동 사랑방 롯지190`인 사례에서
  `name`은 `Lodge190`으로 추출한다.
- `Lodge190`, `롯지190`, `롯지 190`, `연희동 Lodge`처럼 원문, 음차, 띄어쓰기,
  지역을 붙인 축약형 검색어를
  최대 4개 반환할 수 있다.
- 장소 태그가 없는 입력의 기존 추출 동작을 유지한다.
- `./gradlew check`가 성공한다.

## 검증

```shell
./gradlew :nook-api-infrastructure:test \
  --tests org.every.nook.api.infrastructure.openai.OpenAiContentInferenceAdapterTest
./gradlew check
```

## DDL

스키마 변경은 없다.
