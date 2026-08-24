# ADR-0004: 파싱 실행을 독립 worker 런타임으로 분리

## 상태

Accepted

## 배경

콘텐츠 추출, OCR, LLM 추론과 장소 provider 검색은 API 요청 처리보다 오래 실행되고 외부 장애의 영향을
많이 받습니다. 기존 API 내부 비동기 listener는 API blue/green 배포와 생명주기를 공유했고 후속 작업은
인메모리 이벤트 유실 가능성이 있었습니다.

## 결정

- API는 파싱 job을 DB에 생성하고 공개 상태를 조회합니다.
- `nook-api-worker`가 콘텐츠·장소 job을 bounded polling하고 실행합니다.
- 미디어 저장, 장소 썸네일과 태그 처리는 `parsing_follow_up_jobs`에 영속화한 뒤 worker가 처리합니다.
- MySQL job table을 초기 큐로 유지하며 별도 메시지 브로커는 도입하지 않습니다.
- claim 시 증가하는 attempt 번호를 fencing token으로 사용하고 모든 상태·결과 저장을 행 잠금 안에서
  현재 attempt와 비교합니다. 만료된 attempt의 변경은 예외 없이 무시합니다.
- 장소 parsing timeout 기본값은 이미지가 많은 작업을 고려해 10분으로 설정합니다.
- dev와 live는 같은 artifact를 사용하되 프로세스, DB credential, secret과 metric label을 공유하지 않습니다.
- dev worker는 dev VM의 자원 제약 때문에 ops VM에서 실행하고, live worker는 live VM에서 실행합니다.

## 결과

API 배포와 worker 작업 실행이 독립적이며 worker 재시작 후 timeout된 작업을 복구할 수 있습니다. 대신
worker 배포 전에 DDL과 환경별 secret을 준비해야 하고 ops VM은 dev worker 부하를 일부 부담합니다.
