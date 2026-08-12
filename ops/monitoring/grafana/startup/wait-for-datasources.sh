#!/bin/sh

set -eu

max_attempts=30
attempt=1

while [ "$attempt" -le "$max_attempts" ]; do
  if wget -q -T 2 -O /dev/null http://prometheus:9090/-/ready \
    && wget -q -T 2 -O /dev/null http://loki:3100/ready; then
    exec /run.sh
  fi

  echo "Waiting for Prometheus and Loki readiness (${attempt}/${max_attempts})" >&2
  attempt=$((attempt + 1))
  sleep 2
done

echo "Datasource readiness wait timed out; starting Grafana for diagnostics" >&2
exec /run.sh
