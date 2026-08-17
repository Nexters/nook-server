#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
REMOTE_HOST="${REMOTE_HOST:-nook-ops}"
ADMIN_ENV="${ADMIN_ENV:-dev}"
ADMIN_WEB_PORT="${ADMIN_WEB_PORT:-8081}"
REMOTE_DIR="${REMOTE_DIR:-/opt/nook/admin-web/${ADMIN_ENV}}"

ssh -o ClearAllForwardings=yes "$REMOTE_HOST" \
  "mkdir -p '$REMOTE_DIR/nook-admin-web' '$REMOTE_DIR/ops/admin-web'"

rsync -av --delete \
  --exclude "node_modules" \
  --exclude "dist" \
  "$REPO_ROOT/nook-admin-web/" "$REMOTE_HOST:$REMOTE_DIR/nook-admin-web/"

rsync -av --delete \
  "$REPO_ROOT/ops/admin-web/" "$REMOTE_HOST:$REMOTE_DIR/ops/admin-web/"

ssh -o ClearAllForwardings=yes "$REMOTE_HOST" \
  "cd '$REMOTE_DIR/ops/admin-web' && \
    printf 'ADMIN_ENV=%s\nADMIN_WEB_PORT=%s\n' '$ADMIN_ENV' '$ADMIN_WEB_PORT' > .env && \
    if [ '$ADMIN_ENV' = 'dev' ] && docker ps -a --format '{{.Names}}' | grep -qx 'nook-admin-web'; then docker rm -f nook-admin-web; fi && \
    docker compose up -d --build && docker compose ps"
