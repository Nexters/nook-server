#!/usr/bin/env bash
set -euo pipefail

swap_file="/swapfile"
swap_size="2G"

if [[ ! -f "${swap_file}" ]]; then
  sudo fallocate -l "${swap_size}" "${swap_file}"
  sudo chmod 600 "${swap_file}"
  sudo mkswap "${swap_file}"
fi

if ! swapon --show=NAME --noheadings | grep -qx "${swap_file}"; then
  sudo swapon "${swap_file}"
fi

if ! grep -qE '^/swapfile[[:space:]]' /etc/fstab; then
  printf '%s\n' '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

printf '%s\n' 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-nook-swap.conf >/dev/null
sudo sysctl --system >/dev/null

swapon --show
free -h
