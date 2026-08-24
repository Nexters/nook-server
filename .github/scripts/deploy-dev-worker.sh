#!/usr/bin/env bash
set -euo pipefail

: "${DEV_WORKER_IMAGE:?DEV_WORKER_IMAGE is required}"
: "${GABIA_PUBLIC_REGISTRY:?GABIA_PUBLIC_REGISTRY is required}"
: "${GABIA_REGISTRY_USERNAME:?GABIA_REGISTRY_USERNAME is required}"
: "${GABIA_REGISTRY_PASSWORD:?GABIA_REGISTRY_PASSWORD is required}"

root_dir="/opt/nook/dev-worker"
[[ -f "${root_dir}/.env" ]] || { echo "Missing ${root_dir}/.env" >&2; exit 1; }
mkdir -p "${root_dir}/scripts"
cp ops/dev-worker/compose.yml "${root_dir}/compose.yml"
cp ops/dev-worker/scripts/deploy.sh "${root_dir}/scripts/deploy.sh"
chmod 700 "${root_dir}/scripts/deploy.sh"

printf '%s' "${GABIA_REGISTRY_PASSWORD}" |
  docker login "${GABIA_PUBLIC_REGISTRY}" -u "${GABIA_REGISTRY_USERNAME}" --password-stdin
"${root_dir}/scripts/deploy.sh" "${DEV_WORKER_IMAGE}"
