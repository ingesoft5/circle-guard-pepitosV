#!/usr/bin/env bash
# Aprovisiona SonarQube para el stack CI de CircleGuard de forma idempotente.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly CI_DIR="${COMPOSE_DIR}/ci"
readonly CI_COMPOSE_FILE="${CI_DIR}/docker-compose.yml"
readonly CI_ENV_EXAMPLE="${CI_DIR}/.env.example"
readonly CI_ENV_FILE="${CI_DIR}/.env"
readonly GATE_NAME="CircleGuard Gate"
readonly TOKEN_NAME="circleguard-ci"

sonar_url() {
  printf 'http://localhost:%s' "${SONAR_PORT}"
}

sonar_auth_ok() {
  [[ "$(curl -fsS -u "admin:$1" "$(sonar_url)/api/authentication/validate" 2>/dev/null)" == *'"valid":true'* ]]
}

sonar_api() {
  local method="$1"
  shift
  curl -fsS -u "admin:${SONAR_ADMIN_PASSWORD}" -X "${method}" "$@" >/dev/null
}

json_qualitygate_id() {
  python3 -c '
import json, sys
name = sys.argv[1]
data = json.load(sys.stdin)
print(next((g["id"] for g in data.get("qualitygates", []) if g.get("name") == name), ""))
' "$1"
}

ensure_admin_password() {
  if sonar_auth_ok "${SONAR_ADMIN_PASSWORD}"; then
    info "La contraseña de administrador de SonarQube ya está configurada"
    return 0
  fi

  if sonar_auth_ok "admin"; then
    info "Cambiando la contraseña inicial de SonarQube"
    curl -fsS -u "admin:admin" -X POST "$(sonar_url)/api/users/change_password" \
      --data-urlencode "login=admin" \
      --data-urlencode "previousPassword=admin" \
      --data-urlencode "password=${SONAR_ADMIN_PASSWORD}" >/dev/null
    return 0
  fi

  die "No se pudo autenticar en SonarQube con la contraseña configurada ni con la inicial"
}

ensure_token() {
  local token
  local response
  local tokens_json

  tokens_json="$(curl -fsS -u "admin:${SONAR_ADMIN_PASSWORD}" \
    "$(sonar_url)/api/user_tokens/search")"
  if [[ "${tokens_json}" == *"\"name\":\"${TOKEN_NAME}\""* ]]; then
    if [[ -n "${SONAR_TOKEN:-}" ]]; then
      info "El token de análisis ya existe y está registrado en .env"
    else
      warn "El token existe en SonarQube pero no está en .env; no se puede recuperar. Elimínalo en SonarQube y re-ejecuta."
    fi
    return 0
  fi

  info "Generando token de análisis de SonarQube"
  response="$(curl -fsS -u "admin:${SONAR_ADMIN_PASSWORD}" -X POST \
    "$(sonar_url)/api/user_tokens/generate" \
    --data-urlencode "name=${TOKEN_NAME}")"
  token="$(printf '%s' "${response}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
  [[ -n "${token}" ]] || die "No se pudo extraer el token de la respuesta de SonarQube"
  set_env_value "${CI_ENV_FILE}" SONAR_TOKEN "${token}"
  export SONAR_TOKEN="${token}"
  info "Token de SonarQube generado y guardado en ${CI_ENV_FILE}"
}

ensure_quality_gate() {
  local gate_id gate_json

  gate_id="$(curl -fsS -u "admin:${SONAR_ADMIN_PASSWORD}" \
    "$(sonar_url)/api/qualitygates/list" | json_qualitygate_id "${GATE_NAME}")"

  if [[ -z "${gate_id}" ]]; then
    info "Creando quality gate '${GATE_NAME}'"
    curl -fsS -u "admin:${SONAR_ADMIN_PASSWORD}" -X POST \
      "$(sonar_url)/api/qualitygates/create" \
      --data-urlencode "name=${GATE_NAME}" >/dev/null
    gate_id="$(curl -fsS -u "admin:${SONAR_ADMIN_PASSWORD}" \
      "$(sonar_url)/api/qualitygates/list" | json_qualitygate_id "${GATE_NAME}")"
  fi

  [[ -n "${gate_id}" ]] || die "No se pudo crear ni encontrar el quality gate '${GATE_NAME}'"

  gate_json="$(curl -fsS -u "admin:${SONAR_ADMIN_PASSWORD}" \
    "$(sonar_url)/api/qualitygates/show?id=${gate_id}")"

  if [[ "${gate_json}" != *'"metric":"new_coverage"'* ]]; then
    sonar_api POST "$(sonar_url)/api/qualitygates/create_condition" \
      --data-urlencode "gateId=${gate_id}" \
      --data-urlencode "metric=new_coverage" \
      --data-urlencode "op=LT" \
      --data-urlencode "error=60"
  fi

  if [[ "${gate_json}" != *'"metric":"new_duplicated_lines_density"'* ]]; then
    sonar_api POST "$(sonar_url)/api/qualitygates/create_condition" \
      --data-urlencode "gateId=${gate_id}" \
      --data-urlencode "metric=new_duplicated_lines_density" \
      --data-urlencode "op=GT" \
      --data-urlencode "error=3"
  fi

  sonar_api POST "$(sonar_url)/api/qualitygates/set_as_default" \
    --data-urlencode "name=${GATE_NAME}"
  info "Quality gate '${GATE_NAME}' asegurado como predeterminado"
}

main() {
  acquire_lock /tmp/circleguard-ci-sonarqube.lock
  require_cmd curl python3
  ensure_docker

  ensure_env_file "${CI_ENV_EXAMPLE}" "${CI_ENV_FILE}"
  load_env "${CI_ENV_FILE}"

  export CI_PROJECT CI_NETWORK
  export SONAR_PORT SONAR_ADMIN_PASSWORD SONAR_TOKEN

  info "Aprovisionando SonarQube en el proyecto ${CI_PROJECT}"
  ensure_network "${CI_NETWORK}"
  ensure_volume "${CI_PROJECT}-sonar-db"
  ensure_volume "${CI_PROJECT}-sonar-data"
  ensure_volume "${CI_PROJECT}-sonar-extensions"
  ensure_volume "${CI_PROJECT}-sonar-logs"

  compose --env-file "${CI_ENV_FILE}" -f "${CI_COMPOSE_FILE}" up -d sonar-db sonarqube
  wait_http_pattern "$(sonar_url)/api/system/status" '"status":"UP"' 90 5

  ensure_admin_password
  ensure_token
  ensure_quality_gate

  info "SonarQube listo en $(sonar_url) (usuario: admin)"
}

main "$@"
