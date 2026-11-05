#!/usr/bin/env bash
# Provisions one CircleGuard environment host (dev, stage or prod) idempotently.
# Usage: sudo [DEPLOY_SSH_PUBLIC_KEY="ssh-ed25519 ..."] [ADMIN_CIDR=<cidr>] \
#   [NEXUS_REGISTRY=<host:port>] [NEXUS_PULL_USER=<user> NEXUS_PULL_PASSWORD=<password>] \
#   bootstrap-env.sh <env>
# NEXUS_PULL_USER/PASSWORD are the read-only registry credentials generated on the
# CI host (infra/compose/ci/.env); they let the deploy user pull images.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly DEPLOY_USER="${DEPLOY_USER:-deploy}"
readonly ENV_VOLUMES=(postgres neo4j)

ensure_deploy_user() {
  if id "${DEPLOY_USER}" >/dev/null 2>&1; then
    info "User already exists: ${DEPLOY_USER}"
  else
    run_as_root useradd --create-home --shell /bin/bash "${DEPLOY_USER}"
    info "User created: ${DEPLOY_USER}"
  fi
  run_as_root usermod -aG docker "${DEPLOY_USER}"
}

authorize_deploy_key() {
  local public_key="${DEPLOY_SSH_PUBLIC_KEY:-}"
  if [[ -z "${public_key}" ]]; then
    warn "DEPLOY_SSH_PUBLIC_KEY is not set: no SSH key was authorized for ${DEPLOY_USER}"
    return 0
  fi

  local home_dir ssh_dir keys_file
  home_dir="$(getent passwd "${DEPLOY_USER}" | cut -d: -f6)"
  ssh_dir="${home_dir}/.ssh"
  keys_file="${ssh_dir}/authorized_keys"

  run_as_root install -d -m 700 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${ssh_dir}"
  run_as_root touch "${keys_file}"
  if run_as_root grep -qxF -- "${public_key}" "${keys_file}"; then
    info "SSH key already authorized for ${DEPLOY_USER}"
  else
    printf '%s\n' "${public_key}" | run_as_root tee -a "${keys_file}" >/dev/null
    info "SSH key authorized for ${DEPLOY_USER}"
  fi
  run_as_root chown "${DEPLOY_USER}:${DEPLOY_USER}" "${keys_file}"
  run_as_root chmod 600 "${keys_file}"
}

# The Nexus registry on the CI host serves plain HTTP, so Docker must trust it explicitly.
# The registry is merged into the existing daemon.json instead of replacing the file,
# so other daemon settings (log driver, DNS, ...) are kept.
ensure_registry_trust() {
  local registry="${NEXUS_REGISTRY:-}" config=/etc/docker/daemon.json rc=0
  if [[ -z "${registry}" ]]; then
    warn "NEXUS_REGISTRY is not set: Docker was not configured to pull from the registry"
    return 0
  fi
  require_cmd python3

  # Exit code: 0 = already trusted, 10 = file updated
  run_as_root python3 - "${config}" "${registry}" <<'PY' || rc=$?
import json, os, sys

path, registry = sys.argv[1], sys.argv[2]
data = {}
if os.path.exists(path) and os.path.getsize(path) > 0:
    with open(path) as f:
        data = json.load(f)
registries = data.setdefault("insecure-registries", [])
if registry in registries:
    sys.exit(0)
registries.append(registry)
with open(path, "w") as f:
    json.dump(data, f, indent=2)
    f.write("\n")
sys.exit(10)
PY

  case "${rc}" in
    0) info "Docker already trusts the registry: ${registry}" ;;
    10)
      run_as_root systemctl restart docker
      info "Docker now trusts the registry: ${registry}"
      ;;
    *) die "Could not update ${config} (is it valid JSON?)" ;;
  esac
}

# Logs the deploy user in with the read-only registry account, so deployments
# (docker compose pull) can download images. Re-running refreshes the credentials.
ensure_registry_login() {
  local registry="${NEXUS_REGISTRY:-}" user="${NEXUS_PULL_USER:-}" password="${NEXUS_PULL_PASSWORD:-}"
  if [[ -z "${registry}" || -z "${user}" || -z "${password}" ]]; then
    warn "NEXUS_REGISTRY, NEXUS_PULL_USER or NEXUS_PULL_PASSWORD is not set: ${DEPLOY_USER} is not logged in to the registry"
    return 0
  fi

  local home_dir
  home_dir="$(getent passwd "${DEPLOY_USER}" | cut -d: -f6)"
  printf '%s' "${password}" | run_as_root runuser -u "${DEPLOY_USER}" -- \
    env HOME="${home_dir}" docker login "${registry}" -u "${user}" --password-stdin >/dev/null \
    || die "docker login to ${registry} failed for ${DEPLOY_USER} (check NEXUS_PULL_USER/NEXUS_PULL_PASSWORD and the Security Group)"
  info "${DEPLOY_USER} is logged in to ${registry} as ${user} (pull only)"
}

ensure_env_settings() {
  local env_name="$1" target template
  target="$(env_file "${env_name}")"
  template="$(env_template "${env_name}")"

  ensure_env_file "${template}" "${target}"
  if [[ -n "${ADMIN_CIDR:-}" ]]; then
    set_env_value "${target}" ADMIN_CIDR "${ADMIN_CIDR}"
  fi

  run_as_root chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "$(dirname "${target}")"
  run_as_root chmod 600 "${target}"
}

main() {
  validate_env_name "${1:-}"
  local env_name="$1"

  acquire_lock "/tmp/circleguard-env-${env_name}.lock"
  require_ubuntu
  ensure_docker
  ensure_registry_trust
  ensure_deploy_user
  ensure_registry_login
  authorize_deploy_key
  ensure_env_settings "${env_name}"
  load_env "$(env_file "${env_name}")"

  ensure_network "${ENV_NETWORK}"
  local volume
  for volume in "${ENV_VOLUMES[@]}"; do
    ensure_volume "${VOLUME_PREFIX}-${volume}"
  done

  info "Environment ${env_name} is ready (user: ${DEPLOY_USER}, network: ${ENV_NETWORK})"
}

main "$@"
