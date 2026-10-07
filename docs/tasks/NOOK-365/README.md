# NOOK-365 미디어 본문 다운로드 타임아웃

## 목적

인스타그램 CDN의 동영상 응답 본문이 멈추면 후속 작업 dispatcher가 반환하지 않아
전체 후속 작업 큐가 정체되는 문제를 해결한다.

## 장애 근거

- 2026-09-20 22:56 KST live 조회에서 job 2930(post 988, VIDEO sequence 2)이
  22:38:48부터 PROCESSING에 머물렀고 후속 작업 20건이 대기했다.
- 스레드 덤프에서 DownloadedMediaFileWriter의 InputStream.read가 HTTP 본문을 기다렸다.
- 콘텐츠와 장소 파싱은 완료됐고 DB 연결 풀 대기는 없었다.
- HttpRequest.timeout만 사용하고 BodyHandlers.ofInputStream으로 반환한 본문에는
  별도 종료 기한이 없었다. fixedDelay dispatcher는 현재 호출이 반환해야 다시 실행된다.

## 범위

- 기존 MEDIA_DOWNLOAD_READ_TIMEOUT(기본 30초)을 HTTP 요청 시작부터 본문 읽기까지
  적용한다. 리다이렉트는 기존처럼 검증된 각 요청마다 제한을 적용한다.
- 기한 만료 시 응답 스트림을 닫아 HTTP 구독을 취소하고 대기 중인 읽기를 해제한다.
- 타임아웃은 기존 PostMediaStorageTimeoutException으로 전달하고 부분 임시 파일을 삭제한다.
- 정상 완료 및 조기 종료 시 예약된 종료 작업을 제거한다.
- 기존 후속 작업 재시도 및 attempt 소유권 검증을 유지한다.

## 제외 범위

- API 계약과 DB 스키마 변경
- 큐 병렬화, 재시도 정책 변경, S3 업로드 시간 제한 변경
- 운영 DB 상태 수동 변경

## 성공 기준과 검증

- 실제 로컬 HTTP 서버로 무응답 본문, 일부 전송 후 정지, 지속적인 소량 전송을 재현한다.
- 제한 시간 내 타임아웃이 발생하고 부분 임시 파일이 제거되며 다음 다운로드가 성공한다.
- 후속 job 타임아웃 시 재시도가 예약되고 다음 job은 완료된다.
- 정상 다운로드, 기존 미디어 형식 및 크기 제한 검증을 유지한다.
- 관련 테스트와 전체 ./gradlew check를 수행한다.
- develop 및 main 배포 후 worker health와 live 후속 작업 큐를 확인한다.

## 로컬 검증 결과

- 전체 Detekt 통과.
- storage 패키지 테스트 및 ProcessParsingFollowUpJobsUseCaseTest 통과.
- 로컬 전체 check는 Docker 미설치로 기존 MySQL Testcontainers 테스트 4건이 실패했다.
  전체 check는 Docker가 제공되는 GitHub CI에서 최종 검증한다.
