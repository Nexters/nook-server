# NOOK-221 ops 모니터링 스택에 live 환경 추가

## 목적

ops VM의 Prometheus, Loki, Grafana 구성에 live API와 live VM 관측을 추가한다.

## 범위

- live API와 node exporter를 Prometheus target으로 추가한다.
- live VM에 node exporter와 Promtail을 배포한다.
- live VM에서 ops Loki로 전송하는 전용 SSH tunnel을 구성한다.
- dev 대시보드를 기반으로 live overview와 logs 대시보드를 추가한다.
- live API/VM/로그 경보 규칙을 provisioning한다.

## 제외 범위

- RDS MySQL exporter
- Loki `3100` public inbound
- Grafana 외부 공개

## 네트워크

```text
ops Prometheus -> live private IP 192.168.0.216:443, :9100
live Promtail -> 127.0.0.1:3100 -> SSH tunnel -> ops 192.168.0.21:3100
```

API metrics는 live Nginx의 HTTPS endpoint를 private IP로 scrape하고 인증서 검증에는
`api.everynook.co.kr` server name을 사용한다. Loki는 SSH tunnel을 사용하므로 ops 보안 그룹에
`3100` inbound를 추가하지 않는다.

## 배포

live VM에는 전용 SSH key를 `/home/ubuntu/.ssh/nook-ops-tunnel`에 두고 public key를 ops VM의
`authorized_keys`에 등록한다.

```shell
rsync -av --delete --exclude .env ops/live-exporters/ nook-live:/opt/nook/exporters/
ssh nook-live 'sudo cp /opt/nook/exporters/systemd/nook-live-loki-tunnel.service /etc/systemd/system/'
ssh nook-live 'sudo systemctl daemon-reload && sudo systemctl enable --now nook-live-loki-tunnel'
ssh nook-live 'cd /opt/nook/exporters && ./scripts/deploy.sh'

rsync -av --delete --exclude .env ops/monitoring/ nook-ops:/opt/nook/monitoring/
ssh -o ClearAllForwardings=yes nook-ops 'cd /opt/nook/monitoring && ./scripts/deploy.sh'
```

## 검증

```shell
ssh nook-live 'systemctl is-active nook-live-loki-tunnel'
ssh nook-live 'cd /opt/nook/exporters && docker compose ps'
ssh -o ClearAllForwardings=yes nook-ops \
  'curl -fsS --get --data-urlencode "query=up{env=\"live\"}" http://localhost:9090/api/v1/query'
```

Grafana provisioning dashboard UID는 다음과 같다.

- `nook-live-overview`
- `nook-live-logs`

Loki query는 `{env="live", job="nook-api"}`로 확인한다.
