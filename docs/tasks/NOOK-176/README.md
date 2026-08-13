# NOOK-176 OCR 장소 후보 탈락률 개선

## 목적

OCR transcript에 다수의 장소명이 포함된 게시물에서 장소 clue 추출 단계의 개수 제한 때문에 후보가 과도하게 탈락하지 않도록 한다.

## 범위

- 장소 clue 반환 상한을 20개에서 60개로 확대한다.
- 본문에 `52곳`처럼 20개를 초과하는 기대 장소 개수가 있어도 OCR fallback과 recall recovery 판단에 사용한다.
- OpenAI structured output schema, 출력 토큰 한도, 프롬프트의 장소 개수 상한을 함께 조정한다.

## 제외 범위

- OCR provider 교체
- 게시물 이미지 저장 흐름 변경
- DB 스키마 변경

## 검증

- `./gradlew :nook-api-application:test --tests org.every.nook.api.application.place.ProcessPlaceParsingJobUseCaseTest`
- `./gradlew check`
