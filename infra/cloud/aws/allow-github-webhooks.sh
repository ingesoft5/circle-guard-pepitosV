#!/usr/bin/env bash
# Opens the Jenkins port on the CI Security Group to GitHub's webhook ranges, idempotently.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/../../bootstrap/lib/common.sh"

readonly CI_SG_NAME="${CI_SG_NAME:-circleguard-ci-sg}"
readonly JENKINS_PORT="${JENKINS_PORT:-8080}"
export AWS_DEFAULT_REGION="${AWS_REGION:-us-east-1}"

main() {
  require_cmd aws curl jq
  local sg_id cidr output
  sg_id="$(aws ec2 describe-security-groups --filters "Name=group-name,Values=${CI_SG_NAME}" \
    --query 'SecurityGroups[0].GroupId' --output text)"
  [[ "${sg_id}" == sg-* ]] || die "Security Group not found: ${CI_SG_NAME}"

  while IFS= read -r cidr; do
    if output="$(aws ec2 authorize-security-group-ingress \
      --group-id "${sg_id}" --protocol tcp --port "${JENKINS_PORT}" --cidr "${cidr}" 2>&1)"; then
      info "Rule added: ${JENKINS_PORT}/tcp from ${cidr}"
    elif grep -q "InvalidPermission.Duplicate" <<<"${output}"; then
      info "Rule already exists: ${JENKINS_PORT}/tcp from ${cidr}"
    else
      die "Could not add rule ${JENKINS_PORT}/tcp from ${cidr}: ${output}"
    fi
  done < <(curl -fsS https://api.github.com/meta | jq -r '.hooks[] | select(contains(":") | not)')
}

main "$@"
