#!/usr/bin/env bash
set -euo pipefail

: "${DEV_SSH_HOST:?DEV_SSH_HOST is required}"
: "${DEV_SSH_KEY_PATH:?DEV_SSH_KEY_PATH is required}"
: "${DEV_IMAGE:?DEV_IMAGE is required}"
: "${GABIA_PRIVATE_REGISTRY:?GABIA_PRIVATE_REGISTRY is required}"
: "${GABIA_REGISTRY_USERNAME:?GABIA_REGISTRY_USERNAME is required}"
: "${GABIA_REGISTRY_PASSWORD:?GABIA_REGISTRY_PASSWORD is required}"

DEV_SSH_USER="${DEV_SSH_USER:-ubuntu}"
remote="${DEV_SSH_USER}@${DEV_SSH_HOST}"
ssh_options=(
  -i "${DEV_SSH_KEY_PATH}"
  -o BatchMode=yes
  -o StrictHostKeyChecking=accept-new
)

if [[ -n "${DEV_SSH_PORT:-}" ]]; then
  ssh_options+=(-p "${DEV_SSH_PORT}")
fi

printf '%s' "${GABIA_REGISTRY_PASSWORD}" \
  | ssh "${ssh_options[@]}" "${remote}" \
      "docker login ${GABIA_PRIVATE_REGISTRY} -u '${GABIA_REGISTRY_USERNAME}' --password-stdin"

ssh "${ssh_options[@]}" "${remote}" "IMAGE='${DEV_IMAGE}' bash -s" <<'REMOTE'
set -euo pipefail

cd /opt/nook/api

if grep -q '^IMAGE=' .env; then
  sed -i "s|^IMAGE=.*|IMAGE=${IMAGE}|" .env
else
  printf '\nIMAGE=%s\n' "${IMAGE}" >> .env
fi

docker compose pull
docker compose up -d
docker compose ps
REMOTE
