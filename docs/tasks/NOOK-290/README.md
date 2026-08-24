# NOOK-290 파싱 파이프라인 워커 런타임 분리

## 목적

API 프로세스가 요청과 상태 조회만 담당하고, 콘텐츠·장소 파싱과 후속 처리는 독립 worker가 담당하도록
실행 경계를 분리합니다. API 배포가 진행 중인 작업을 중단하지 않으며 worker의 동시성과 처리량을
명시적으로 제한합니다.

## 범위

- `nook-api-worker` 독립 Spring Boot 런타임
- 콘텐츠·장소 작업의 bounded polling, claim, retry 및 timeout 복구
- attempt 기반 fencing과 10분 장소 처리 timeout으로 오래된 실행의 결과 덮어쓰기 방지
- 미디어·장소 썸네일·태그 후속 처리의 영속 작업 전환
- API 런타임에서 parsing scheduler와 executor 제거
- ops VM의 dev worker와 live VM의 live worker 배포 구성
- worker health, Prometheus metric 및 로그 수집
- Gabia Registry의 cross-repository blob mount 제약을 피하기 위해 API와 같은 image repository에서
  `worker-dev-*`, `worker-prod-*` 전용 tag로 worker artifact 구분

## 제외 범위

- Kafka, SQS 등 외부 메시지 브로커
- live 전용 worker VM
- 파싱 정책 및 공개 API 변경

## 성공 기준

- API 애플리케이션 컨텍스트에는 parsing dispatcher가 존재하지 않습니다.
- worker 재시작 후 미완료 작업이 다시 처리되어 최종 상태로 수렴합니다.
- timeout 후 새 attempt가 claim되면 과거 attempt의 진행·완료·재시도·실패 변경은 안전하게 무시됩니다.
- 조회 batch와 콘텐츠·장소 실행 동시성이 설정으로 제한됩니다.
- API blue/green 배포와 worker 배포 생명주기가 분리됩니다.
- dev와 live worker가 DB, secret, metric label을 공유하지 않습니다.

## 검증

- `./gradlew detekt`
- `./gradlew test`
- `./gradlew check`
- dev에서 게시물 생성부터 콘텐츠·장소·후속 처리 완료까지 확인

## 배포 순서

1. 환경 DB에 `ddl/up.sql`을 적용합니다.
2. ops VM `/opt/nook/dev-worker/.env`와 live VM `/opt/nook/live-worker/.env`를 준비합니다.
3. dev worker의 `DB_URL`은 dev private IP `192.168.0.102:3806`만 사용하고, 3806 inbound는 ops VM
   `192.168.0.21/32`로 제한합니다.
4. API 이미지를 먼저 배포해 API 내부 dispatcher를 제거합니다.
5. 같은 버전의 worker 이미지를 배포합니다.
6. `up{job="nook-worker"}`와 outstanding/follow-up job 상태가 수렴하는지 확인합니다.

API와 worker가 서로 다른 버전을 장시간 혼용하지 않습니다. rollback 시에도 API와 worker를 같은 commit의
이미지로 되돌리며, 테이블 rollback은 두 런타임이 이전 버전으로 돌아간 뒤 수행합니다.
