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

docker compose pull
docker compose up -d
docker compose ps
