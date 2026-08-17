#!/usr/bin/env bash
set -euo pipefail

sudo swapoff /swapfile
sudo sed -i '\|^/swapfile[[:space:]]|d' /etc/fstab
sudo rm -f /etc/sysctl.d/99-nook-swap.conf
sudo rm -f /swapfile
sudo sysctl --system >/dev/null

swapon --show
free -h
