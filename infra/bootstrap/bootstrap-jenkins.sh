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
readonly SECRETS_DIR="${CI_DIR}/secrets"
readonly JENKINS_UID=1000

# casc.yaml reads one file per deploy key. A missing file would become a broken credential, so
# each one must exist: an empty file means the key is pending, and deployments will fail.
ensure_deploy_key_files() {
  local env_name key_file
  run_as_root install -d -m 700 -o "${JENKINS_UID}" -g "${JENKINS_UID}" "${SECRETS_DIR}"
  for env_name in dev stage prod; do
    key_file="${SECRETS_DIR}/deploy-ssh-${env_name}"
    if ! run_as_root test -s "${key_file}"; then
      run_as_root install -m 400 -o "${JENKINS_UID}" -g "${JENKINS_UID}" /dev/null "${key_file}"
      warn "No deploy key for ${env_name}: run load-deploy-credential.sh ${env_name} before deploying"
    fi
  done
}

main() {
  acquire_lock /tmp/circleguard-ci-jenkins.lock
  require_cmd curl
  ensure_docker

  ensure_env_file "${CI_ENV_EXAMPLE}" "${CI_ENV_FILE}"
  ensure_registry_env_keys "${CI_ENV_FILE}"
  load_env "${CI_ENV_FILE}"

  export CI_PROJECT CI_NETWORK
  export JENKINS_PORT JENKINS_ADMIN_USER JENKINS_ADMIN_PASSWORD
  export GITHUB_OWNER GITHUB_REPO GITHUB_USER GITHUB_TOKEN
  export DISCORD_WEBHOOK_URL
  export NEXUS_REGISTRY IMAGE_NAMESPACE NEXUS_CI_USER NEXUS_CI_PASSWORD
  DOCKER_GID="$(stat -c '%g' /var/run/docker.sock 2>/dev/null || echo 0)"
  export DOCKER_GID
  # Saved in .env so a direct "docker compose up" also keeps Jenkins' access to Docker.
  set_env_value "${CI_ENV_FILE}" DOCKER_GID "${DOCKER_GID}"

  if [[ "${GITHUB_TOKEN:-}" == "" || "${GITHUB_TOKEN}" == "__SET_ME__" ]]; then
    warn "GITHUB_TOKEN no está configurado; el job multibranch no podrá escanear GitHub"
  fi

  if [[ "${DISCORD_WEBHOOK_URL:-}" == "" || "${DISCORD_WEBHOOK_URL}" == "__SET_ME__" ]]; then
    warn "DISCORD_WEBHOOK_URL no está configurado; las notificaciones de Discord no se enviarán"
  fi

  info "Aprovisionando Jenkins en el proyecto ${CI_PROJECT}"
  ensure_network "${CI_NETWORK}"
  ensure_volume "${CI_PROJECT}-jenkins-home"
  ensure_deploy_key_files

  # --build: la imagen de Jenkins agrega el Docker CLI; Docker reutiliza la caché
  # si jenkins/Dockerfile no cambió.
  compose --env-file "${CI_ENV_FILE}" -f "${CI_COMPOSE_FILE}" up -d --build jenkins
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
