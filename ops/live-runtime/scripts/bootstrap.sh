#!/usr/bin/env bash
set -euo pipefail

root_dir="/opt/nook/live"
runtime_dir="${root_dir}/runtime"

sudo apt-get update
sudo apt-get install -y ca-certificates curl nginx certbot python3-certbot-nginx default-mysql-client

sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

. /etc/os-release
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" |
  sudo tee /etc/apt/sources.list.d/docker.list >/dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu

sudo install -m 0644 "${runtime_dir}/nginx/upstream.conf" /etc/nginx/conf.d/nook-live-upstream.conf
sudo install -m 0644 "${runtime_dir}/nginx/api.everynook.co.kr.conf" /etc/nginx/sites-available/api.everynook.co.kr
sudo ln -sfn /etc/nginx/sites-available/api.everynook.co.kr /etc/nginx/sites-enabled/api.everynook.co.kr
sudo unlink /etc/nginx/sites-enabled/default 2>/dev/null || true
sudo nginx -t
sudo systemctl enable --now nginx docker

"${runtime_dir}/scripts/configure-swap.sh"

sudo chown -R ubuntu:ubuntu "${root_dir}"
chmod 700 "${root_dir}"
chmod 600 "${root_dir}/.env"
sudo chown root:root "${root_dir}/secrets"
sudo chmod 700 "${root_dir}/secrets"
if compgen -G "${root_dir}/secrets/*" >/dev/null; then
  sudo chown 100:101 "${root_dir}/secrets/"*
  sudo chmod 400 "${root_dir}/secrets/"*
fi

docker --version
docker compose version
nginx -v
swapon --show
