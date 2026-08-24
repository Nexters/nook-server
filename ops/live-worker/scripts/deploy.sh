#!/usr/bin/env bash
set -euo pipefail

image="${1:?immutable live worker image is required}"
root_dir="/opt/nook/live-worker"

[[ -f "${root_dir}/.env" ]] || { echo "Missing ${root_dir}/.env" >&2; exit 1; }
[[ "${image}" =~ ^everynook\.cr\.gabiacloud\.com/nook/nook-worker:prod- ]] || {
  echo "Invalid live worker image: ${image}" >&2
  exit 1
}

secret_files=(
  "${root_dir}/secrets/aws-credentials"
  "${root_dir}/secrets/aws-config"
  "${root_dir}/secrets/firebase-service-account.json"
)
for secret_file in "${secret_files[@]}"; do
  [[ -f "${secret_file}" ]] || { echo "Missing ${secret_file}" >&2; exit 1; }
done
sudo chown 100:101 "${secret_files[@]}"
sudo chmod 600 "${secret_files[@]}"

cd "${root_dir}"
IMAGE="${image}" docker compose pull worker
IMAGE="${image}" docker compose up -d --wait --wait-timeout 120 worker
IMAGE="${image}" docker compose exec -T worker sh -lc \
  'test -r /run/secrets/aws_credentials && test -r /run/secrets/aws_config && test -r /run/secrets/firebase-service-account.json'
curl -fsS http://192.168.0.216:18081/actuator/health
