#!/usr/bin/env bash
# Ensures a swap file on the CI host so a memory peak during parallel builds slows the
# host down instead of getting a JVM (Nexus, SonarQube, Gradle) OOM-killed. Idempotent:
# an existing swap file of the requested size is left alone.
#   infra/bootstrap/ensure-swap.sh            # 4 GB at /swapfile
#   SWAP_SIZE_GB=8 infra/bootstrap/ensure-swap.sh
# Only for the CI host: Kubernetes nodes (k3s) expect swap to be off.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly SWAP_FILE="${SWAP_FILE:-/swapfile}"
readonly SWAP_SIZE_GB="${SWAP_SIZE_GB:-4}"
readonly SWAPPINESS="${SWAPPINESS:-10}"

current_size_gb() {
  local bytes
  bytes="$(run_as_root stat -c %s "${SWAP_FILE}" 2>/dev/null || echo 0)"
  echo $((bytes / 1024 / 1024 / 1024))
}

ensure_file() {
  if [[ "$(current_size_gb)" -eq "${SWAP_SIZE_GB}" ]]; then
    info "Swap file ${SWAP_FILE} already has ${SWAP_SIZE_GB} GB"
    return 0
  fi
  if run_as_root swapon --show=NAME --noheadings | grep -qx "${SWAP_FILE}"; then
    info "Resizing ${SWAP_FILE}: turning it off first"
    run_as_root swapoff "${SWAP_FILE}"
  fi
  info "Creating ${SWAP_FILE} (${SWAP_SIZE_GB} GB)"
  run_as_root rm -f "${SWAP_FILE}"
  run_as_root fallocate -l "${SWAP_SIZE_GB}G" "${SWAP_FILE}"
  run_as_root chmod 600 "${SWAP_FILE}"
  run_as_root mkswap "${SWAP_FILE}" >/dev/null
}

ensure_active() {
  if run_as_root swapon --show=NAME --noheadings | grep -qx "${SWAP_FILE}"; then
    info "Swap ${SWAP_FILE} already active"
  else
    run_as_root swapon "${SWAP_FILE}"
    info "Swap ${SWAP_FILE} activated"
  fi
  if ! grep -qE "^${SWAP_FILE}[[:space:]]" /etc/fstab; then
    echo "${SWAP_FILE} none swap sw 0 0" | run_as_root tee -a /etc/fstab >/dev/null
    info "Swap added to /etc/fstab"
  fi
}

ensure_swappiness() {
  local conf=/etc/sysctl.d/90-circleguard-swap.conf
  if [[ "$(cat "${conf}" 2>/dev/null)" != "vm.swappiness=${SWAPPINESS}" ]]; then
    echo "vm.swappiness=${SWAPPINESS}" | run_as_root tee "${conf}" >/dev/null
  fi
  run_as_root sysctl -q -w "vm.swappiness=${SWAPPINESS}"
}

main() {
  [[ "${SWAP_SIZE_GB}" =~ ^[1-9][0-9]*$ ]] || die "SWAP_SIZE_GB must be a positive integer"
  require_cmd fallocate mkswap swapon
  ensure_file
  ensure_active
  ensure_swappiness
  info "Swap ready: $(run_as_root swapon --show --noheadings | tr -s ' ')"
}

main "$@"
