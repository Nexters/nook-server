# NOOK-107 게시물 저장 파이프라인 지연 계측 및 직렬 병목 제거

## 목적

Instagram 게시물 저장 후 장소 파싱 완료까지 이어지는 외부 호출의 단계별 지연을 관측하고,
장소 확정에 필요하지 않은 미디어 복사와 장소 썸네일 저장을 완료 경로에서 분리합니다.

## 범위

- 콘텐츠 수집, 제목 생성, 미디어 저장, 장소 단서 추출, 장소 검색, 후보 선택, DB 완료 처리의 단계별 시간 계측
- 이벤트 처리 queue delay 계측
- 원본 미디어 정보 저장 직후 장소 파싱과 S3 미디어 복사를 독립적으로 실행
- 첫 검색어에서 엄격 일치 장소가 하나면 후속 검색어 호출 생략
- 장소 연결 완료 후 Google 장소 썸네일을 별도 비동기 작업으로 저장
- terminal 콘텐츠 오류는 재시도하지 않고, 재시도 대기는 worker를 점유하지 않도록 예약 실행
- 먼저 성공한 장소 provider가 있으면 남아 있는 provider future 취소

## 제외 범위

- 제목 생성과 장소 단서 추출 OpenAI 요청 통합
- OpenAI 모델 및 프롬프트 변경
- endpoint, request/response 및 polling 상태 계약 변경
- 외부 메시지 큐 도입

## 성공 기준

- `postId`, `attempt`, flow, stage, outcome과 `durationMs`를 로그에서 확인할 수 있습니다.
- stage별 Timer와 이벤트 queue delay Timer가 Micrometer registry에 기록됩니다.
- 장소 파싱은 Instagram 원본 미디어가 저장되면 S3 복사를 기다리지 않고 시작합니다.
- 첫 검색어에서 장소가 엄격 일치하면 후속 검색어를 호출하지 않습니다.
- 장소 파싱 상태는 Google 썸네일 조회와 저장을 기다리지 않고 `COMPLETED`가 됩니다.
- 재시도 대기 동안 parsing worker가 `Thread.sleep`으로 점유되지 않습니다.
- 기존 API와 실패 의미를 유지하고 관련 테스트 및 `./gradlew check`가 통과합니다.

## 검증

- `ProcessPostContentParsingJobUseCaseTest`
- `ProcessPlaceParsingJobUseCaseTest`
- 콘텐츠·장소 parsing event listener 테스트
- media 및 thumbnail persistence adapter 테스트
- `CompositePlaceSearchProviderTest`
- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`
