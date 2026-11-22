#!/usr/bin/env bash
# Stores the credentials used by the Terraform pipeline steps (HU-32) on the CI host,
# idempotently. Reads the value from stdin so it never reaches argv or the repository:
#   ssh ubuntu@<ci-host> 'sudo ~/circleguard/infra/bootstrap/load-terraform-credential.sh aws' \
#     < ~/.aws/credentials-terraform
#   ssh ubuntu@<ci-host> 'sudo ~/circleguard/infra/bootstrap/load-terraform-credential.sh ssh' \
#     < ~/.ssh/circleguard-admin-key
#   ssh ubuntu@<ci-host> 'sudo ~/circleguard/infra/bootstrap/load-terraform-credential.sh grafana' \
#     < ~/grafana-admin-password.txt
# The files are kept in infra/compose/ci/secrets, which casc.yaml reads as the Jenkins
# credentials aws-terraform, prometheus-ssh-key and prometheus-grafana-password.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly CI_ENV_FILE="${COMPOSE_DIR}/ci/.env"
readonly SECRETS_DIR="${COMPOSE_DIR}/ci/secrets"
readonly JENKINS_UID=1000

reload_cookies=""
cleanup_reload_cookies() {
  [[ -n "${reload_cookies}" ]] && rm -f "${reload_cookies}"
}

reload_casc() {
  local jenkins="http://localhost:${JENKINS_PORT}" crumb
  reload_cookies="$(mktemp)"
  trap cleanup_reload_cookies EXIT
  crumb="$(curl -fsS -c "${reload_cookies}" -u "${JENKINS_ADMIN_USER}:${JENKINS_ADMIN_PASSWORD}" \
    "${jenkins}/crumbIssuer/api/json" \
    | python3 -c 'import json, sys; d = json.load(sys.stdin); print(d["crumbRequestField"] + ":" + d["crumb"])')"
  curl -fsS -o /dev/null -b "${reload_cookies}" -u "${JENKINS_ADMIN_USER}:${JENKINS_ADMIN_PASSWORD}" \
    -H "${crumb}" -X POST "${jenkins}/configuration-as-code/reload"
}

write_secret() {
  local name="$1" value="$2" suffix="${3:-}"
  install -d -m 700 -o "${JENKINS_UID}" -g "${JENKINS_UID}" "${SECRETS_DIR}"
  (umask 077; printf '%s%s' "${value}" "${suffix}" >"${SECRETS_DIR}/${name}")
  chown "${JENKINS_UID}:${JENKINS_UID}" "${SECRETS_DIR}/${name}"
  chmod 400 "${SECRETS_DIR}/${name}"
}

main() {
  local kind="${1:-}" content
  case "${kind}" in
    aws | ssh | grafana) ;;
    *) die "Usage: load-terraform-credential.sh <aws|ssh|grafana> < value" ;;
  esac

  require_cmd curl python3
  load_env "${CI_ENV_FILE}"
  content="$(cat)"
  [[ -n "${content}" ]] || die "Expected a value on stdin"

  case "${kind}" in
    aws)
      [[ "${content}" == *"[default]"* && "${content}" == *"aws_access_key_id"* ]] \
        || die "Expected an AWS credentials file with [default] and aws_access_key_id"
      write_secret "aws-terraform" "${content}" $'\n'
      ;;
    ssh)
      [[ "${content}" == *"PRIVATE KEY"* ]] || die "Expected a private key on stdin"
      write_secret "prometheus-ssh-key" "${content}" $'\n'
      ;;
    grafana)
      write_secret "prometheus-grafana-password" "${content}"
      ;;
  esac

  reload_casc
  info "Jenkins Terraform credential ready: ${kind}"
}

main "$@"
