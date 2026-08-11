# NOOK-108 게시물 제목과 장소 단서 OpenAI 호출 통합

## 목적

같은 Instagram 본문, 해시태그와 장소 태그로 각각 실행하던 제목 생성과 텍스트 장소 단서 추출을
하나의 OpenAI structured output 요청으로 통합합니다.

## 범위

- 제목과 텍스트 장소 단서를 함께 반환하는 application port 정의
- OpenAI structured output schema와 adapter 통합
- 통합 추론 결과의 장소 단서를 `place_parsing_jobs`에 저장해 비동기 처리와 복구에서 재사용
- 신규 job은 저장된 텍스트 장소 단서를 사용하고, 기존 데이터처럼 단서가 없을 때만 텍스트 추론 fallback
- 텍스트 단서로 장소를 확정하지 못할 때 기존 이미지 분석 fallback 유지
- 기존 후보 선택, 제목 기본값, 장소 개수와 검색어 제한 유지

## 제외 범위

- OpenAI 모델 변경
- 이미지 분석 제거
- 장소 provider 및 API 계약 변경

## 성공 기준

- 신규 게시물의 정상 텍스트 경로에서 제목과 장소 단서가 OpenAI 요청 한 번으로 생성됩니다.
- 통합 추론 결과가 DB에 저장되어 장소 parsing 재시작 시 OpenAI 텍스트 요청을 반복하지 않습니다.
- 텍스트 단서가 비어 있거나 확정되지 않으면 기존 이미지 fallback이 동작합니다.
- 기존 제목 fallback, strict match와 후보 선택 동작을 유지합니다.
- `./gradlew check`가 통과합니다.

## 검증

- OpenAI 통합 structured output adapter 테스트
- 콘텐츠 parsing use case 호출 횟수와 persistence 테스트
- 저장된 단서 사용 및 레거시 텍스트 fallback 테스트
- 이미지 fallback 회귀 테스트
- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`
