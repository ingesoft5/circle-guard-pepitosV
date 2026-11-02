#!/usr/bin/env bash
# Orquestador idempotente del stack CI de CircleGuard:
# Jenkins -> SonarQube -> Nexus, con verificación final de endpoints.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly CI_DIR="${COMPOSE_DIR}/ci"
readonly CI_ENV_EXAMPLE="${CI_DIR}/.env.example"
readonly CI_ENV_FILE="${CI_DIR}/.env"

wait_for_ci_stack() {
  wait_http "http://localhost:${JENKINS_PORT}/login" 60 5
  wait_http_pattern "http://localhost:${SONAR_PORT}/api/system/status" '"status":"UP"' 90 5
  wait_http "http://localhost:${NEXUS_PORT}/service/rest/v1/status" 60 5
}

main() {
  acquire_lock /tmp/circleguard-ci-bootstrap.lock
  require_cmd docker curl
  ensure_docker

  ensure_env_file "${CI_ENV_EXAMPLE}" "${CI_ENV_FILE}"
  load_env "${CI_ENV_FILE}"

  export CI_PROJECT CI_NETWORK
  export JENKINS_PORT SONAR_PORT NEXUS_PORT

  info "Iniciando bootstrap del stack CI (${CI_PROJECT})"
  bash "${SCRIPT_DIR}/bootstrap-jenkins.sh"
  bash "${SCRIPT_DIR}/bootstrap-sonarqube.sh"
  bash "${SCRIPT_DIR}/bootstrap-nexus.sh"

  info "Verificando endpoints del stack CI"
  wait_for_ci_stack

  info "Stack CI listo"
  info "  Jenkins:   http://localhost:${JENKINS_PORT}"
  info "  SonarQube: http://localhost:${SONAR_PORT}"
  info "  Nexus:     http://localhost:${NEXUS_PORT}"
  info "  Registry:  localhost:${NEXUS_REGISTRY_PORT}/docker-hosted"
}

main "$@"
