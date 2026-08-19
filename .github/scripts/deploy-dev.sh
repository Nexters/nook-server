#!/usr/bin/env bash
set -euo pipefail

: "${DEV_IMAGE:?DEV_IMAGE is required}"
: "${GABIA_PUBLIC_REGISTRY:?GABIA_PUBLIC_REGISTRY is required}"
: "${GABIA_REGISTRY_USERNAME:?GABIA_REGISTRY_USERNAME is required}"
: "${GABIA_REGISTRY_PASSWORD:?GABIA_REGISTRY_PASSWORD is required}"
: "${DEV_SSH_TARGET:?DEV_SSH_TARGET is required}"

if [[ ! "${DEV_IMAGE}" =~ ^everynook\.cr\.gabiacloud\.com/nook/nook-api:dev-([0-9]+-[0-9a-f]{8}|[0-9a-f]{12,40})$ ]]; then
  echo "Only immutable dev-<sequence>-<SHA8> or legacy dev-<SHA> images can be deployed: ${DEV_IMAGE}" >&2
  exit 1
fi

printf '%s' "${GABIA_REGISTRY_PASSWORD}" \
  | ssh "${DEV_SSH_TARGET}" \
      "docker login '${GABIA_PUBLIC_REGISTRY}' -u '${GABIA_REGISTRY_USERNAME}' --password-stdin"

ssh "${DEV_SSH_TARGET}" bash -s -- "${DEV_IMAGE}" <<'REMOTE_SCRIPT'
set -euo pipefail

dev_image="$1"
cd /opt/nook/api

docker pull "${dev_image}"

if grep -q '^IMAGE=' .env; then
  sed -i "s|^IMAGE=.*|IMAGE=${dev_image}|" .env
else
  printf '\nIMAGE=%s\n' "${dev_image}" >> .env
fi

docker compose up -d --wait --wait-timeout 120
docker compose ps
curl -fsS http://127.0.0.1:8080/actuator/health
REMOTE_SCRIPT
