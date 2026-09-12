#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example. Update GRAFANA_ADMIN_PASSWORD before exposing Grafana."
fi

if ! grep -q '^SLACK_ALERT_WEBHOOK_URL=https://hooks.slack.com/services/' .env; then
  echo "SLACK_ALERT_WEBHOOK_URL must be set in .env before deploying Slack alerting." >&2
  exit 1
fi

for key in DISCORD_DEV_ALERT_WEBHOOK_URL DISCORD_LIVE_ALERT_WEBHOOK_URL; do
  if ! grep -Eq "^${key}=https://discord.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+$" .env; then
    echo "${key} must be set in .env before deploying Discord alerting." >&2
    exit 1
  fi
done

docker compose pull
docker compose up -d
docker compose ps
