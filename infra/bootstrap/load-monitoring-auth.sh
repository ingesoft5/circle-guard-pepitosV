#!/usr/bin/env bash
# Creates the BasicAuth secret and Traefik middleware for the Prometheus and Alertmanager
# Ingresses (HU-26). The password is read from stdin, so it never reaches argv or the repo:
#   ssh ubuntu@<node> 'sudo ~/circleguard/infra/bootstrap/load-monitoring-auth.sh monitoring' \
#     < ~/.config/circleguard/monitoring-auth-password
# It is idempotent: the Secret is re-applied and the Middleware is applied again.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly NAMESPACE="monitoring"
readonly SECRET_NAME="monitoring-basic-auth"
readonly MIDDLEWARE_FILE="${SCRIPT_DIR}/../../observability/ingress/basic-auth-middleware.yaml"

main() {
  local user="${1:-monitoring}" password password_hash tmp
  local -a kubectl
  [[ "${user}" =~ ^[A-Za-z0-9._-]+$ ]] || die "Usage: load-monitoring-auth.sh [user] < password"

  password="$(cat)"
  [[ -n "${password}" ]] || die "Expected the basic auth password on stdin"

  if command -v kubectl >/dev/null 2>&1; then
    kubectl=(kubectl)
  elif command -v k3s >/dev/null 2>&1; then
    kubectl=(k3s kubectl)
  else
    die "kubectl or k3s is required"
  fi

  if command -v htpasswd >/dev/null 2>&1; then
    password_hash="$(printf '%s\n' "${password}" | htpasswd -niB "${user}")"
  elif command -v openssl >/dev/null 2>&1; then
    password_hash="${user}:$(printf '%s' "${password}" | openssl passwd -apr1 -stdin)"
  else
    die "htpasswd or openssl is required to hash the password"
  fi

  tmp="$(mktemp)"
  trap 'rm -f "${tmp}"' EXIT
  (umask 077; printf '%s\n' "${password_hash}" >"${tmp}")

  "${kubectl[@]}" get namespace "${NAMESPACE}" >/dev/null 2>&1 \
    || "${kubectl[@]}" create namespace "${NAMESPACE}"

  "${kubectl[@]}" -n "${NAMESPACE}" create secret generic "${SECRET_NAME}" \
    --from-file=users="${tmp}" --dry-run=client -o yaml \
    | "${kubectl[@]}" apply -f -

  "${kubectl[@]}" apply -f "${MIDDLEWARE_FILE}"
  info "BasicAuth ready for /prometheus and /alertmanager (user ${user})"
}

main "$@"
