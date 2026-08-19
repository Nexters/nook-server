# NOOK-240 Live Admin Web 배포 및 CI 자동화

## 목적

ops VM에 Live Admin Web을 최초 배포하고, `main` 브랜치의 Admin Web 변경이 운영 환경 승인 후
자동 배포되도록 CI를 구성합니다.

## 범위

- ops VM에 `nook-live-admin-web` 컨테이너를 배포합니다.
- Live Admin Web은 ops VM의 `127.0.0.1:8082`에만 바인딩합니다.
- Dev와 Live가 서로 교체되지 않도록 Compose project name을 환경별로 분리합니다.
- `main` 브랜치의 Admin Web 변경 시 production 이미지를 빌드하고 `live` environment 승인을 거쳐
  ops VM에 배포합니다.
- `admin.everynook.co.kr`의 Cloudflare Access와 Tunnel 라우팅을 점검합니다.

## 제외 범위

- Admin API 기능 또는 Admin Web 화면 변경
- Cloudflare Access 정책 변경
- Live API 배포 방식 변경

## 성공 기준

- ops VM에서 `nook-live-admin-web` 컨테이너가 정상 실행됩니다.
- ops VM의 `http://127.0.0.1:8082/health`가 성공합니다.
- `main` 브랜치의 Admin Web 변경 시 production 이미지가 자동 배포됩니다.
- `admin.everynook.co.kr`이 Cloudflare Access로 보호되고 Live Admin Web과 Admin API로 라우팅됩니다.

## 검증

```shell
bash -n .github/scripts/deploy-admin-web.sh
docker compose -f ops/admin-web/docker-compose.yml config
pnpm --dir nook-admin-web install --frozen-lockfile
pnpm --dir nook-admin-web build
```

ops VM 배포 후 다음을 확인합니다.

```shell
docker ps --filter name=nook-live-admin-web
curl -fsS http://127.0.0.1:8082/health
```

## 점검 결과

- ops VM의 Dev Admin(`8081`)과 Live Admin(`8082`) health check가 성공했습니다.
- Cloudflare Access에는 `admin.everynook.co.kr` self-hosted application과 allow 정책이 있습니다.
- `nook-ops` Tunnel은 healthy 상태입니다.
- 다만 `admin.everynook.co.kr` DNS 레코드와 Tunnel의 Live Admin ingress 규칙은 아직 없습니다.
  외부 접근을 활성화하려면 API path 규칙과 `localhost:8082` catch-all 규칙을 추가해야 합니다.
