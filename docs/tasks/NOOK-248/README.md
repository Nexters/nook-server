# NOOK-248 ERROR 로그 포워더의 API 컨테이너 교체 추적 수정

## 목적

dev API 재배포로 컨테이너 ID가 변경된 뒤에도 error-log-forwarder가 새 컨테이너의 ERROR 로그와 stack trace를 Slack으로 계속 전달하도록 수정한다.

## 원인

error-log-forwarder가 시작 시 Docker 로그 파일 경로를 한 번만 결정하고 열린 파일을 계속 따라가므로, API 컨테이너 재생성 후 삭제된 이전 로그 파일 descriptor를 유지한다.

## 범위

- API 컨테이너 로그 경로 변경 및 파일 삭제 감지
- 동일 경로의 Docker 로그 rotation 감지
- 새 컨테이너 로그 파일로 자동 재연결
- 최초 시작 시 과거 로그 소급 전송 방지
- 컨테이너 교체 후 새 로그 누락 방지
- 관련 단위 테스트 추가
- dev exporter 수동 재배포 및 상태 검증

## 제외 범위

- API 컨테이너 강제 재배포를 이용한 운영 테스트
- Slack webhook 및 채널 변경
- live 환경 변경

## 성공 기준

- API 컨테이너 ID 또는 로그 파일 inode가 변경되면 forwarder가 현재 Docker JSON 로그 파일을 자동으로 연다.
- 최초 연결은 기존 파일의 끝에서 시작하고, 재연결은 새 파일의 처음부터 읽는다.
- 새 파일에서 발생한 JSON ERROR의 `message`와 `stack_trace`를 전송 대상으로 만든다.
- 기존 파서 테스트와 전체 Gradle 검증이 통과한다.

## 검증

- `python3 -m unittest discover -s ops/dev-exporters/error-log-forwarder -p 'test_*.py' -v`
- `python3 -m py_compile ops/dev-exporters/error-log-forwarder/forward_error_logs.py ops/dev-exporters/error-log-forwarder/test_forward_error_logs.py`
- `./gradlew check`
