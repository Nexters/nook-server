# live ERROR stack trace Slack 전달 추가

## 목적

live API에서 발생한 ERROR 로그와 stack trace를 기존 Slack ERROR 로그 채널로 전달한다.

## 범위

- live exporter stack에 blue/green API 로그 forwarder를 추가한다.
- 기존 dev forwarder와 동일한 JSON `level` 및 `stack_trace` 처리를 적용한다.
- live exporter 배포 전에 Slack webhook 설정을 검증한다.
- blue/green 슬롯 전환 전후 양쪽 컨테이너 로그를 추적한다.

## 제외 범위

- Grafana Alert 메시지 템플릿 변경
- 애플리케이션 로깅 형식 변경
- 의도적인 live ERROR 발생을 통한 Slack 전송 시험

## 성공 기준

- live blue/green API 컨테이너에서 발생한 ERROR와 stack trace가 Slack ERROR 로그 채널로 전달된다.
- 슬롯 전환 시에도 새 컨테이너 로그 파일을 계속 추적한다.
- Slack webhook이 설정되지 않은 live exporter 배포는 중단된다.
- dev ERROR 로그 전달 동작은 변경되지 않는다.

## 검증

- `python3 -m unittest discover -s ops/dev-exporters/error-log-forwarder -p 'test_*.py' -v`
- `python3 -m py_compile ops/dev-exporters/error-log-forwarder/forward_error_logs.py ops/live-exporters/error-log-forwarder/forward_error_logs.py ops/dev-exporters/error-log-forwarder/test_forward_error_logs.py`
- live VM의 Docker Compose를 사용한 `docker compose config --quiet`
- `bash -n ops/live-exporters/scripts/deploy.sh`
- `./gradlew check`
- live VM에서 blue/green forwarder 실행, 슬롯별 로그 파일 추적, Promtail/Loki tunnel 및 active API health 확인

## 운영 반영

- 적용일: 2026-08-24
- 적용 대상: live VM `/opt/nook/exporters`
- 결과: blue/green forwarder가 각 API 로그 파일을 추적하며, 재시작 횟수 0과 active API health `UP`을 확인했다.
