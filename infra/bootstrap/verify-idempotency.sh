#!/usr/bin/env bash
# Verifica la idempotencia del bootstrap del stack CI:
# ejecuta bootstrap-all.sh dos veces, compara contenedores/volúmenes,
# comprueba persistencia de datos y valida los endpoints.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly CI_DIR="${COMPOSE_DIR}/ci"
readonly CI_ENV_EXAMPLE="${CI_DIR}/.env.example"
readonly CI_ENV_FILE="${CI_DIR}/.env"
readonly EVIDENCE_DIR="${REPO_ROOT}/docs/evidencias/HU-03-HU-04"
readonly MARKER_FILE="/var/jenkins_home/.idempotency-marker"
readonly MARKER_VALUE="circleguard-idempotency-marker"

snapshot_containers() {
  docker_cli ps -a --format '{{.Names}}' | grep -E "^${CI_PROJECT}-" | sort || true
}

snapshot_volumes() {
  docker_cli volume ls --format '{{.Name}}' | grep -E "^${CI_PROJECT}-" | sort || true
}

wait_for_ci_stack() {
  wait_http "http://localhost:${JENKINS_PORT}/login" 60 5
  wait_http_pattern "http://localhost:${SONAR_PORT}/api/system/status" '"status":"UP"' 90 5
  wait_http "http://localhost:${NEXUS_PORT}/service/rest/v1/status" 60 5
}

assert_expected_containers() {
  local name
  for name in \
    "${CI_PROJECT}-jenkins" \
    "${CI_PROJECT}-sonar-db" \
    "${CI_PROJECT}-sonarqube" \
    "${CI_PROJECT}-nexus"; do
    container_running "${name}" || die "El contenedor ${name} no está corriendo"
  done
}

main() {
  acquire_lock /tmp/circleguard-ci-verify.lock
  require_cmd docker curl
  ensure_docker

  ensure_env_file "${CI_ENV_EXAMPLE}" "${CI_ENV_FILE}"
  load_env "${CI_ENV_FILE}"

  export CI_PROJECT CI_NETWORK
  export JENKINS_PORT SONAR_PORT NEXUS_PORT

  mkdir -p "${EVIDENCE_DIR}"
  local log_file
  log_file="${EVIDENCE_DIR}/verify-idempotency-$(date +%Y%m%d-%H%M%S).log"
  exec > >(tee -a "${log_file}") 2>&1

  info "Evidencia: ${log_file}"
  info "Primera ejecución de bootstrap-all.sh"
  bash "${SCRIPT_DIR}/bootstrap-all.sh"
  wait_for_ci_stack
  assert_expected_containers

  local containers_before volumes_before containers_after volumes_after
  containers_before="$(snapshot_containers)"
  volumes_before="$(snapshot_volumes)"

  info "Escribiendo marcador de persistencia en el volumen de Jenkins"
  docker_cli exec "${CI_PROJECT}-jenkins" sh -c \
    "printf '%s\n' '${MARKER_VALUE}' > '${MARKER_FILE}'"

  info "Segunda ejecución de bootstrap-all.sh"
  bash "${SCRIPT_DIR}/bootstrap-all.sh"
  wait_for_ci_stack
  assert_expected_containers

  containers_after="$(snapshot_containers)"
  volumes_after="$(snapshot_volumes)"

  if [[ "${containers_before}" != "${containers_after}" ]]; then
    diff -u <(printf '%s\n' "${containers_before}") <(printf '%s\n' "${containers_after}") >&2 || true
    die "La segunda ejecución modificó los contenedores del stack CI"
  fi

  if [[ "${volumes_before}" != "${volumes_after}" ]]; then
    diff -u <(printf '%s\n' "${volumes_before}") <(printf '%s\n' "${volumes_after}") >&2 || true
    die "La segunda ejecución creó o eliminó volúmenes del stack CI"
  fi

  local marker
  marker="$(docker_cli exec "${CI_PROJECT}-jenkins" cat "${MARKER_FILE}" | tr -d '\r\n')"
  [[ "${marker}" == "${MARKER_VALUE}" ]] \
    || die "Se perdió el marcador de persistencia en el volumen de Jenkins"

  info "Idempotencia verificada correctamente"
  info "  Contenedores estables: ${containers_after//$'\n'/ }"
  info "  Volúmenes estables: ${volumes_after//$'\n'/ }"
  info "  Persistencia de datos: OK"
}

main "$@"
