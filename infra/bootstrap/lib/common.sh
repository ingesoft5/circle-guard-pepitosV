#!/usr/bin/env bash
# Utilidades compartidas por los scripts de bootstrap de infraestructura de CircleGuard.
# Este archivo está pensado para ser cargado, no ejecutado directamente:
#   source "$(dirname "$0")/lib/common.sh"

set -Eeuo pipefail

readonly INFRA_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPO_ROOT="$(cd "${INFRA_ROOT}/.." && pwd)"
readonly DEPLOY_DIR="${REPO_ROOT}/deploy"
readonly ENVS_DIR="${DEPLOY_DIR}/compose"
readonly COMPOSE_DIR="${INFRA_ROOT}/compose"
readonly SECURITY_DIR="${INFRA_ROOT}/security"

if [[ -t 1 ]]; then
  readonly C_RESET=$'\033[0m'
  readonly C_DIM=$'\033[2m'
  readonly C_RED=$'\033[0;31m'
  readonly C_GREEN=$'\033[0;32m'
  readonly C_YELLOW=$'\033[0;33m'
  readonly C_BLUE=$'\033[0;34m'
else
  readonly C_RESET=""
  readonly C_DIM=""
  readonly C_RED=""
  readonly C_GREEN=""
  readonly C_YELLOW=""
  readonly C_BLUE=""
fi

info() {
  printf '%s[%s] INFO %s %s\n' "${C_DIM}" "$(date +%H:%M:%S)" "${C_RESET}" "$*"
}

warn() {
  printf '%s[%s] WARN %s %s\n' "${C_YELLOW}" "$(date +%H:%M:%S)" "${C_RESET}" "$*" >&2
}

error() {
  printf '%s[%s] ERROR%s %s\n' "${C_RED}" "$(date +%H:%M:%S)" "${C_RESET}" "$*" >&2
}

die() {
  error "$*"
  exit 1
}

is_root() {
  [[ "${EUID}" -eq 0 ]]
}

run_as_root() {
  if is_root; then
    "$@"
  else
    sudo "$@"
  fi
}

require_cmd() {
  local cmd
  for cmd in "$@"; do
    command -v "${cmd}" >/dev/null 2>&1 || die "No se encontró el comando requerido: ${cmd}"
  done
}

require_ubuntu() {
  [[ -f /etc/os-release ]] || die "No se pudo detectar el sistema operativo (falta /etc/os-release)"
  # shellcheck disable=SC1091
  . /etc/os-release
  case "${ID:-}" in
    ubuntu|debian) ;;
    *) die "Sistema operativo no soportado: ${PRETTY_NAME:-desconocido}. Use Ubuntu 24.04 LTS o Debian 12." ;;
  esac
}

docker_cli() {
  if is_root || docker info >/dev/null 2>&1; then
    docker "$@"
  else
    sudo docker "$@"
  fi
}

compose() {
  docker_cli compose "$@"
}

ensure_docker() {
  if command -v docker >/dev/null 2>&1 && docker_cli compose version >/dev/null 2>&1; then
    if command -v systemctl >/dev/null 2>&1; then
      run_as_root systemctl enable --now docker >/dev/null 2>&1 || true
    fi
    info "Docker Engine y el plugin de Compose ya están instalados"
    return 0
  fi

  info "Instalando Docker Engine y el plugin de Compose"
  require_ubuntu
  require_cmd apt-get curl gpg

  run_as_root apt-get update -y
  run_as_root apt-get install -y ca-certificates curl gnupg
  run_as_root install -m 0755 -d /etc/apt/keyrings

  if [[ ! -f /etc/apt/keyrings/docker.gpg ]]; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
      | run_as_root gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    run_as_root chmod a+r /etc/apt/keyrings/docker.gpg
  fi

  local arch codename
  arch="$(dpkg --print-architecture)"
  codename="$(. /etc/os-release && echo "${VERSION_CODENAME:-}")"
  printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu %s stable\n' \
    "${arch}" "${codename}" \
    | run_as_root tee /etc/apt/sources.list.d/docker.list >/dev/null

  run_as_root apt-get update -y
  run_as_root apt-get install -y \
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  run_as_root systemctl enable --now docker

  local target_user="${SUDO_USER:-${USER:-}}"
  if [[ -n "${target_user}" && "${target_user}" != "root" ]]; then
    run_as_root usermod -aG docker "${target_user}" || true
    warn "Usuario ${target_user} agregado al grupo docker; puede requerirse reiniciar sesión (esta sesión usa sudo como respaldo)"
  fi

  command -v docker >/dev/null 2>&1 || die "La instalación de Docker falló"
  docker_cli compose version >/dev/null 2>&1 || die "La instalación del plugin de Docker Compose falló"
  info "Docker se instaló correctamente"
}

gen_password() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 24
  else
    od -An -tx1 -N24 /dev/urandom | tr -d ' \n'
  fi
}

replace_placeholders() {
  local file="$1" tmp
  tmp="$(mktemp)"
  while IFS= read -r line || [[ -n "${line}" ]]; do
    if [[ "${line}" == *"__GENERATE__"* ]]; then
      line="${line//__GENERATE__/$(gen_password)}"
    fi
    printf '%s\n' "${line}" >>"${tmp}"
  done <"${file}"
  mv "${tmp}" "${file}"
  chmod 600 "${file}"
}

ensure_env_file() {
  local example="$1"
  local target="$2"

  if [[ -f "${target}" ]]; then
    info "Usando archivo env existente: ${target}"
    return 0
  fi

  [[ -f "${example}" ]] || die "No se encontró la plantilla de entorno: ${example}"
  cp "${example}" "${target}"
  chmod 600 "${target}"
  replace_placeholders "${target}"
  info "Archivo env generado desde la plantilla: ${target}"
}

load_env() {
  local file="$1"
  [[ -f "${file}" ]] || die "No se encontró el archivo env: ${file}"
  set -a
  # shellcheck disable=SC1090
  source "${file}"
  set +a
}

acquire_lock() {
  local lock_file="${1:-/tmp/circleguard-bootstrap.lock}"
  exec 9>"${lock_file}"
  if ! flock -n 9; then
    die "Ya hay otro proceso de bootstrap en ejecución (lock: ${lock_file})"
  fi
}

retry() {
  local attempts="$1"
  local delay="$2"
  shift 2

  local attempt=1
  until "$@"; do
    if (( attempt >= attempts )); then
      return 1
    fi
    warn "Intento ${attempt}/${attempts} falló: $* — reintentando en ${delay}s"
    sleep "${delay}"
    attempt=$((attempt + 1))
  done
}

wait_http() {
  local url="$1"
  local attempts="${2:-60}"
  local delay="${3:-5}"

  info "Esperando a ${url}"
  retry "${attempts}" "${delay}" curl -fsS --max-time 10 -o /dev/null "${url}" \
    || die "El endpoint no quedó listo: ${url}"
  info "Endpoint listo: ${url}"
}

ensure_network() {
  local name="$1"
  if docker_cli network inspect "${name}" >/dev/null 2>&1; then
    info "La red ya existe: ${name}"
  else
    docker_cli network create "${name}" >/dev/null
    info "Red creada: ${name}"
  fi
}

ensure_volume() {
  local name="$1"
  if docker_cli volume inspect "${name}" >/dev/null 2>&1; then
    info "El volumen ya existe: ${name}"
  else
    docker_cli volume create "${name}" >/dev/null
    info "Volumen creado: ${name}"
  fi
}

container_running() {
  [[ "$(docker_cli inspect -f '{{.State.Running}}' "$1" 2>/dev/null || true)" == "true" ]]
}

validate_env_name() {
  case "${1:-}" in
    dev|stage|prod) ;;
    *) die "Uso: $(basename "$0") <dev|stage|prod>" ;;
  esac
}

env_file() {
  printf '%s/%s/.env\n' "${ENVS_DIR}" "$1"
}

env_template() {
  printf '%s/%s/.env.example\n' "${ENVS_DIR}" "$1"
}
