#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  cp .env.example .env
fi

if [ ! -f mysql-exporter.my.cnf ]; then
  echo "mysql-exporter.my.cnf is required." >&2
  exit 1
fi

if ! grep -q '^ERROR_LOG_SLACK_WEBHOOK_URL=https://hooks.slack.com/services/' .env; then
  echo "ERROR_LOG_SLACK_WEBHOOK_URL must be set in .env before deploying error log forwarding." >&2
  exit 1
fi

if grep -q '^ERROR_LOG_DISCORD_WEBHOOK_URL=.' .env &&
  ! grep -Eq '^ERROR_LOG_DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+$' .env; then
  echo "ERROR_LOG_DISCORD_WEBHOOK_URL must be a Discord webhook URL when set." >&2
  exit 1
fi

if [ ! -f ../error-log-forwarder/forward_error_logs.py ]; then
  echo "Deploy ops/error-log-forwarder beside the exporters directory first." >&2
  exit 1
fi

docker compose pull
docker compose up -d
docker compose ps
