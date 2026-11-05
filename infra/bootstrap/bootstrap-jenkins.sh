#!/usr/bin/env bash
# Aprovisiona Jenkins para el stack CI de CircleGuard de forma idempotente.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly CI_DIR="${COMPOSE_DIR}/ci"
readonly CI_COMPOSE_FILE="${CI_DIR}/docker-compose.yml"
readonly CI_ENV_EXAMPLE="${CI_DIR}/.env.example"
readonly CI_ENV_FILE="${CI_DIR}/.env"

main() {
  acquire_lock /tmp/circleguard-ci-jenkins.lock
  require_cmd curl
  ensure_docker

  ensure_env_file "${CI_ENV_EXAMPLE}" "${CI_ENV_FILE}"
  load_env "${CI_ENV_FILE}"

  export CI_PROJECT CI_NETWORK
  export JENKINS_PORT JENKINS_ADMIN_USER JENKINS_ADMIN_PASSWORD
  export GITHUB_OWNER GITHUB_REPO GITHUB_USER GITHUB_TOKEN
  DOCKER_GID="$(stat -c '%g' /var/run/docker.sock 2>/dev/null || echo 0)"
  export DOCKER_GID

  if [[ "${GITHUB_TOKEN:-}" == "" || "${GITHUB_TOKEN}" == "__SET_ME__" ]]; then
    warn "GITHUB_TOKEN no está configurado; el job multibranch no podrá escanear GitHub"
  fi

  info "Aprovisionando Jenkins en el proyecto ${CI_PROJECT}"
  ensure_network "${CI_NETWORK}"
  ensure_volume "${CI_PROJECT}-jenkins-home"

  compose --env-file "${CI_ENV_FILE}" -f "${CI_COMPOSE_FILE}" up -d jenkins
  wait_http "http://localhost:${JENKINS_PORT}/login" 60 5

  info "Instalando/actualizando los plugins declarados"
  docker_cli exec "${CI_PROJECT}-jenkins" \
    jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

  info "Reiniciando Jenkins para aplicar plugins y JCasC"
  docker_cli restart "${CI_PROJECT}-jenkins" >/dev/null
  wait_http "http://localhost:${JENKINS_PORT}/login" 60 5

  info "Jenkins listo en http://localhost:${JENKINS_PORT} (usuario: ${JENKINS_ADMIN_USER})"
}

main "$@"
