# NOOK-113 애플리케이션 Prometheus 메트릭 노출 설정

## 목적

PLG 기반 모니터링 구축의 첫 단계로 API 애플리케이션이 Prometheus scrape 대상이 될 수 있게 actuator
Prometheus endpoint를 노출합니다.

## 범위

- API 실행 모듈에 Prometheus meter registry를 추가합니다.
- API actuator web exposure에 `prometheus`를 포함합니다.
- `MANAGEMENT_PORT` 환경변수로 management endpoint 포트를 분리할 수 있게 설정합니다.
- API 보안 설정에서 `/actuator/prometheus`를 인증 없이 접근 가능한 endpoint에 포함합니다.
- Prometheus endpoint 접근 보안 테스트를 추가합니다.

## 제외 범위

- PLG VM provisioning
- Prometheus, Grafana, Loki, Promtail 또는 Alloy 구성
- Node Exporter, MySQL Exporter 설치
- Grafana dashboard 및 alert rule 작성
- dev/live 보안그룹 실제 변경
- 배포 파이프라인 변경
- batch Prometheus scrape endpoint 구성

## 설계

`micrometer-registry-prometheus`를 runtime dependency로 추가해 Spring Boot actuator가
`/actuator/prometheus` endpoint를 자동 구성하게 합니다.

기본 management port는 기존 애플리케이션 포트와 동일하게 유지합니다. dev/live 배포에서 모니터링 트래픽을
분리하려면 `MANAGEMENT_PORT=18080`처럼 환경변수를 주입하고, 해당 포트는 같은 VPC 안의 PLG VM private IP에서만
접근하도록 보안그룹에서 제한합니다.

batch 애플리케이션은 `WebApplicationType.NONE`으로 실행되므로 HTTP scrape endpoint를 열려면 런타임 성격을
바꿔야 합니다. batch 메트릭은 long-running management server 전환 또는 Pushgateway 사용 여부를 별도 작업에서
결정합니다.

## 성공 기준

- API 애플리케이션에서 `/actuator/prometheus`가 노출됩니다.
- 기존 API 인증 정책은 유지됩니다.
- Prometheus endpoint는 인증 없이 scrape 가능합니다.
- `./gradlew check`가 성공합니다.

## 검증

```shell
./gradlew check
```

배포 후 dev VM에서 다음을 확인합니다.

```shell
curl -fsS http://localhost:${MANAGEMENT_PORT:-8080}/actuator/prometheus | head
```

PLG VM에서는 dev/live private endpoint 기준으로 scrape 가능해야 합니다.
