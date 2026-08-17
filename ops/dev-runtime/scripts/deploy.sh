#!/usr/bin/env bash
set -euo pipefail

remote_host="${1:-nook-dev}"

rsync -av ops/dev-runtime/api/compose.yml "${remote_host}:/opt/nook/api/compose.yml"
rsync -av ops/dev-runtime/mysql/compose.yml "${remote_host}:/opt/nook/mysql/compose.yml"
rsync -av ops/dev-runtime/scripts/configure-swap.sh "${remote_host}:/tmp/nook-configure-swap.sh"

ssh "${remote_host}" <<'REMOTE_SCRIPT'
set -euo pipefail

chmod 600 /opt/nook/api/compose.yml /opt/nook/mysql/compose.yml
chmod 700 /tmp/nook-configure-swap.sh
/tmp/nook-configure-swap.sh
rm -f /tmp/nook-configure-swap.sh

docker compose -f /opt/nook/mysql/compose.yml --env-file /opt/nook/mysql/.env config >/dev/null
docker compose -f /opt/nook/api/compose.yml --env-file /opt/nook/api/.env config >/dev/null

cd /opt/nook/mysql
docker compose up -d --wait --wait-timeout 120

cd /opt/nook/api
docker compose up -d --wait --wait-timeout 120
REMOTE_SCRIPT
