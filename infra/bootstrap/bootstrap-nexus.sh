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

# writePolicy allow_once: un tag publicado no se puede sobrescribir, así una
# versión siempre apunta a la misma imagen (desplegar y revertir versiones exactas).
docker_hosted_body() {
  printf '{"name":"docker-hosted","online":true,"storage":{"blobStoreName":"default","strictContentTypeValidation":true,"writePolicy":"allow_once"},"docker":{"httpPort":%s,"httpsPort":null,"forceBasicAuth":true,"v1Enabled":false}}' \
    "${NEXUS_REGISTRY_PORT}"
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
    info "El repositorio Docker 'docker-hosted' ya existe; aplicando su configuración"
    nexus_api PUT "$(nexus_url)/service/rest/v1/repositories/docker/hosted/docker-hosted" \
      -H "Content-Type: application/json" \
      -d "$(docker_hosted_body)" >/dev/null
  else
    info "Creando repositorio Docker 'docker-hosted' con puerto ${NEXUS_REGISTRY_PORT}"
    nexus_api POST "$(nexus_url)/service/rest/v1/repositories/docker/hosted" \
      -H "Content-Type: application/json" \
      -d "$(docker_hosted_body)" >/dev/null
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

# El realm "Docker Bearer Token" es el que acepta el docker login contra el registro.
ensure_docker_realm() {
  local active updated
  active="$(nexus_api GET "$(nexus_url)/service/rest/v1/security/realms/active")"
  if [[ "${active}" == *'"DockerToken"'* ]]; then
    info "El realm 'Docker Bearer Token' ya está activo"
    return 0
  fi

  if [[ "${active}" =~ ^\[[[:space:]]*\]$ ]]; then
    updated='["DockerToken"]'
  else
    updated="${active%]*}, \"DockerToken\"]"
  fi
  nexus_api PUT "$(nexus_url)/service/rest/v1/security/realms/active" \
    -H "Content-Type: application/json" -d "${updated}" >/dev/null
  info "Realm 'Docker Bearer Token' activado"
}

ensure_role() {
  local id="$1" description="$2" privileges="$3" body
  body="$(printf '{"id":"%s","name":"%s","description":"%s","privileges":%s,"roles":[]}' \
    "${id}" "${id}" "${description}" "${privileges}")"

  if nexus_api GET "$(nexus_url)/service/rest/v1/security/roles/${id}" >/dev/null 2>&1; then
    nexus_api PUT "$(nexus_url)/service/rest/v1/security/roles/${id}" \
      -H "Content-Type: application/json" -d "${body}" >/dev/null
    info "Rol '${id}' actualizado"
  else
    nexus_api POST "$(nexus_url)/service/rest/v1/security/roles" \
      -H "Content-Type: application/json" -d "${body}" >/dev/null
    info "Rol '${id}' creado"
  fi
}

# Crea el usuario o, si ya existe, sincroniza su rol y su contraseña con el .env.
ensure_user() {
  local user="$1" password="$2" role="$3" existing body
  body="$(printf '{"userId":"%s","firstName":"%s","lastName":"CircleGuard","emailAddress":"%s@circleguard.local","source":"default","status":"active","readOnly":false,"roles":["%s"]' \
    "${user}" "${user}" "${user}" "${role}")"

  existing="$(nexus_api GET "$(nexus_url)/service/rest/v1/security/users?userId=${user}&source=default")"
  if [[ "${existing}" =~ \"userId\"[[:space:]]*:[[:space:]]*\"${user}\" ]]; then
    nexus_api PUT "$(nexus_url)/service/rest/v1/security/users/${user}" \
      -H "Content-Type: application/json" -d "${body}}" >/dev/null
    nexus_api PUT "$(nexus_url)/service/rest/v1/security/users/${user}/change-password" \
      -H "Content-Type: text/plain" --data "${password}" >/dev/null
    info "Usuario '${user}' actualizado (rol ${role})"
  else
    nexus_api POST "$(nexus_url)/service/rest/v1/security/users" \
      -H "Content-Type: application/json" -d "${body},\"password\":\"${password}\"}" >/dev/null
    info "Usuario '${user}' creado (rol ${role})"
  fi
}

# Usuarios con privilegio mínimo: Jenkins publica, los ambientes solo descargan.
ensure_registry_users() {
  local prefix="nx-repository-view-docker-docker-hosted"
  ensure_role "circleguard-docker-push" "Push y pull de imágenes en docker-hosted (Jenkins)" \
    "[\"${prefix}-add\",\"${prefix}-edit\",\"${prefix}-read\",\"${prefix}-browse\"]"
  ensure_role "circleguard-docker-pull" "Solo pull de imágenes en docker-hosted (ambientes)" \
    "[\"${prefix}-read\",\"${prefix}-browse\"]"

  ensure_user "${NEXUS_CI_USER}" "${NEXUS_CI_PASSWORD}" "circleguard-docker-push"
  ensure_user "${NEXUS_PULL_USER}" "${NEXUS_PULL_PASSWORD}" "circleguard-docker-pull"
}

main() {
  acquire_lock /tmp/circleguard-ci-nexus.lock
  require_cmd curl
  ensure_docker

  ensure_env_file "${CI_ENV_EXAMPLE}" "${CI_ENV_FILE}"
  ensure_registry_env_keys "${CI_ENV_FILE}"
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
  ensure_docker_realm
  ensure_registry_users

  info "Nexus listo en $(nexus_url) (usuario: ${NEXUS_ADMIN_USER})"
  info "Registro Docker: localhost:${NEXUS_REGISTRY_PORT} (imágenes: localhost:${NEXUS_REGISTRY_PORT}/${IMAGE_NAMESPACE}/<nombre>:<versión>)"
  info "Usuarios del registro: ${NEXUS_CI_USER} (push, Jenkins) y ${NEXUS_PULL_USER} (pull, ambientes)"
}

main "$@"
