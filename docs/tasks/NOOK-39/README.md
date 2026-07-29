# NOOK-39 OpenAI 이미지 보강 장소 파싱 파이프라인

## 목적

텍스트 기반 장소 추론으로 장소를 하나도 확정하지 못한 경우에만 저장된 Instagram 이미지를
OpenAI Vision 입력으로 보강해, 이미지에만 상호명이 있는 게시물의 장소 확정률을 높인다.
별도 OCR provider 없이 기존 `gpt-5-nano`와 저해상도 이미지 입력을 사용해 비용을 제한한다.

## 범위

- 텍스트 장소 단서 추출과 카카오 후보 검증을 우선 실행
- 텍스트 경로에서 장소를 하나도 확정하지 못했을 때만 이미지 보강
- 저장된 `IMAGE` 미디어를 원본 순서로 최대 20장 선택
- 모든 이미지를 한 번의 OpenAI Responses 요청에 `detail: low`로 전달
- 본문, 해시태그, 장소 태그와 이미지를 함께 사용한 structured output 장소 단서 추출
- 이미지 보강 단서에 기존 카카오 후보 검색과 검증 흐름 재사용
- 이미지까지 분석했지만 장소를 확정하지 못한 경우 재시도 없이 실패 처리
- OpenAI 또는 장소 검색 provider 예외에는 기존 backoff 재시도 유지

## 제외 범위

- NAVER Cloud 등 별도 OCR provider
- 영상 frame 추출
- `detail: high` 또는 고성능 모델 자동 fallback
- 이미지 다운로드, 리사이징 또는 contact sheet 생성
- Batch API 전환
- 공개 API 또는 DB 스키마 변경

## 처리 흐름

1. 게시물 본문, 해시태그, 장소 태그로 장소 단서를 추출한다.
2. 단서별 카카오 후보를 검색하고 기존 엄격 매칭 및 LLM 후보 선택을 수행한다.
3. 하나 이상의 장소가 확정되면 이미지 호출 없이 작업을 완료한다.
4. 모든 단서가 미해결이고 저장된 이미지가 있으면 최대 20장을 한 요청으로 분석한다.
5. 이미지 보강 단서를 같은 장소 검색 및 검증 흐름으로 확정한다.
6. 이미지까지 근거가 없으면 동일 입력을 재시도하지 않고 `FAILED`로 종료한다.
7. provider 예외는 기존 설정된 재시도 횟수와 backoff를 적용한다.

## 성공 기준

- 텍스트만으로 하나 이상의 장소가 확정되면 이미지를 OpenAI에 전달하지 않는다.
- 텍스트 단서가 없거나 모든 단서가 미해결이면 이미지 보강을 같은 attempt 안에서 한 번 실행한다.
- 원본 순서의 이미지 최대 20장이 단일 요청에 포함되고 모두 `detail: low`를 사용한다.
- 이미지 보강 후 확정된 복수 장소가 추론 순서대로 저장된다.
- 이미지까지 장소 근거가 없으면 재시도 없이 실패한다.
- provider 예외는 기존 backoff 정책에 따라 재시도한다.
- 공개 API 계약과 DB 스키마를 변경하지 않는다.
- `./gradlew detekt`, 관련 테스트, `./gradlew check`가 성공한다.

## 검증

```shell
./gradlew detekt
./gradlew :nook-api-application:test
./gradlew :nook-api-infrastructure:test
./gradlew check
```

## DDL

이 작업은 DB 스키마를 변경하지 않는다.
