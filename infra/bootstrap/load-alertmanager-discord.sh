#!/usr/bin/env bash
# Creates the Secret with the Discord webhook that Alertmanager uses for the CircleGuard alerts
# (HU-41). The URL is read from stdin, so it never reaches argv or the repo:
#   ssh ubuntu@<node> 'sudo ~/circleguard/infra/bootstrap/load-alertmanager-discord.sh' \
#     < ~/.config/circleguard/alertmanager-discord-webhook
# It is idempotent: running it again replaces the URL. The AlertmanagerConfig in
# observability/alerts/alertmanager-config.yaml references the Secret, and the operator puts the
# URL in the Alertmanager configuration it generates.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly NAMESPACE="monitoring"
readonly SECRET_NAME="alertmanager-discord"

# Global, not local to main: the EXIT trap runs after main returns.
tmp=""
cleanup() {
  [[ -n "${tmp}" ]] && rm -f "${tmp}"
  return 0
}
trap cleanup EXIT

main() {
  local url
  local -a kubectl

  url="$(tr -d '[:space:]')"
  [[ "${url}" =~ ^https://(discord\.com|discordapp\.com)/api/webhooks/[0-9]+/[A-Za-z0-9_-]+$ ]] \
    || die "Expected a Discord webhook URL (https://discord.com/api/webhooks/<id>/<token>) on stdin"

  if command -v kubectl >/dev/null 2>&1; then
    kubectl=(kubectl)
  elif command -v k3s >/dev/null 2>&1; then
    kubectl=(k3s kubectl)
  else
    die "kubectl or k3s is required"
  fi

  tmp="$(mktemp)"
  (umask 077; printf '%s' "${url}" >"${tmp}")

  "${kubectl[@]}" get namespace "${NAMESPACE}" >/dev/null 2>&1 \
    || "${kubectl[@]}" create namespace "${NAMESPACE}"

  "${kubectl[@]}" -n "${NAMESPACE}" create secret generic "${SECRET_NAME}" \
    --from-file=webhook-url="${tmp}" --dry-run=client -o yaml \
    | "${kubectl[@]}" apply -f -

  info "Discord webhook ready for Alertmanager (Secret ${NAMESPACE}/${SECRET_NAME})"
}

main "$@"
