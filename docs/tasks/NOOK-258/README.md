# NOOK-258 Instagram 원본 이미지 기반 단건 병렬 OCR 전환

## 목적

Instagram 원본 이미지로 OCR을 즉시 시작하고 자체 스토리지 저장과 병렬로 수행한다. 이미지 5장 batch를
이미지당 단건 요청으로 전환해 전사 출력 예산과 실패를 이미지별로 격리한다.

## 범위

- OCR 스토리지 readiness gate 제거
- 이미지당 단건 OCR과 최대 4개 제한 병렬 실행
- 원본 URL 실패 시 DB의 최신 저장 URL로 해당 이미지만 재시도
- 일부 이미지 실패 시 성공한 전사는 유지하고, 전 이미지 호출 실패만 작업 재시도
- 실행 동시성 환경 설정과 회귀 테스트

## 제외 범위

- 장소 파싱 완전성 상태 변경
- 장소 사진 provider chain 변경
- 특정 게시물 예외 처리

## 성공 기준

- 스토리지 저장 지연만으로 OCR 작업이 실패하지 않는다.
- 모든 OCR provider 호출은 이미지 한 장만 포함한다.
- 한 이미지의 실패가 다른 이미지 전사를 폐기하지 않는다.
- 저장 URL이 준비되면 실패한 원본 URL만 최신 URL로 재시도한다.
- `./gradlew check`가 통과한다.

## 검증

- `./gradlew :nook-api-application:test --tests '*ProcessPlaceParsingJobUseCaseTest'`
- `./gradlew check`
