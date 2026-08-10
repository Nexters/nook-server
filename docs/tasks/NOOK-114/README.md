# NOOK-114 ops VM PLG 모니터링 스택 구축

## 목적

dev/live 애플리케이션과 인프라 상태를 관찰하기 위한 ops VM 기반 PLG 모니터링 스택을 구축합니다.

## 범위

- 로컬 SSH config에 `nook-dev`, `nook-ops` host alias를 구성합니다.
- ops VM용 Docker Compose 기반 Prometheus, Grafana, Loki, Promtail 구성 파일을 준비합니다.
- Prometheus dev API scrape target을 구성합니다.
- dev VM용 Node Exporter와 MySQL Exporter 구성 파일을 준비합니다.
- Prometheus dev VM/MySQL scrape target을 구성합니다.
- Grafana datasource provisioning으로 Prometheus와 Loki를 자동 등록합니다.
- Grafana dashboard provisioning으로 `Nook Dev Overview` 대시보드를 자동 등록합니다.
- dev API Docker 로그를 Loki로 수집하기 위한 dev VM Promtail을 구성합니다.
- Grafana alerting provisioning으로 dev API ERROR 로그 Slack 알림을 구성합니다.
- 기본 retention을 Prometheus 15일, Loki 7일로 설정합니다.

## 제외 범위

- 공인 IP 부여
- 도메인, TLS, reverse proxy 구성
- live target 연결
- live 알림 규칙 구성

## 접속 구조

```text
local -> nook-dev public IP -> nook-ops private IP
```

dev VM에서 ops VM SSH 포트 접근은 확인됐습니다.

```text
nook-dev private IP: 192.168.0.102
nook-ops private IP: 192.168.0.21
```

ops VM private key는 로컬의 `~/.ssh/SSH_KeyPair-260722234747.pem`에 둡니다.

## SSH

`~/.ssh/config`에 다음 host alias를 둡니다.

```sshconfig
Host nook-dev
  HostName 1.201.120.75
  User ubuntu
  IdentityFile ~/.ssh/nook-server-dev.pem
  IdentitiesOnly yes

Host nook-ops
  HostName 192.168.0.21
  User ubuntu
  IdentityFile ~/.ssh/SSH_KeyPair-260722234747.pem
  IdentitiesOnly yes
  ProxyJump nook-dev
  LocalForward 3000 localhost:3000
  LocalForward 9090 localhost:9090
```

## 배포

ops VM에 Docker와 Compose plugin이 설치되어 있어야 합니다.

```shell
ssh nook-ops 'sudo mkdir -p /opt/nook/monitoring && sudo chown -R ubuntu:ubuntu /opt/nook'
rsync -av ops/monitoring/ nook-ops:/opt/nook/monitoring/
ssh nook-ops 'bash /opt/nook/monitoring/scripts/install-docker-ubuntu.sh'
ssh nook-ops 'cd /opt/nook/monitoring && cp -n .env.example .env'
ssh nook-ops 'cd /opt/nook/monitoring && ./scripts/deploy.sh'
```

`.env`의 `GRAFANA_ADMIN_PASSWORD`는 배포 전에 운영용 값으로 바꿉니다.
Slack 알림을 사용하려면 `.env`의 `SLACK_ALERT_WEBHOOK_URL`에 Slack Incoming Webhook URL을 설정합니다.
dev VM Promtail이 Loki로 로그를 push할 수 있도록 Loki는 기본적으로 ops private IP인 `192.168.0.21:3100`에 바인드됩니다.

## 검증

```shell
ssh nook-ops 'docker compose -f /opt/nook/monitoring/docker-compose.yml ps'
ssh nook-ops 'curl -fsS http://localhost:9090/-/ready'
ssh nook-ops 'curl -fsS http://localhost:3000/api/health'
```

로컬에서는 터널이 열린 상태에서 다음 URL로 접근합니다.

```text
http://localhost:3000
http://localhost:9090
```

Prometheus target은 `Status > Target health`에서 `nook-api` job의 `dev` target을 확인합니다.

## dev exporter 배포

dev VM에는 Node Exporter와 MySQL Exporter를 별도 compose로 실행합니다.
같은 compose에서 Promtail도 실행해 `nook-dev-api` 컨테이너의 Docker 로그를 Loki로 전송합니다.
dev VM에서 ops VM의 Loki 포트로 직접 접근할 수 없는 환경에서는 systemd SSH 터널을 먼저 설치합니다.

```shell
ssh nook-dev 'sudo mkdir -p /opt/nook/exporters && sudo chown -R ubuntu:ubuntu /opt/nook/exporters'
rsync -av ops/dev-exporters/ nook-dev:/opt/nook/exporters/
ssh nook-dev 'sudo cp /opt/nook/exporters/systemd/nook-dev-loki-tunnel.service /etc/systemd/system/'
ssh nook-dev 'sudo systemctl daemon-reload && sudo systemctl enable --now nook-dev-loki-tunnel'
ssh nook-dev 'cd /opt/nook/exporters && ./scripts/deploy.sh'
```

MySQL Exporter는 `mysql-exporter.my.cnf`에 exporter 계정 접속 정보를 둡니다. 이 파일은 Git에 포함하지 않습니다.
Promtail의 Loki push URL은 `ops/dev-exporters/.env`의 `LOKI_PUSH_URL`로 조정할 수 있습니다. 기본값은
dev VM의 SSH 터널을 통해 `http://127.0.0.1:3100/loki/api/v1/push`로 전송합니다.
애플리케이션 로그 라인에서 `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`를 추출해 Loki `level` 라벨로 저장합니다.

Prometheus는 다음 dev target을 scrape합니다.

```text
192.168.0.102:8080  nook-api
192.168.0.102:9100  node exporter
192.168.0.102:9104  mysql exporter
```

Loki 로그 수집과 Slack 알림은 Grafana에서 다음 기준으로 확인합니다.

```logql
{env="dev", job="nook-api"}
{env="dev", job="nook-api", level="ERROR"}
sum by (level) (rate({env="dev", job="nook-api", level=~"TRACE|DEBUG|INFO|WARN|ERROR"}[5m]))
sum(count_over_time({env="dev", job="nook-api"} |~ "(?i)(ERROR|Unexpected API exception)" [5m]))
```

`Nook Dev Logs` 대시보드는 dev API 로그를 독립적으로 조회합니다.

- `Level` 변수: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` 로그 레벨 필터
- Grafana time picker: 조회 시간 범위 필터
- `API Log Rate by Level`: 로그 레벨별 초당 로그 수
- `ERROR Logs`, `WARN Logs`: 조회 시간 범위의 레벨별 로그 수
- `API Logs`: 선택한 레벨과 시간 범위의 dev API 로그
