#!/usr/bin/env bash
set -euo pipefail

: "${ADMIN_WEB_IMAGE:?ADMIN_WEB_IMAGE is required}"
: "${ADMIN_ENV:?ADMIN_ENV is required}"
: "${ADMIN_WEB_PORT:?ADMIN_WEB_PORT is required}"
: "${GABIA_PUBLIC_REGISTRY:?GABIA_PUBLIC_REGISTRY is required}"
: "${GABIA_REGISTRY_USERNAME:?GABIA_REGISTRY_USERNAME is required}"
: "${GABIA_REGISTRY_PASSWORD:?GABIA_REGISTRY_PASSWORD is required}"

ADMIN_REMOTE_DIR="${ADMIN_REMOTE_DIR:-/opt/nook/admin-web/${ADMIN_ENV}}"

if [[ "${ADMIN_DEPLOY_LOCAL:-false}" == "true" ]]; then
  mkdir -p "${ADMIN_REMOTE_DIR}/ops/admin-web"
  rsync -av --delete ops/admin-web/ "${ADMIN_REMOTE_DIR}/ops/admin-web/"

  cat > "${ADMIN_REMOTE_DIR}/ops/admin-web/.env" <<EOF
ADMIN_ENV=${ADMIN_ENV}
ADMIN_WEB_PORT=${ADMIN_WEB_PORT}
ADMIN_WEB_IMAGE=${ADMIN_WEB_IMAGE}
EOF

  printf '%s' "${GABIA_REGISTRY_PASSWORD}" \
    | docker login "${GABIA_PUBLIC_REGISTRY}" \
        -u "${GABIA_REGISTRY_USERNAME}" \
        --password-stdin

  cd "${ADMIN_REMOTE_DIR}/ops/admin-web"
  if [[ "${ADMIN_ENV}" == "dev" ]] && docker ps -a --format '{{.Names}}' | grep -qx 'nook-admin-web'; then
    docker rm -f nook-admin-web
  fi
  docker compose pull
  docker compose up -d --no-build
  docker compose ps
  curl -fsS "http://127.0.0.1:${ADMIN_WEB_PORT}/health"
  exit 0
fi

: "${ADMIN_OPS_SSH_TARGET:?ADMIN_OPS_SSH_TARGET is required when ADMIN_DEPLOY_LOCAL is not true}"

ssh "${ADMIN_OPS_SSH_TARGET}" "mkdir -p '${ADMIN_REMOTE_DIR}/ops/admin-web'"
rsync -av --delete ops/admin-web/ "${ADMIN_OPS_SSH_TARGET}:${ADMIN_REMOTE_DIR}/ops/admin-web/"

ssh "${ADMIN_OPS_SSH_TARGET}" "cat > '${ADMIN_REMOTE_DIR}/ops/admin-web/.env'" <<EOF
ADMIN_ENV=${ADMIN_ENV}
ADMIN_WEB_PORT=${ADMIN_WEB_PORT}
ADMIN_WEB_IMAGE=${ADMIN_WEB_IMAGE}
EOF

printf '%s' "${GABIA_REGISTRY_PASSWORD}" |
  ssh "${ADMIN_OPS_SSH_TARGET}" \
    "docker login '${GABIA_PUBLIC_REGISTRY}' -u '${GABIA_REGISTRY_USERNAME}' --password-stdin"

ssh "${ADMIN_OPS_SSH_TARGET}" \
  "cd '${ADMIN_REMOTE_DIR}/ops/admin-web' && \
    if [ '${ADMIN_ENV}' = 'dev' ] && docker ps -a --format '{{.Names}}' | grep -qx 'nook-admin-web'; then docker rm -f nook-admin-web; fi && \
    docker compose pull && \
    docker compose up -d --no-build && \
    docker compose ps && \
    curl -fsS 'http://127.0.0.1:${ADMIN_WEB_PORT}/health'"
