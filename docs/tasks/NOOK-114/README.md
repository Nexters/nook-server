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
- 기본 retention을 Prometheus 15일, Loki 7일로 설정합니다.

## 제외 범위

- 공인 IP 부여
- 도메인, TLS, reverse proxy 구성
- Alertmanager 및 alert rule 구성
- live target 연결
- Alertmanager 및 알림 규칙 구성

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

```shell
ssh nook-dev 'sudo mkdir -p /opt/nook/exporters && sudo chown -R ubuntu:ubuntu /opt/nook/exporters'
rsync -av ops/dev-exporters/ nook-dev:/opt/nook/exporters/
ssh nook-dev 'cd /opt/nook/exporters && ./scripts/deploy.sh'
```

MySQL Exporter는 `mysql-exporter.my.cnf`에 exporter 계정 접속 정보를 둡니다. 이 파일은 Git에 포함하지 않습니다.

Prometheus는 다음 dev target을 scrape합니다.

```text
192.168.0.102:8080  nook-api
192.168.0.102:9100  node exporter
192.168.0.102:9104  mysql exporter
```
