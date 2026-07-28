# NOOK-82 단일 장소명 후보의 잘못된 region 매칭 실패 보완

## 목적

모든 검색어와 provider에서 얻은 후보를 보존하고, 기존의 엄격한 매칭으로 장소를 확정하지 못하면
LLM이 검색 근거를 함께 비교해 가장 적합한 후보를 선택하도록 보완한다.

## 범위

- 모든 검색어와 카카오·네이버 결과 수집
- `(provider, externalPlaceId)` 기준 중복 제거와 후보별 `matchedQueries` 보존
- 기존의 정규화된 상호명·`region` 엄격 매칭을 1차로 수행
- 1차 결과가 정확히 하나면 LLM 호출 없이 확정
- 1차 결과가 없거나 여러 개면 장소 단서와 전체 후보를 OpenAI structured output에 전달
- LLM은 전달받은 후보 인덱스 하나 또는 근거 부족 시 `null`만 반환
- 선택 거부와 provider·LLM 오류는 기존 재시도 및 실패 정책 유지

## 제외 범위

- 상호명 유사도 또는 부분 일치
- 좌표 기반 거리 판별
- LLM 장소 단서 추출 프롬프트와 장소 검색 provider 변경

## 성공 기준

- `이츠야` 사례에서 전체 후보와 검색 근거가 fallback에 전달되고 카카오 후보가 저장된다.
- 엄격 매칭 결과가 하나이면 fallback LLM을 호출하지 않는다.
- LLM이 `null`을 반환하면 임의 후보를 저장하지 않는다.
- 중복 후보는 하나로 합쳐지고 해당 후보를 찾은 검색어가 모두 유지된다.
- `./gradlew check`가 성공한다.

## 검증

```shell
./gradlew :nook-api-application:test \
  --tests org.every.nook.api.application.place.ProcessPlaceParsingJobUseCaseTest
./gradlew check
```

## DDL

스키마 변경은 없다.
