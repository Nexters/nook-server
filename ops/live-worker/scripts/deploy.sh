#!/usr/bin/env bash
set -euo pipefail

image="${1:?immutable live worker image is required}"
root_dir="/opt/nook/live-worker"

[[ -f "${root_dir}/.env" ]] || { echo "Missing ${root_dir}/.env" >&2; exit 1; }
[[ "${image}" =~ ^everynook\.cr\.gabiacloud\.com/nook/nook-api:worker-prod- ]] || {
  echo "Invalid live worker image: ${image}" >&2
  exit 1
}

cd "${root_dir}"
IMAGE="${image}" docker compose pull worker
IMAGE="${image}" docker compose up -d --wait --wait-timeout 120 worker
curl -fsS http://192.168.0.216:18081/actuator/health
