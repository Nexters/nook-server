#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example. Update GRAFANA_ADMIN_PASSWORD before exposing Grafana."
fi

docker compose pull
docker compose up -d
docker compose ps

