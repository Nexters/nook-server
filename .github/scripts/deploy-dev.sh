#!/usr/bin/env bash
set -euo pipefail

: "${DEV_IMAGE:?DEV_IMAGE is required}"
: "${GABIA_PUBLIC_REGISTRY:?GABIA_PUBLIC_REGISTRY is required}"
: "${GABIA_REGISTRY_USERNAME:?GABIA_REGISTRY_USERNAME is required}"
: "${GABIA_REGISTRY_PASSWORD:?GABIA_REGISTRY_PASSWORD is required}"

printf '%s' "${GABIA_REGISTRY_PASSWORD}" \
  | docker login "${GABIA_PUBLIC_REGISTRY}" \
      -u "${GABIA_REGISTRY_USERNAME}" \
      --password-stdin

cd /opt/nook/api

if grep -q '^IMAGE=' .env; then
  sed -i "s|^IMAGE=.*|IMAGE=${IMAGE}|" .env
else
  printf '\nIMAGE=%s\n' "${IMAGE}" >> .env
fi

docker compose pull
docker compose up -d
docker compose ps
