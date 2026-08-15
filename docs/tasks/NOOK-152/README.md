# NOOK-152 Grafana DatasourceError 반복 알림과 로그 통계 불일치 수정

## 목적

일시적인 Prometheus/Loki 조회 실패가 모든 업무 Alert의 `DatasourceError`로 증폭되는 문제를 줄이고,
로그 대시보드의 ERROR/WARN 통계를 실제 조회 범위와 일치시킵니다.

## 원인

- Grafana-managed Alert의 `execErrState: Error`는 조회 실패마다 원래 Alert와 독립적인
  `DatasourceError`를 생성합니다.
- 기존 정책은 모든 `DatasourceError`를 같은 이름으로 묶어 1시간마다 반복 전송했습니다.
- datasource provisioning이 Grafana 시작 시 기존 Prometheus/Loki datasource를 삭제한 뒤 다시
  생성해, 시작 구간의 조회 실패 가능성을 키웠습니다.
- 로그 stat은 `count_over_time(...[$__range])`를 range 쿼리로 실행해 조회 종료 시점에는 데이터가
  없어도 과거의 마지막 non-null 값을 표시했습니다.

## 범위

- ERROR/WARN stat을 instant 쿼리로 변경합니다.
- 업무 Alert의 실행 오류 상태를 Grafana provisioning enum인 `KeepLast`로 변경합니다.
- Prometheus/Loki 연결 상태를 확인하는 전용 Alert를 추가합니다.
- `DatasourceError`를 datasource와 원본 규칙별로 그룹화하고 최초 알림을 2분 지연하며 반복 주기를
  6시간으로 분리합니다.
- datasource 삭제 후 재생성을 제거합니다.
- Grafana 시작 전 Prometheus/Loki readiness를 최대 60초 확인합니다. 제한 시간 뒤에는 진단을 위해
  Grafana를 시작합니다.

## 제외 범위

- 애플리케이션 로그 레벨 또는 로그 포맷 변경
- dev API 기능/API 계약 변경
- live 환경 Alert 임계치 도입

## 성공 기준

- 조회 범위에 ERROR/WARN 로그가 없으면 stat이 0을 표시합니다.
- 일시적 datasource 조회 실패가 모든 업무 Alert의 `DatasourceError`로 확산되지 않습니다.
- 2분 이상 지속되는 Prometheus/Loki 실행 오류는 전용 규칙의 `DatasourceError`로 식별됩니다.
- alert provisioning YAML, dashboard JSON, Docker Compose 구성이 유효합니다.

## 검증

```shell
ruby -e 'require "yaml"; Dir["ops/**/*.yml"].each { |file| YAML.load_file(file) }'
jq empty ops/monitoring/grafana/dashboards/*.json
docker compose -f ops/monitoring/docker-compose.yml config
./gradlew check
```

ops VM 반영 후 Grafana rules API에서 모든 규칙의 health/state를 확인하고, Loki와 Prometheus의
`vector(1)` 쿼리 및 로그 stat의 instant 쿼리 결과를 직접 비교합니다.
