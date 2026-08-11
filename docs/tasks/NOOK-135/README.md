# NOOK-135 Slack alert 채널과 ERROR 로그 채널 분리

## 목적

Slack 알림을 목적별로 분리합니다.

- alert 채널: Grafana가 감지한 지표 및 로그 기반 이상징후 알림
- error log 채널: dev API ERROR 로그와 이어지는 stack trace 원문

## 범위

- Grafana alert contact point의 `SLACK_ALERT_WEBHOOK_URL`은 alert 채널 webhook으로 사용합니다.
- dev exporter에 `error-log-forwarder`를 추가해 dev API ERROR 로그 원문을 error log 채널로 전송합니다.
- `ERROR` 로그 라인부터 다음 로그 레벨 라인 전까지를 하나의 Slack 메시지 code block으로 묶습니다.
- webhook URL은 `.env`에만 저장하고 레포에는 커밋하지 않습니다.

## 제외 범위

- live 환경 로그 전송
- 애플리케이션 로그 포맷 변경
- Slack webhook URL 또는 bot token 커밋

## 채널

```text
alert     C0BJMSDFUHH
error log C0BP0H0419S
```

## dev exporter 환경 변수

```dotenv
ERROR_LOG_CONTAINER_NAME=nook-dev-api
ERROR_LOG_SLACK_WEBHOOK_URL=
ERROR_LOG_MAX_BYTES=3500
ERROR_LOG_FLUSH_SECONDS=2
```

## 검증

- `python3 -m py_compile ops/dev-exporters/error-log-forwarder/forward_error_logs.py`
- `ruby -e 'require "yaml"; Dir["ops/**/*.yml"].each { |f| YAML.load_file(f) }'`
- `./gradlew check`
- secret 패턴 스캔
