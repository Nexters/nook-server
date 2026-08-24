#!/usr/bin/env bash
set -euo pipefail

: "${LIVE_WORKER_IMAGE:?LIVE_WORKER_IMAGE is required}"
: "${LIVE_SSH_TARGET:?LIVE_SSH_TARGET is required}"
: "${LIVE_SSH_KEY:?LIVE_SSH_KEY is required}"

ssh_options=(-i "${LIVE_SSH_KEY}" -o BatchMode=yes -o StrictHostKeyChecking=yes)
ssh "${ssh_options[@]}" "${LIVE_SSH_TARGET}" 'mkdir -p /opt/nook/live-worker/scripts'
rsync -av --delete -e "ssh -i ${LIVE_SSH_KEY} -o BatchMode=yes -o StrictHostKeyChecking=yes" \
  --exclude .env --exclude secrets \
  ops/live-worker/ "${LIVE_SSH_TARGET}:/opt/nook/live-worker/"
ssh "${ssh_options[@]}" "${LIVE_SSH_TARGET}" \
  "chmod 700 /opt/nook/live-worker/scripts/deploy.sh && /opt/nook/live-worker/scripts/deploy.sh '${LIVE_WORKER_IMAGE}'"
