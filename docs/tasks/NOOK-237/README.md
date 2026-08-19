# NOOK-237 Cloudflare Access OIDC 기반 Grafana 단일 로그인 구성

## 목적

Cloudflare Access에서 허용된 Gmail 계정이 Grafana 로컬 로그인 화면을 다시 거치지 않고
사용자별 SSO 계정으로 진입하도록 구성합니다.

## 범위

- Cloudflare Access에 Grafana OIDC SaaS 애플리케이션을 구성합니다.
- 기존 `Nook Ops Allow List`와 같은 정책으로 OIDC 로그인을 제한합니다.
- Grafana Generic OAuth와 자동 로그인을 활성화합니다.
- 신규 SSO 사용자의 기본 조직 역할은 `Viewer`로 제한합니다.
- OAuth client secret은 ops VM의 `/opt/nook/monitoring/.env`에만 저장합니다.
- 기존 Grafana 로컬 관리자 계정은 장애 복구용으로 유지합니다.

## 제외 범위

- Grafana anonymous access 활성화
- Prometheus 또는 Loki 외부 공개
- 기존 Cloudflare Access allowlist 사용자 변경
- Grafana 대시보드, datasource 또는 alert 규칙 변경

## 성공 기준

- 허용된 Gmail 사용자는 Grafana 비밀번호 입력 없이 사용자별 계정으로 진입합니다.
- 허용되지 않은 사용자는 Cloudflare Access에서 차단됩니다.
- 신규 SSO 사용자는 기본 `Viewer` 권한을 받습니다.
- 기존 로컬 관리자 복구 경로가 유지됩니다.
- Grafana health와 기존 datasource 및 dashboard provisioning이 정상입니다.
- Grafana origin의 `127.0.0.1:3000` 바인딩이 유지됩니다.

## 구현

Grafana는 Cloudflare Access를 Generic OAuth provider로 사용합니다. 브라우저가 Grafana의
`/login/generic_oauth`로 이동하면 Cloudflare Access가 기존 인증 세션과 allowlist 정책을
확인하고 OIDC authorization code를 발급합니다. Grafana는 `email` claim으로 사용자 계정을
생성하며 로그인 화면을 표시하지 않습니다.

Cloudflare에서 발급한 client ID와 client secret은 다음 환경변수로 주입합니다.

```text
GRAFANA_OIDC_CLIENT_ID
GRAFANA_OIDC_CLIENT_SECRET
GRAFANA_OIDC_ISSUER_URL
GRAFANA_ROOT_URL
```

## 검증

```shell
docker compose --env-file ops/monitoring/.env.example \
  -f ops/monitoring/docker-compose.yml config
./gradlew check
```

배포 후 다음을 확인합니다.

- Cloudflare OIDC application의 callback URL, scope, policy
- `https://<grafana-domain>/login`의 OAuth 자동 redirect
- 허용 계정의 Grafana 사용자별 로그인 및 `Viewer` 역할
- 비허용 계정의 Cloudflare Access 차단
- `http://127.0.0.1:3000/api/health`
- Grafana datasource 및 dashboard API
- Docker의 `127.0.0.1:3000` port binding

자동 로그인 장애 시 `https://<grafana-domain>/login?disableAutoLogin=true`로 로컬
로그인 화면에 접근할 수 있습니다.

## 롤백

1. 배포 전 백업한 Compose와 `.env`를 복원합니다.
2. Grafana 컨테이너를 재생성해 Generic OAuth 설정을 제거합니다.
3. Cloudflare의 OIDC SaaS 애플리케이션을 제거합니다.
4. 기존 Grafana 로컬 관리자 계정으로 로그인합니다.
