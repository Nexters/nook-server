# NOOK-238 Grafana SSO allowlist 사용자 Editor 권한 동기화

## 목적

Cloudflare Access allowlist를 통과한 Grafana SSO 사용자가 운영 대시보드를 수정할 수 있도록
Grafana 조직 역할을 `Editor`로 동기화합니다.

## 범위

- Generic OAuth role mapping을 모든 허용 사용자에 대해 `Editor`로 설정합니다.
- 신규 SSO 사용자의 기본 조직 역할을 `Editor`로 설정합니다.
- 기존 SSO 사용자도 다음 로그인 시 `Editor`로 동기화합니다.
- ops VM에 변경을 배포하고 실제 런타임 설정을 검증합니다.

## 제외 범위

- Grafana 조직 `Admin` 또는 server admin 권한 부여
- Cloudflare Access allowlist 변경
- Grafana 관리자 비밀번호 재설정
- Grafana DB 직접 수정

## 성공 기준

- allowlist 사용자가 재로그인 후 대시보드를 수정할 수 있습니다.
- OAuth 조직 역할 동기화가 활성화됩니다.
- Grafana health와 `127.0.0.1:3000` 바인딩이 유지됩니다.

## 검증

```shell
docker compose --env-file ops/monitoring/.env.example \
  -f ops/monitoring/docker-compose.yml config
./gradlew check
```

배포 후 Grafana 컨테이너에서 다음 설정을 확인합니다.

```text
GF_USERS_AUTO_ASSIGN_ORG_ROLE=Editor
GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_PATH='Editor'
GF_AUTH_GENERIC_OAUTH_SKIP_ORG_ROLE_SYNC=false
```

## 롤백

배포 전 Compose 백업을 복원하고 Grafana 컨테이너를 재생성합니다. 기존 SSO 사용자의 역할은
다음 로그인 시 복원된 role mapping에 따라 다시 동기화됩니다.
