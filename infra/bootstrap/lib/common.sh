#!/usr/bin/env bash
# Shared helpers for CircleGuard infrastructure bootstrap scripts.
# This file is intended to be sourced, not executed directly:
#   source "$(dirname "$0")/lib/common.sh"

set -Eeuo pipefail

readonly INFRA_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly ENVS_DIR="${INFRA_ROOT}/envs"
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

log() {
  local level="$1"
  shift
  printf '%s[%s]%s %s\n' "${C_DIM}" "$(date +%H:%M:%S)" "${C_RESET}" "$*" >&2
  : "$level"
}

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
    command -v "${cmd}" >/dev/null 2>&1 || die "Required command not found: ${cmd}"
  done
}

require_ubuntu() {
  [[ -f /etc/os-release ]] || die "Cannot detect the operating system (/etc/os-release is missing)"
  # shellcheck disable=SC1091
  . /etc/os-release
  case "${ID:-}" in
    ubuntu|debian) ;;
    *) die "Unsupported OS: ${PRETTY_NAME:-unknown}. Use Ubuntu 24.04 LTS or Debian 12." ;;
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
    info "Docker Engine and Compose plugin are already installed"
    return 0
  fi

  info "Installing Docker Engine and Compose plugin"
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
    warn "User ${target_user} added to the docker group; re-login may be required (this session falls back to sudo)"
  fi

  command -v docker >/dev/null 2>&1 || die "Docker installation failed"
  docker_cli compose version >/dev/null 2>&1 || die "Docker Compose plugin installation failed"
  info "Docker installed successfully"
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
    info "Using existing env file: ${target}"
    return 0
  fi

  [[ -f "${example}" ]] || die "Env template not found: ${example}"
  cp "${example}" "${target}"
  chmod 600 "${target}"
  replace_placeholders "${target}"
  info "Generated env file from template: ${target}"
}

load_env() {
  local file="$1"
  [[ -f "${file}" ]] || die "Env file not found: ${file}"
  set -a
  # shellcheck disable=SC1090
  source "${file}"
  set +a
}

acquire_lock() {
  local lock_file="${1:-/tmp/circleguard-bootstrap.lock}"
  exec 9>"${lock_file}"
  if ! flock -n 9; then
    die "Another bootstrap process is already running (lock: ${lock_file})"
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
    warn "Attempt ${attempt}/${attempts} failed: $* — retrying in ${delay}s"
    sleep "${delay}"
    attempt=$((attempt + 1))
  done
}

wait_http() {
  local url="$1"
  local attempts="${2:-60}"
  local delay="${3:-5}"

  info "Waiting for ${url}"
  retry "${attempts}" "${delay}" curl -fsS --max-time 10 -o /dev/null "${url}" \
    || die "Endpoint did not become ready: ${url}"
  info "Endpoint ready: ${url}"
}

ensure_network() {
  local name="$1"
  if docker_cli network inspect "${name}" >/dev/null 2>&1; then
    info "Network already exists: ${name}"
  else
    docker_cli network create "${name}" >/dev/null
    info "Created network: ${name}"
  fi
}

ensure_volume() {
  local name="$1"
  if docker_cli volume inspect "${name}" >/dev/null 2>&1; then
    info "Volume already exists: ${name}"
  else
    docker_cli volume create "${name}" >/dev/null
    info "Created volume: ${name}"
  fi
}

container_running() {
  [[ "$(docker_cli inspect -f '{{.State.Running}}' "$1" 2>/dev/null || true)" == "true" ]]
}

validate_env_name() {
  case "${1:-}" in
    dev|stage|prod) ;;
    *) die "Usage: $(basename "$0") <dev|stage|prod>" ;;
  esac
}
