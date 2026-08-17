#!/usr/bin/env bash
set -euo pipefail

: "${DEV_IMAGE:?DEV_IMAGE is required}"
: "${GABIA_PUBLIC_REGISTRY:?GABIA_PUBLIC_REGISTRY is required}"
: "${GABIA_REGISTRY_USERNAME:?GABIA_REGISTRY_USERNAME is required}"
: "${GABIA_REGISTRY_PASSWORD:?GABIA_REGISTRY_PASSWORD is required}"
: "${DEV_SSH_TARGET:?DEV_SSH_TARGET is required}"

printf '%s' "${GABIA_REGISTRY_PASSWORD}" \
  | ssh "${DEV_SSH_TARGET}" \
      "docker login '${GABIA_PUBLIC_REGISTRY}' -u '${GABIA_REGISTRY_USERNAME}' --password-stdin"

ssh "${DEV_SSH_TARGET}" bash -s -- "${DEV_IMAGE}" <<'REMOTE_SCRIPT'
set -euo pipefail

dev_image="$1"
cd /opt/nook/api

if grep -q '^IMAGE=' .env; then
  sed -i "s|^IMAGE=.*|IMAGE=${dev_image}|" .env
else
  printf '\nIMAGE=%s\n' "${dev_image}" >> .env
fi

docker compose pull
docker compose up -d
docker compose ps
curl -fsS http://127.0.0.1:8080/actuator/health
REMOTE_SCRIPT
