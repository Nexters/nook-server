# NOOK-347 Slack 제거 및 환경별 Discord 알림 전환

## 목적

Slack 발송을 제거하고 Discord에서 dev/live 일반 알림과 ERROR 로그를 분리한다. 공용 ops 및 환경 미지정 알림은 별도 공용 Discord 채널로 전달한다.

## 범위

- ERROR 로그 전송기는 `ops/error-log-forwarder/forward_error_logs.py` 하나를 dev/live에서 공유한다. 환경 이름과 웹훅을 환경변수로 주입한다.
- Slack 메시지 생성, HTTP 발송, Compose 환경변수, 배포 전 검증을 제거한다.
- Grafana의 기존 Slack 수신처 4개 UID를 `deleteContactPoints`로 정리한다. Discord 수신처와 정책 적용 후 기존 수신처가 삭제된다.
- Grafana Discord 메시지 형식은 공통으로 관리하고 dev/live/ops 웹훅만 다르게 설정한다.
- `env=dev` → dev 일반, `env=live` → live 일반, 나머지 환경 및 환경 미지정 → 공용 일반 채널로 전달한다.
- `DatasourceError`의 기존 그룹과 알림 주기를 유지하며 같은 환경별 라우팅을 적용한다.
- 로그의 요청 컨텍스트, 스택 트레이스, 요청 ID로 필터링한 Grafana 링크를 embed에 표시한다. 제목을 누르면 Grafana로 이동한다.
- Discord 길이 제한과 멘션 억제를 적용하며, 전송 실패는 웹훅 원문 없이 기록한다.

## 서버 설정

| 위치 | 키 | 용도 |
| --- | --- | --- |
| ops monitoring `.env` | `DISCORD_DEV_ALERT_WEBHOOK_URL` | dev 일반 알림 |
| ops monitoring `.env` | `DISCORD_LIVE_ALERT_WEBHOOK_URL` | live 일반 알림 |
| ops monitoring `.env` | `DISCORD_OPS_ALERT_WEBHOOK_URL` | 공용 ops/환경 미지정 일반 알림 |
| dev exporters `.env` | `ERROR_LOG_DISCORD_WEBHOOK_URL`, `ERROR_LOG_ENV=dev` | dev ERROR 로그 |
| live exporters `.env` | `ERROR_LOG_DISCORD_WEBHOOK_URL`, `ERROR_LOG_ENV=live` | live blue/green ERROR 로그 |

실제 웹훅 원문은 Git에 저장하지 않는다. Discord 웹훅은 배포 필수값이며 Slack 값은 더 이상 사용하지 않는다.
공통 전송기 디렉터리는 exporters와 나란히 배포한다. 서버에서는 `/opt/nook/error-log-forwarder/forward_error_logs.py`를 마운트한다.

## 제외 범위

- API 계약, DB, 워커 변경
- 알림 서비스의 GitHub Actions 자동 배포 추가
- ERROR 로그 영속 큐 또는 재시도 추가

## 검증

```bash
python3 -m unittest discover -s ops/error-log-forwarder -v
./gradlew check -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' --max-workers=2
```

- Python 14개 테스트: 파싱/컨테이너 교체, 컨텍스트/환경별 링크, 길이 제한, Discord 단독 발송, 실패 후 다음 로그 처리, 빈 메시지 처리.
- YAML 파싱 및 dev/live/ops/환경 미지정 라우팅과 DatasourceError 주기 검증.
- 배포 스크립트 구문, diff 공백, 웹훅 원문 미포함 확인.

## 배포 상태 및 순서

기존 Slack+Discord 병행 버전은 dev/live 서버에 수동 반영된 상태다. 이번 Slack 제거 및 공용 Discord 채널 추가는 Git 변경이며 아직 서버에 적용하지 않았다.

1. `develop` 코드 반영 및 `main` PR 리뷰/머지.
2. ops monitoring `.env`에 사용자 제공 공용 웹훅을 `DISCORD_OPS_ALERT_WEBHOOK_URL`로 설정.
3. Git에서 확정한 공통 전송기, Compose, Grafana provisioning 파일을 해당 서버에 배포.
4. Grafana 및 ERROR 로그 전송기만 재생성하고 Discord 수신과 Slack 미발송 확인.

현재 GitHub Actions는 exporters/monitoring 변경을 자동 배포하지 않는다. 서버 반영은 별도 작업으로 수행한다.

## 롤백

이전 Git 버전의 전송기 및 Compose/provisioning 파일을 복원하고 필요한 환경변수를 복구한 뒤 해당 알림 서비스만 재생성한다. Slack 수신처가 삭제된 뒤에는 이전 provisioning을 다시 적용해야 한다.
