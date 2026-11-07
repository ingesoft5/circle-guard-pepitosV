#!/usr/bin/env bash
# Stores the deploy SSH key of one environment for Jenkins, idempotently.
# Runs on the CI host and reads the private key from stdin, so it never reaches argv:
#   ssh ubuntu@<ci-host> 'sudo ~/circleguard/infra/bootstrap/load-deploy-credential.sh dev' \
#     < ~/.ssh/circleguard-deploy-dev
# The key is kept in infra/compose/ci/secrets, which casc.yaml reads as deploy-ssh-<env>.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly CI_ENV_FILE="${COMPOSE_DIR}/ci/.env"
readonly SECRETS_DIR="${COMPOSE_DIR}/ci/secrets"
readonly JENKINS_UID=1000

reload_casc() {
  local jenkins="http://localhost:${JENKINS_PORT}" crumb
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
  local env_name="$1" credential_id="deploy-ssh-$1"
  require_cmd curl python3
  load_env "${CI_ENV_FILE}"

  local private_key
  private_key="$(cat)"
  [[ "${private_key}" == *"PRIVATE KEY"* ]] || die "Expected a private key on stdin"

  install -d -m 700 -o "${JENKINS_UID}" -g "${JENKINS_UID}" "${SECRETS_DIR}"
  (umask 077; printf '%s\n' "${private_key}" > "${SECRETS_DIR}/${credential_id}")
  chown "${JENKINS_UID}:${JENKINS_UID}" "${SECRETS_DIR}/${credential_id}"
  chmod 400 "${SECRETS_DIR}/${credential_id}"

  reload_casc
  info "Jenkins credential ${credential_id} is ready"
}

main "$@"
