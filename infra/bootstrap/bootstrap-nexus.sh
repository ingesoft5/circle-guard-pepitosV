#!/usr/bin/env bash
# Aprovisiona Nexus para el stack CI de CircleGuard de forma idempotente.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly CI_DIR="${COMPOSE_DIR}/ci"
readonly CI_COMPOSE_FILE="${CI_DIR}/docker-compose.yml"
readonly CI_ENV_EXAMPLE="${CI_DIR}/.env.example"
readonly CI_ENV_FILE="${CI_DIR}/.env"

nexus_url() {
  printf 'http://localhost:%s' "${NEXUS_PORT}"
}

nexus_auth_ok() {
  curl -fsS -u "admin:$1" "$(nexus_url)/service/rest/v1/status" >/dev/null 2>&1
}

nexus_api() {
  local method="$1"
  shift
  curl -fsS -u "admin:${NEXUS_ADMIN_PASSWORD}" -X "${method}" "$@"
}

nexus_repo_exists() {
  nexus_api GET "$(nexus_url)/service/rest/v1/repositories/$1" >/dev/null 2>&1
}

ensure_admin_password() {
  local initial_password

  if nexus_auth_ok "${NEXUS_ADMIN_PASSWORD}"; then
    info "La contraseña de administrador de Nexus ya está configurada"
    return 0
  fi

  info "Leyendo la contraseña inicial de Nexus"
  retry 30 5 docker_cli exec "${CI_PROJECT}-nexus" test -s /nexus-data/admin.password \
    || die "No se encontró /nexus-data/admin.password en el contenedor de Nexus"
  initial_password="$(docker_cli exec "${CI_PROJECT}-nexus" cat /nexus-data/admin.password | tr -d '\r\n')"
  [[ -n "${initial_password}" ]] || die "No se pudo leer la contraseña inicial de Nexus"

  info "Cambiando la contraseña inicial de Nexus"
  curl -fsS -u "admin:${initial_password}" -X PUT \
    -H "Content-Type: text/plain" \
    --data "${NEXUS_ADMIN_PASSWORD}" \
    "$(nexus_url)/service/rest/v1/security/users/admin/change-password" >/dev/null

  nexus_auth_ok "${NEXUS_ADMIN_PASSWORD}" \
    || die "No se pudo autenticar en Nexus con la nueva contraseña"
}

ensure_repositories() {
  if nexus_repo_exists "docker-hosted"; then
    info "El repositorio Docker 'docker-hosted' ya existe"
  else
    info "Creando repositorio Docker 'docker-hosted' con puerto ${NEXUS_REGISTRY_PORT}"
    nexus_api POST "$(nexus_url)/service/rest/v1/repositories/docker/hosted" \
      -H "Content-Type: application/json" \
      -d "{\"name\":\"docker-hosted\",\"online\":true,\"storage\":{\"blobStoreName\":\"default\",\"strictContentTypeValidation\":true,\"writePolicy\":\"allow\"},\"docker\":{\"httpPort\":${NEXUS_REGISTRY_PORT},\"httpsPort\":null,\"forceBasicAuth\":true,\"v1Enabled\":false}}" >/dev/null
  fi

  if nexus_repo_exists "raw-hosted"; then
    info "El repositorio raw 'raw-hosted' ya existe"
  else
    info "Creando repositorio raw 'raw-hosted'"
    nexus_api POST "$(nexus_url)/service/rest/v1/repositories/raw/hosted" \
      -H "Content-Type: application/json" \
      -d '{"name":"raw-hosted","online":true,"storage":{"blobStoreName":"default","strictContentTypeValidation":true,"writePolicy":"allow"}}' >/dev/null
  fi
}

main() {
  acquire_lock /tmp/circleguard-ci-nexus.lock
  require_cmd curl
  ensure_docker

  ensure_env_file "${CI_ENV_EXAMPLE}" "${CI_ENV_FILE}"
  load_env "${CI_ENV_FILE}"

  export CI_PROJECT CI_NETWORK
  export NEXUS_PORT NEXUS_REGISTRY_PORT NEXUS_ADMIN_USER NEXUS_ADMIN_PASSWORD

  info "Aprovisionando Nexus en el proyecto ${CI_PROJECT}"
  ensure_network "${CI_NETWORK}"
  ensure_volume "${CI_PROJECT}-nexus-data"

  compose --env-file "${CI_ENV_FILE}" -f "${CI_COMPOSE_FILE}" up -d nexus
  wait_http "$(nexus_url)/service/rest/v1/status" 60 5

  ensure_admin_password
  ensure_repositories

  info "Nexus listo en $(nexus_url) (usuario: ${NEXUS_ADMIN_USER})"
  info "Registro Docker interno: localhost:${NEXUS_REGISTRY_PORT}/docker-hosted"
}

main "$@"
