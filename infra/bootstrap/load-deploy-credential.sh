#!/usr/bin/env bash
# Stores the deploy SSH key of one environment as a Jenkins credential, idempotently.
# Runs on the CI host and reads the private key from stdin, so it never reaches disk or argv:
#   ssh ubuntu@<ci-host> 'sudo ~/circleguard/infra/bootstrap/load-deploy-credential.sh dev' \
#     < ~/.ssh/circleguard-deploy-dev
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly CI_ENV_FILE="${COMPOSE_DIR}/ci/.env"
readonly DEPLOY_USER="${DEPLOY_USER:-deploy}"

credential_xml() {
  local credential_id="$1" env_name="$2" private_key="$3"
  cat <<EOF
<com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey>
  <scope>GLOBAL</scope>
  <id>${credential_id}</id>
  <description>Deploy key for ${env_name}</description>
  <username>${DEPLOY_USER}</username>
  <privateKeySource class="com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey\$DirectEntryPrivateKeySource">
    <privateKey>${private_key}</privateKey>
  </privateKeySource>
</com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey>
EOF
}

main() {
  validate_env_name "${1:-}"
  local env_name="$1" credential_id="deploy-ssh-$1"
  require_cmd curl python3
  load_env "${CI_ENV_FILE}"

  local private_key
  private_key="$(cat)"
  [[ "${private_key}" == *"PRIVATE KEY"* ]] || die "Expected a private key on stdin"

  local jenkins="http://localhost:${JENKINS_PORT}" store crumb
  store="${jenkins}/credentials/store/system/domain/_"
  cookies="$(mktemp)"
  trap 'rm -f "${cookies}"' EXIT
  crumb="$(curl -fsS -c "${cookies}" -u "${JENKINS_ADMIN_USER}:${JENKINS_ADMIN_PASSWORD}" \
    "${jenkins}/crumbIssuer/api/json" \
    | python3 -c 'import json, sys; d = json.load(sys.stdin); print(d["crumbRequestField"] + ":" + d["crumb"])')"

  local target
  if curl -fsS -o /dev/null -u "${JENKINS_ADMIN_USER}:${JENKINS_ADMIN_PASSWORD}" \
    "${store}/credential/${credential_id}/api/json" 2>/dev/null; then
    target="${store}/credential/${credential_id}/config.xml"
    info "Updating Jenkins credential ${credential_id}"
  else
    target="${store}/createCredentials"
    info "Creating Jenkins credential ${credential_id}"
  fi

  credential_xml "${credential_id}" "${env_name}" "${private_key}" \
    | curl -fsS -o /dev/null -b "${cookies}" -u "${JENKINS_ADMIN_USER}:${JENKINS_ADMIN_PASSWORD}" \
      -H "${crumb}" -H "Content-Type: application/xml" -X POST --data-binary @- "${target}"
  info "Jenkins credential ${credential_id} is ready"
}

main "$@"
