#!/usr/bin/env bash
set -euo pipefail

image="${1:?immutable prod image is required}"
if [[ ! "${image}" =~ ^everynook\.cr\.gabiacloud\.com/nook/nook-api:prod-([0-9]+-[0-9a-f]{8}|[0-9a-f]{12,40})$ ]]; then
  echo "Only immutable prod-<sequence>-<SHA8> or legacy prod-<SHA> images can be deployed: ${image}" >&2
  exit 1
fi

root_dir="/opt/nook/live"
runtime_dir="${root_dir}/runtime"
state_file="${root_dir}/.deployment.env"
compose_file="${runtime_dir}/compose.yml"

[[ -f "${root_dir}/.env" ]] || { echo "Missing ${root_dir}/.env" >&2; exit 1; }

ACTIVE_SLOT=""
BLUE_IMAGE="${image}"
GREEN_IMAGE="${image}"
PREVIOUS_IMAGE=""
if [[ -f "${state_file}" ]]; then
  # shellcheck disable=SC1090
  source "${state_file}"
fi

case "${ACTIVE_SLOT}" in
  blue) candidate_slot="green"; previous_image="${BLUE_IMAGE}" ;;
  green) candidate_slot="blue"; previous_image="${GREEN_IMAGE}" ;;
  "") candidate_slot="blue"; previous_image="" ;;
  *) echo "Invalid ACTIVE_SLOT in ${state_file}: ${ACTIVE_SLOT}" >&2; exit 1 ;;
esac

if [[ "${candidate_slot}" == "blue" ]]; then
  BLUE_IMAGE="${image}"
  candidate_port=8081
else
  GREEN_IMAGE="${image}"
  candidate_port=8082
fi

write_state() {
  local temp_file
  temp_file="$(mktemp "${root_dir}/.deployment.env.XXXXXX")"
  {
    printf 'ACTIVE_SLOT=%s\n' "${ACTIVE_SLOT}"
    printf 'BLUE_IMAGE=%s\n' "${BLUE_IMAGE}"
    printf 'GREEN_IMAGE=%s\n' "${GREEN_IMAGE}"
    printf 'PREVIOUS_IMAGE=%s\n' "${PREVIOUS_IMAGE}"
  } >"${temp_file}"
  chmod 600 "${temp_file}"
  mv "${temp_file}" "${state_file}"
}

write_state
docker compose -f "${compose_file}" --env-file "${state_file}" pull "${candidate_slot}"
docker compose -f "${compose_file}" --env-file "${state_file}" up -d --no-deps "${candidate_slot}"

for attempt in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:${candidate_port}/actuator/health" >/dev/null; then
    break
  fi
  if [[ "${attempt}" -eq 30 ]]; then
    docker compose -f "${compose_file}" --env-file "${state_file}" logs --tail=200 "${candidate_slot}" >&2
    echo "Candidate slot failed health check: ${candidate_slot}" >&2
    exit 1
  fi
  sleep 4
done

"${runtime_dir}/scripts/switch-slot.sh" "${candidate_slot}"
for attempt in $(seq 1 10); do
  if curl -fsS -H 'Host: api.everynook.co.kr' http://127.0.0.1/actuator/health >/dev/null; then
    break
  fi
  if [[ "${attempt}" -eq 10 ]]; then
    if [[ -n "${ACTIVE_SLOT}" ]]; then
      "${runtime_dir}/scripts/switch-slot.sh" "${ACTIVE_SLOT}"
    fi
    echo "Nginx health check failed after switching to ${candidate_slot}" >&2
    exit 1
  fi
  sleep 1
done

ACTIVE_SLOT="${candidate_slot}"
PREVIOUS_IMAGE="${previous_image}"
write_state

printf 'ACTIVE_SLOT=%s\nCURRENT_IMAGE=%s\nPREVIOUS_IMAGE=%s\n' \
  "${ACTIVE_SLOT}" "${image}" "${PREVIOUS_IMAGE}"
