# NOOK-228 ERROR 로그 포워더 JSON 파싱 수정

## 목적

dev API가 JSON 로그를 출력하는 환경에서 ERROR 로그와 stack trace가 Slack error log 채널로 전달되지 않는 문제를 수정한다.

## 원인

error-log-forwarder가 일반 텍스트 로그의 `ERROR` 패턴만 인식해 JSON 로그의 `level` 및 `stack_trace` 필드를 처리하지 못한다.

## 범위

- 애플리케이션 JSON 로그의 `level` 필드 기반 ERROR 탐지
- `message`와 `stack_trace`를 Slack 메시지 본문에 포함
- 기존 일반 텍스트 로그 호환 유지
- JSON ERROR/INFO, 일반 텍스트 ERROR, 잘못된 JSON에 대한 단위 테스트 추가

## 제외 범위

- Slack webhook 또는 채널 변경
- Grafana alert 규칙 변경
- live 환경 배포

## 성공 기준

- 실제 dev JSON 로그 형식의 `Unexpected API exception`이 stack trace와 함께 전송 대상으로 만들어진다.
- INFO 로그는 전송 대상에서 제외된다.
- Python 단위 테스트와 `./gradlew check`가 통과한다.

## 검증

- `python3 -m unittest discover -s ops/dev-exporters/error-log-forwarder -p 'test_*.py' -v`
- `python3 -m py_compile ops/dev-exporters/error-log-forwarder/forward_error_logs.py ops/dev-exporters/error-log-forwarder/test_forward_error_logs.py`
- `./gradlew check`
