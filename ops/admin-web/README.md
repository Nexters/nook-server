# Nook Admin Web Ops

This deploys the static admin frontend to the ops VM as an nginx container.

## Layout

```text
/opt/nook/admin-web/dev
  nook-admin-web/
  ops/admin-web/

/opt/nook/admin-web/live
  ops/admin-web/
```

The dev container binds to `127.0.0.1:8081`. The live container binds to
`127.0.0.1:8082`. Both are intended to be exposed through Cloudflare
Tunnel without opening a public inbound port.

## Deploy

```shell
ops/admin-web/scripts/deploy.sh
```

Override the target environment when needed:

```shell
ADMIN_ENV=live ADMIN_WEB_PORT=8082 ops/admin-web/scripts/deploy.sh
```

## Cloudflare Tunnel

Point the admin hostname to the local service on the ops VM:

```yaml
ingress:
  # `/api/admin/v1/**` must reach nook-api before the static-web catch-all.
  - hostname: dev-admin.everynook.co.kr
    path: ^/api/admin/v1(?:/.*)?$
    service: http://192.168.0.102:8080
  - hostname: dev-admin.everynook.co.kr
    service: http://localhost:8081
  - hostname: admin.everynook.co.kr
    path: ^/api/admin/v1(?:/.*)?$
    service: https://api.everynook.co.kr
  - hostname: admin.everynook.co.kr
    service: http://localhost:8082
  - service: http_status:404
```

Keep Cloudflare Access enabled for the admin hostname. Server-side admin API
authorization is enforced separately under `/api/admin/v1/**`. Configure the
admin hostname and its `/api/admin/v1/*` route in the same Access application so
the origin receives the same `Cf-Access-Jwt-Assertion` token.

Configure the API process with the Access application values:

```shell
ADMIN_ACCESS_ENABLED=true
ADMIN_ACCESS_TEAM_DOMAIN=https://your-team.cloudflareaccess.com
ADMIN_ACCESS_AUDIENCE=your-access-application-aud-tag
```

The admin web calls `/api/admin/v1/**` on its own origin. The path-specific
Tunnel rule therefore has to appear before the static admin-web rule.
