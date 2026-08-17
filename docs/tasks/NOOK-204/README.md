# NOOK-204 dev VM 메모리 상한과 swap 구성

## 목적

2GB dev VM에서 API와 MySQL이 호스트 메모리를 제한 없이 사용해 발생하는 지속적인 메모리 경보와
OOM 위험을 줄입니다.

## 범위

- API 컨테이너: memory 640MiB, reservation 512MiB, memory+swap 768MiB
- API JVM: initial heap 128MiB, maximum heap 384MiB
- MySQL 컨테이너: memory 768MiB, reservation 512MiB, memory+swap 1GiB
- MySQL: `max_connections=50`
- dev VM: 2GiB `/swapfile`, `vm.swappiness=10`
- overview dashboard: JVM heap 사용률과 used/committed/max 추이
- alert: heap 85% 초과 10분 warning, 95% 초과 5분 critical

MySQL은 변경 전 약 601MiB를 사용하고 기동 중 일시적으로 사용량이 증가하므로 640MiB 대신
768MiB로 제한합니다. 변경 전 최대 동시 연결 이력은 20개이므로 dev의 연결 상한은 50개로
설정합니다.

## 배포

```shell
ops/dev-runtime/scripts/deploy.sh nook-dev
```

배포 스크립트는 서버의 기존 `.env`, AWS credential, MySQL data volume을 변경하지 않습니다.

## 검증

```shell
bash -n ops/dev-runtime/scripts/*.sh
docker compose -f ops/dev-runtime/api/compose.yml config
docker compose -f ops/dev-runtime/mysql/compose.yml config
./gradlew check
```

서버에서는 다음 항목을 확인합니다.

- API와 MySQL 컨테이너 health가 `healthy`
- API memory limit 640MiB와 MySQL memory limit 768MiB
- JVM maximum heap 약 384MiB
- MySQL `max_connections=50`
- 2GiB swap 활성화와 `/etc/fstab` 등록
- actuator health `UP` 및 OOM kill 없음
- Grafana overview의 JVM heap 패널과 두 heap alert rule provision

## Rollback

이전 Compose 파일로 API와 MySQL을 재기동한 뒤 swap이 더 이상 필요하지 않은 경우 dev VM에서
다음 스크립트를 실행합니다.

```shell
ops/dev-runtime/scripts/rollback-swap.sh
```

swap 사용량이 남아 있으면 `swapoff`가 메모리를 다시 RAM으로 옮기므로, 충분한 가용 메모리를
확인한 뒤 rollback합니다.
