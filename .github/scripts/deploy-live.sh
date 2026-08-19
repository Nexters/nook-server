#!/usr/bin/env bash
set -euo pipefail

: "${LIVE_SSH_TARGET:?LIVE_SSH_TARGET is required}"
: "${LIVE_SSH_KEY:?LIVE_SSH_KEY is required}"
: "${LIVE_ACTION:?LIVE_ACTION is required}"

ssh_options=(-i "${LIVE_SSH_KEY}" -o BatchMode=yes -o StrictHostKeyChecking=yes)

ssh "${ssh_options[@]}" "${LIVE_SSH_TARGET}" 'mkdir -p /opt/nook/live/runtime'
rsync -av --delete -e "ssh -i ${LIVE_SSH_KEY} -o BatchMode=yes -o StrictHostKeyChecking=yes" \
  ops/live-runtime/ "${LIVE_SSH_TARGET}:/opt/nook/live/runtime/"
ssh "${ssh_options[@]}" "${LIVE_SSH_TARGET}" 'chmod 700 /opt/nook/live/runtime/scripts/*.sh'

case "${LIVE_ACTION}" in
  deploy)
    : "${LIVE_IMAGE:?LIVE_IMAGE is required for deploy}"
    ssh "${ssh_options[@]}" "${LIVE_SSH_TARGET}" \
      "/opt/nook/live/runtime/scripts/deploy.sh '${LIVE_IMAGE}'"
    ;;
  rollback)
    ssh "${ssh_options[@]}" "${LIVE_SSH_TARGET}" \
      '/opt/nook/live/runtime/scripts/rollback.sh'
    ;;
  *)
    echo "Unsupported LIVE_ACTION: ${LIVE_ACTION}" >&2
    exit 1
    ;;
esac
