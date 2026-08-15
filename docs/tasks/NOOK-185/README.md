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

## 후속: 이미지를 URL 대신 바이트로 전달

### 문제

Cloud Vision 전환 직후 OCR 이 필요한 게시물이 계속 실패했다.

```
Google Cloud Vision failed: We can not access the URL currently.
Please download the content and pass it in.
```

`imageUri` 로 CloudFront URL 을 넘기고 구글이 직접 받아가게 하는 구조였다. Vision 의
`imageUri` 는 GCS(`gs://`) 가 아닌 일반 HTTP URL 에 대해 best-effort 라 보장되지 않는다.

배제한 원인:

- URL 비공개 아님 — 외부에서 `HTTP 200`, 정상 jpeg 응답
- CDN 이 구글 fetcher 를 막는 것도 아님 — `Google-Cloud-Vision` / `Googlebot` / `curl` UA 전부 200
- 이미지 저장 전 호출도 아님 — 저장 완료(11장) 후 1~4초 뒤 호출

### 조치

서버가 이미지를 내려받아 base64 로 실어 보낸다.

```
"image": { "source": { "imageUri": ... } }   ->   "image": { "content": "<base64>" }
```

`VisionImageDownloader` 가 다운로드와 크기 검사를 맡는다. 이미지 1장당
`external.google-cloud-vision.max-image-bytes`(기본 4MB), 요청 합계
`max-request-bytes`(기본 12MB)를 넘기면 실패한다. base64 는 약 4/3 로 늘어나므로
Vision 의 20MB 제한 아래에 머무르도록 잡은 값이다.

### 검증

- `./gradlew build`
- `GoogleCloudVisionImageTextExtractorTest` — base64 전송, `imageUri` 미사용, 다운로드 실패, 크기 초과
