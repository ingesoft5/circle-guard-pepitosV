#!/usr/bin/env bash
# Stores the kubeconfig of one k3s cluster for Jenkins (credential kubeconfig-<env>), idempotently.
# Runs on the CI host and reads the kubeconfig from stdin, so it never reaches argv or the
# workstation disk. The API address is rewritten to the node's private IP (reached through the
# VPC peering; k3s includes the node IP in its certificate):
#   ssh ubuntu@<k3s-node> 'sudo cat /etc/rancher/k3s/k3s.yaml' \
#     | ssh ubuntu@<ci-host> 'sudo ~/circleguard/infra/bootstrap/load-kubeconfig.sh dev <node-private-ip>'
# The file is kept in infra/compose/ci/secrets, which casc.yaml reads as kubeconfig-<env>.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly CI_ENV_FILE="${COMPOSE_DIR}/ci/.env"
readonly SECRETS_DIR="${COMPOSE_DIR}/ci/secrets"
readonly JENKINS_UID=1000

reload_casc() {
  local jenkins="http://localhost:${JENKINS_PORT}" crumb cookies
  cookies="$(mktemp)"
  trap 'rm -f "${cookies}"' EXIT
  crumb="$(curl -fsS -c "${cookies}" -u "${JENKINS_ADMIN_USER}:${JENKINS_ADMIN_PASSWORD}" \
    "${jenkins}/crumbIssuer/api/json" \
    | python3 -c 'import json, sys; d = json.load(sys.stdin); print(d["crumbRequestField"] + ":" + d["crumb"])')"
  curl -fsS -o /dev/null -b "${cookies}" -u "${JENKINS_ADMIN_USER}:${JENKINS_ADMIN_PASSWORD}" \
    -H "${crumb}" -X POST "${jenkins}/configuration-as-code/reload"
}

main() {
  validate_env_name "${1:-}"
  local env_name="$1" api_ip="${2:-}" credential_id="kubeconfig-$1" kubeconfig
  [[ "${api_ip}" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] \
    || die "Usage: load-kubeconfig.sh <dev|stage|prod> <node-private-ip> < k3s.yaml"
  require_cmd curl python3
  load_env "${CI_ENV_FILE}"

  kubeconfig="$(cat)"
  [[ "${kubeconfig}" == *"client-key-data"* ]] || die "Expected a k3s kubeconfig on stdin"
  kubeconfig="${kubeconfig//https:\/\/127.0.0.1:6443/https://${api_ip}:6443}"
  kubeconfig="${kubeconfig//: default/: circleguard-${env_name}}"

  install -d -m 700 -o "${JENKINS_UID}" -g "${JENKINS_UID}" "${SECRETS_DIR}"
  (umask 077; printf '%s\n' "${kubeconfig}" > "${SECRETS_DIR}/${credential_id}")
  chown "${JENKINS_UID}:${JENKINS_UID}" "${SECRETS_DIR}/${credential_id}"
  chmod 400 "${SECRETS_DIR}/${credential_id}"

  reload_casc
  info "Jenkins credential ${credential_id} is ready (API https://${api_ip}:6443)"
}

main "$@"
