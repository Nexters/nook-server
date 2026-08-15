# NOOK-175 OCR 실행 전 게시물 이미지 저장 완료 대기

## 목적

게시물 장소 파싱에서 OCR fallback이 필요한 경우, Instagram CDN URL이 그대로 남아 있는 상태로 OCR을 호출하지 않도록 한다. 이미지 저장 작업이 아직 완료되지 않았다면 장소 파싱 job을 재시도시켜 저장 완료 이후 OCR을 수행한다.

## 범위

- 텍스트만으로 장소가 충분히 해석되는 경우 기존처럼 이미지 저장을 기다리지 않는다.
- OCR fallback이 필요한 경우에만 게시물 이미지 URL이 OCR에 사용할 수 있는 저장 URL인지 확인한다.
- 미준비 상태에서는 OCR provider를 호출하지 않고 기존 place parsing retry 흐름으로 넘긴다.

## 제외 범위

- OCR provider 변경
- 장소 검색/후보 선택 로직 변경
- DB 스키마 변경

## 검증

- `./gradlew :nook-api-application:test --tests org.every.nook.api.application.place.ProcessPlaceParsingJobUseCaseTest`
- `./gradlew :nook-api-infrastructure:test --tests org.every.nook.api.infrastructure.persistence.place.PostMediaOcrReadinessAdapterTest`
