#!/usr/bin/env bash
# Prepares a Jenkins build agent host (circleguard-ci-agent-<n>) and connects it to the
# controller. Idempotent: run it again to update the image or the secret.
#   sudo JENKINS_URL=http://<ci-private-ip>:8080/ JENKINS_AGENT_NAME=ci-agent-1 \
#        JENKINS_AGENT_SECRET=<secret> NEXUS_REGISTRY=<tools-private-ip>:5000 \
#        infra/bootstrap/bootstrap-ci-agent.sh
# The secret is shown in Jenkins (Manage Jenkins > Nodes > ci-agent-1); it is saved in
# infra/compose/ci-agent/secrets/agent-secret (mode 600, never versioned). Values given once are
# kept in infra/compose/ci-agent/.env, so later runs need no arguments.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly AGENT_DIR="${COMPOSE_DIR}/ci-agent"
readonly AGENT_ENV="${AGENT_DIR}/.env"
readonly AGENT_SECRET_FILE="${AGENT_DIR}/secrets/agent-secret"
# uid of the jenkins user inside the image, which reads the secret.
readonly JENKINS_UID=1000

main() {
  acquire_lock /tmp/circleguard-ci-agent-bootstrap.lock
  require_cmd curl
  ensure_docker
  bash "${SCRIPT_DIR}/ensure-swap.sh"

  local key
  touch "${AGENT_ENV}"
  chmod 600 "${AGENT_ENV}"
  for key in JENKINS_URL JENKINS_AGENT_NAME NEXUS_REGISTRY; do
    if [[ -n "${!key:-}" ]]; then
      set_env_value "${AGENT_ENV}" "${key}" "${!key}"
    fi
  done
  set_env_value "${AGENT_ENV}" DOCKER_GID "$(stat -c '%g' /var/run/docker.sock)"
  load_env "${AGENT_ENV}"
  [[ -n "${JENKINS_URL:-}" ]] || die "Define JENKINS_URL (private address of the controller)"
  [[ -n "${JENKINS_AGENT_NAME:-}" ]] || die "Define JENKINS_AGENT_NAME (for example ci-agent-1)"

  # Builds push to and pull from Nexus over plain HTTP, like the controller host.
  ensure_registry_trust "${NEXUS_REGISTRY:-}"

  install -d -m 700 -o "${JENKINS_UID}" "$(dirname "${AGENT_SECRET_FILE}")"
  if [[ -n "${JENKINS_AGENT_SECRET:-}" ]]; then
    printf '%s' "${JENKINS_AGENT_SECRET}" >"${AGENT_SECRET_FILE}"
  fi
  [[ -s "${AGENT_SECRET_FILE}" ]] || die "Pass JENKINS_AGENT_SECRET once (shown in Jenkins for ${JENKINS_AGENT_NAME})"
  chown "${JENKINS_UID}" "${AGENT_SECRET_FILE}"
  chmod 400 "${AGENT_SECRET_FILE}"

  (cd "${AGENT_DIR}" && compose up -d --build)

  local attempt
  for attempt in $(seq 1 30); do
    if docker_cli logs "${CI_AGENT_PROJECT:-circleguard-ci-agent}" 2>&1 | grep -q "INFO: Connected"; then
      info "Agent ${JENKINS_AGENT_NAME} connected to ${JENKINS_URL}"
      return 0
    fi
    sleep 5
  done
  die "Agent did not connect; see: docker logs ${CI_AGENT_PROJECT:-circleguard-ci-agent}"
}

main "$@"
