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

The dev container binds to `127.0.0.1:8081`. The future live container should
bind to `127.0.0.1:8082`. Both are intended to be exposed through Cloudflare
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
  - hostname: dev-admin.example.com
    service: http://localhost:8081
  - hostname: admin.example.com
    service: http://localhost:8082
  - service: http_status:404
```

Keep Cloudflare Access enabled for the admin hostname. Server-side admin API
authorization must still be enforced separately under `/api/admin/v1/**`.
