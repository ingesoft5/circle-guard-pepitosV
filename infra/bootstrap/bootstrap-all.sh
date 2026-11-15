#!/usr/bin/env bash
# Orquestador idempotente del stack CI de CircleGuard, con verificación final de endpoints.
# CI_ROLE (sudo CI_ROLE=tools ./bootstrap-all.sh, o en .env) elige qué levanta este host:
#   all     SonarQube -> Jenkins -> Nexus en un solo host (por defecto)
#   tools   SonarQube y Nexus (host circleguard-tools)
#   jenkins solo Jenkins (host circleguard-ci); usa SONAR_HOST_URL y NEXUS_REGISTRY del host tools
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly CI_DIR="${COMPOSE_DIR}/ci"
readonly CI_ENV_EXAMPLE="${CI_DIR}/.env.example"
readonly CI_ENV_FILE="${CI_DIR}/.env"
readonly HOST_LINK_KEYS=(CI_ROLE JENKINS_WEBHOOK_URL NEXUS_REGISTRY NEXUS_REGISTRY_API SONAR_HOST_URL)

wait_for_ci_stack() {
  if [[ "${CI_ROLE}" != "tools" ]]; then
    wait_http "http://localhost:${JENKINS_PORT}/login" 60 5
  fi
  if [[ "${CI_ROLE}" != "jenkins" ]]; then
    wait_http_pattern "http://localhost:${SONAR_PORT}/api/system/status" '"status":"UP"' 90 5
    wait_http "http://localhost:${NEXUS_PORT}/service/rest/v1/status" 60 5
  fi
}

main() {
  acquire_lock /tmp/circleguard-ci-bootstrap.lock
  require_cmd curl
  ensure_docker
  bash "${SCRIPT_DIR}/ensure-swap.sh"

  # The role and the addresses between the two CI hosts can be given on the command line
  # (sudo CI_ROLE=tools JENKINS_WEBHOOK_URL=... ./bootstrap-all.sh). They win over .env and
  # are saved there, so the next runs need no arguments.
  local key value
  local -A requested=()
  for key in "${HOST_LINK_KEYS[@]}"; do
    if [[ -n "${!key:-}" ]]; then
      requested[${key}]="${!key}"
    fi
  done
  ensure_env_file "${CI_ENV_EXAMPLE}" "${CI_ENV_FILE}"
  for key in "${!requested[@]}"; do
    value="${requested[${key}]}"
    set_env_value "${CI_ENV_FILE}" "${key}" "${value}"
  done
  load_env "${CI_ENV_FILE}"

  export CI_PROJECT CI_NETWORK
  export JENKINS_PORT SONAR_PORT NEXUS_PORT

  CI_ROLE="${CI_ROLE:-all}"
  case "${CI_ROLE}" in
    all | tools | jenkins) ;;
    *) die "CI_ROLE must be all, tools or jenkins (got: ${CI_ROLE})" ;;
  esac
  export CI_ROLE

  info "Iniciando bootstrap del stack CI (${CI_PROJECT}, rol ${CI_ROLE})"
  if [[ "${CI_ROLE}" != "jenkins" ]]; then
    bash "${SCRIPT_DIR}/bootstrap-sonarqube.sh"
  fi
  if [[ "${CI_ROLE}" != "tools" ]]; then
    bash "${SCRIPT_DIR}/bootstrap-jenkins.sh"
  fi
  if [[ "${CI_ROLE}" != "jenkins" ]]; then
    bash "${SCRIPT_DIR}/bootstrap-nexus.sh"
  fi

  info "Verificando endpoints del stack CI"
  wait_for_ci_stack

  info "Stack CI listo (rol ${CI_ROLE})"
  if [[ "${CI_ROLE}" != "tools" ]]; then
    info "  Jenkins:   http://localhost:${JENKINS_PORT}"
  fi
  if [[ "${CI_ROLE}" != "jenkins" ]]; then
    info "  SonarQube: http://localhost:${SONAR_PORT}"
    info "  Nexus:     http://localhost:${NEXUS_PORT}"
    info "  Registry:  localhost:${NEXUS_REGISTRY_PORT} (imágenes: <registro>/${IMAGE_NAMESPACE:-circleguard}/<nombre>:<versión>)"
  fi
}

main "$@"
