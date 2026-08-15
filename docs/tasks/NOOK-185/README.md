# NOOK-185 OCR을 Google Cloud Vision으로 전환

## 목적

- 게시물 장소 파싱에서 이미지 OCR을 OpenAI 이미지 입력 기반 구현 대신 Google Cloud Vision API로 수행한다.
- OpenAI는 본문/전사 텍스트 기반 장소 추론과 후보 선택 역할로 유지한다.

## 범위

- `ImageTextExtractor` 구현체를 Google Cloud Vision REST API 기반으로 추가하고 빈 연결을 전환한다.
- Cloud Vision 설정을 추가한다.
- 기존 이미지 저장 완료 확인 흐름은 유지한다.

## 제외 범위

- 장소 추론 LLM 교체
- 장소 검색/매칭 흐름 변경
- 이미지 저장 완료 확인 정책 변경

## 검증

- `./gradlew :nook-api-infrastructure:test --tests '*GoogleCloudVisionImageTextExtractorTest'`
- `./gradlew :nook-api-application:test --tests '*ProcessPlaceParsingJobUseCaseTest'`
- `./gradlew check`
