# NOOK-145 dev Grafana 핵심 Alert 규칙 확장

## 목적

dev 환경에서 API, VM, MySQL 장애와 주요 장애 징후를 조기에 감지할 수 있도록 Grafana Alert 규칙을 확장합니다.

## 범위

- API, MySQL exporter, node exporter의 scrape 실패를 감지합니다.
- HTTP 5xx 증가와 p95 latency 증가를 최소 요청량 조건과 함께 감지합니다.
- VM 루트 디스크와 메모리 사용률을 감지합니다.
- Hikari connection pool 고갈과 connection 획득 timeout을 감지합니다.
- 기존 API ERROR 로그 Alert와 Slack firing/resolved 알림 경로를 유지합니다.
- dev 메모리 warning 임계치는 `MemAvailable` 기준 사용률 90%로 설정합니다.

## Alert 기준

| Alert | 조건 | 지속 | 등급 |
| --- | --- | --- | --- |
| API Down | `up{job="nook-api", env="dev"} < 1` | 2분 | critical |
| MySQL Exporter Down | `up{job="mysql", env="dev"} < 1` | 5분 | warning |
| Node Exporter Down | `up{job="node", env="dev"} < 1` | 5분 | warning |
| HTTP 5xx 증가 | 5분간 5xx 5건 이상 또는 요청 20건 이상이면서 5xx 비율 5% 이상 | 5분 | warning |
| HTTP p95 지연 | 10분간 요청 20건 이상이면서 p95 1초 이상 | 10분 | warning |
| 디스크 부족 | 루트 파일시스템 사용률 80% 초과 | 15분 | warning |
| 디스크 위험 | 루트 파일시스템 사용률 90% 초과 | 5분 | critical |
| 메모리 부족 | `MemAvailable` 기준 사용률 90% 초과 | 10분 | warning |
| DB pool 고갈 | pending 발생 또는 pool 사용률 90% 이상 | 3분 | critical |
| DB connection timeout | 5분간 Hikari timeout 증가 | 즉시 | critical |
| API ERROR 로그 | 5분간 ERROR 로그 발생 | 1분 | warning |

## 제외 범위

- HTTP Request Rate 자체에 대한 Alert
- live 환경 Alert 배포
- 외부 uptime monitor와 dead-man's-switch
- VM 증설 또는 swap 구성
- 애플리케이션 비즈니스 지표 Alert
- 기존 Grafana 대시보드 개편

## 성공 기준

- 모든 규칙이 Grafana에 provisioning되고 활성화됩니다.
- Grafana rules API에서 모든 규칙의 평가 상태가 `health=ok`입니다.
- 정상 상태에서 firing 또는 pending 상태인 규칙이 없습니다.
- 저트래픽 dev 환경에서 HTTP 5xx 비율과 latency 규칙이 불필요하게 발화하지 않습니다.
- 기존 Slack contact point로 firing 및 resolved 메시지를 전송할 수 있습니다.
- 기존 API ERROR 로그 Alert가 유지됩니다.

## 검증

```shell
ruby -e 'require "yaml"; Dir["ops/**/*.yml"].each { |file| YAML.load_file(file) }'
./gradlew check
```

배포 후 Grafana provisioning API와 rules API에서 규칙 수, 설정, `health`, `state`, `lastEvaluation`을 확인합니다. 각 PromQL은 Prometheus API에서 직접 실행해 구문과 현재 값을 확인합니다.
