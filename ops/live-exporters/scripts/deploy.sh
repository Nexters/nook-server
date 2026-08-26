#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  cp .env.example .env
fi

if ! grep -q '^ERROR_LOG_SLACK_WEBHOOK_URL=https://hooks.slack.com/services/' .env; then
  echo "ERROR_LOG_SLACK_WEBHOOK_URL must be set in .env before deploying error log forwarding." >&2
  exit 1
fi

docker compose pull
docker compose up -d --force-recreate
docker compose ps
