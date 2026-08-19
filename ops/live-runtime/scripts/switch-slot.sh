#!/usr/bin/env bash
set -euo pipefail

slot="${1:?slot is required}"
case "${slot}" in
  blue) port=8081 ;;
  green) port=8082 ;;
  *) echo "Unsupported slot: ${slot}" >&2; exit 1 ;;
esac

upstream_file="$(mktemp)"
trap 'unlink "${upstream_file}"' EXIT
cat >"${upstream_file}" <<EOF
upstream nook_live_api {
    server 127.0.0.1:${port};
    keepalive 32;
}
EOF

sudo install -m 0644 "${upstream_file}" /etc/nginx/conf.d/nook-live-upstream.conf
sudo nginx -t
sudo systemctl reload nginx
