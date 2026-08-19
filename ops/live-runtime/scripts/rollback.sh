#!/usr/bin/env bash
set -euo pipefail

root_dir="/opt/nook/live"
state_file="${root_dir}/.deployment.env"
[[ -f "${state_file}" ]] || { echo "No deployment state found" >&2; exit 1; }

# shellcheck disable=SC1090
source "${state_file}"
[[ -n "${PREVIOUS_IMAGE:-}" ]] || { echo "No previous image is recorded" >&2; exit 1; }

exec "${root_dir}/runtime/scripts/deploy.sh" "${PREVIOUS_IMAGE}"
