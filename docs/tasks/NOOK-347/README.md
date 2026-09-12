# NOOK-347 Slack 유지 및 환경별 Discord 알림 추가

## 목적

기존 Slack 일반 알림/ERROR 로그 채널을 유지하면서 Discord에 dev/live 일반 알림과 ERROR 로그를 각각 전달한다.

## 범위

- Grafana `env=dev`, `env=live` 알림을 해당 환경의 Slack+Discord contact point로 라우팅한다.
- Slack 웹훅은 기존 값을 재사용한다. Slack에서는 dev/live가 계속 같은 일반 알림 채널로 전달된다.
- `DatasourceError`의 그룹과 알림 주기를 유지하며 환경별 하위 라우팅을 적용한다.
- `env=ops` 또는 환경 미지정 알림은 기존 Slack 경로를 유지한다.
- dev 및 live blue/green ERROR 로그 전송기에 Discord 동시 발송을 추가한다.
- `ops/error-log-forwarder/forward_error_logs.py`를 모든 환경에서 공유한다. 환경별 소스 복제 없이 `ERROR_LOG_ENV`와 웹훅 값만 주입한다.
- Grafana도 Slack/Discord별 메시지 설정을 YAML anchor로 공유하고 환경별 웹훅만 재정의한다.
- 요청 컨텍스트, 스택 트레이스, 요청 ID로 필터링된 Grafana 링크를 Discord embed에 표시한다. 제목을 누르면 Grafana로 이동한다.
- Discord 본문/필드 길이를 제한하고 코드 블록과 멘션을 안전하게 처리한다.
- 한 provider의 네트워크/HTTP 오류가 다른 provider 발송을 막지 않도록 처리하고, 오류 로그에 웹훅 URL을 노출하지 않는다.

## 서버 설정

| 위치 | 키 | 용도 |
| --- | --- | --- |
| ops monitoring `.env` | `SLACK_DEV_ALERT_WEBHOOK_URL`, `SLACK_LIVE_ALERT_WEBHOOK_URL` | dev/live 일반 알림 — 기존 Slack 웹훅과 같은 값 |
| ops monitoring `.env` | `DISCORD_DEV_ALERT_WEBHOOK_URL` | dev 일반 알림 |
| ops monitoring `.env` | `DISCORD_LIVE_ALERT_WEBHOOK_URL` | live 일반 알림 |
| dev exporters `.env` | `ERROR_LOG_DISCORD_WEBHOOK_URL` | dev ERROR 로그 |
| live exporters `.env` | `ERROR_LOG_DISCORD_WEBHOOK_URL` | live blue/green ERROR 로그 |

실제 웹훅 값은 서버 `.env`에만 둔다. dev/live의 Slack 로그 웹훅 값도 동일하게 유지한다. 환경별 Slack 일반 알림 값이 비어 있으면 `SLACK_ALERT_WEBHOOK_URL`로 대체한다. ERROR 로그의 Discord 값이 비어 있으면 기존 Slack 전송만 수행한다. Grafana 환경별 contact point에는 두 일반 알림 웹훅이 필요하다.

공통 전송기 디렉터리는 exporters와 나란히 배포한다. 서버에서는 `/opt/nook/error-log-forwarder/forward_error_logs.py`를 마운트한다. 각 exporters `.env`에 `ERROR_LOG_ENV=dev` 또는 `ERROR_LOG_ENV=live`를 둔다.

```bash
python3 -m unittest discover -s ops/error-log-forwarder -v
```

## 제외 범위

- Slack 제거 또는 Slack dev/live 채널 분리
- API/DB/워커 변경
- 공용 ops 알림의 새 Discord 채널 지정
- ERROR 로그 영속 큐 또는 재시도 추가: 기존처럼 발송 실패는 기록하며, 별도 재전송하지 않는다.

## 성공 기준 및 검증

- Python 단위 테스트 17개 통과: 기존 로그 파싱/컨테이너 교체/Slack 메시지, Discord 컨텍스트와 환경별 링크, 한글·이모지 및 길이 제한, 양쪽 발송과 실패 격리, Discord 미설정 시 Slack 유지.
- 변경 YAML 파싱, 배포 스크립트 구문, `git diff --check` 통과.
- `./gradlew check -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' --max-workers=2` 통과. 기본 512MiB 실행은 Gradle GC thrashing으로 중단돼 실행 메모리를 늘렸다.
- 배포 전 대상 파일이 `origin/main`과 일치함을 확인했다.
- Grafana 및 dev/live ERROR 로그 전송기만 선택적으로 재생성했다. 이미지를 pull하지 않았으며 API/워커는 재시작하지 않았다.
- Grafana health 정상, 실제 등록 contact point와 환경 라우팅 확인.
- dev/live 전송기의 구조화 테스트 로그를 Slack과 Discord에 전송하여 Slack HTTP 200 및 Discord 메시지 생성 응답을 확인했다.
- Grafana 13.2 수신처 테스트 API로 dev/live의 기존 Slack 및 Discord integration을 각각 검사하여 4개 모두 `success`를 확인했다. 실제 등록된 정책에서 환경별 matcher와 공용 Slack fallback을 확인했다.
- ERROR 로그 전송기 dev/live blue/green 3개에서 공통 소스 해시, 환경값, 두 웹훅 설정을 확인했다.
- 서버 실행 설정에서 dev/live가 공통 소스를 마운트하고 환경변수로 구분됨을 확인했다. Slack 일반 및 로그 웹훅의 dev/live 값이 각각 같음을 비교했다.

## 롤백

서버의 `/opt/nook/{monitoring,exporters}/backups/NOOK-347`에 보관한 변경 전 파일을 복원하고 해당 Grafana 또는 ERROR 로그 전송기 서비스만 다시 생성한다. 추가한 Discord 환경변수는 제거할 수 있으며 기존 Slack 값은 변경하지 않는다.
