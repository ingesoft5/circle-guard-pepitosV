#!/usr/bin/env bash
# Creates or updates the circleguard-secrets Secret of one environment, idempotently:
#   deploy/k8s/scripts/apply-secrets.sh <dev|stage|prod> [env-file]
# The env file (default ~/.circleguard/k8s-<env>.env, mode 600) is generated on the first run
# from deploy/compose/<env>/.env.example with random values, like the Compose bootstrap, and
# reused afterwards so the database passwords never change under a running volume. No value
# reaches argv, the chart or the repository. Uses the current kubectl context (KUBECONFIG).
set -Eeuo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
readonly KEYS=(POSTGRES_USER POSTGRES_PASSWORD NEO4J_USER NEO4J_PASSWORD REDIS_PASSWORD
  LDAP_ADMIN_PASSWORD JWT_SECRET QR_SECRET VAULT_SECRET VAULT_SALT VAULT_HASH_SALT)
readonly SECRET_NAME="${SECRET_NAME:-circleguard-secrets}"

TMP_FILE=""
trap 'rm -f "${TMP_FILE}"' EXIT

die() { echo "apply-secrets: $*" >&2; exit 1; }

generate_env_file() {
  local example="$1" target="$2" line
  [[ -r "${example}" ]] || die "Template not found: ${example}"
  install -d -m 700 "$(dirname "${target}")"
  (
    umask 077
    while IFS= read -r line || [[ -n "${line}" ]]; do
      while [[ "${line}" == *"__GENERATE__"* ]]; do
        line="${line/__GENERATE__/$(openssl rand -hex 24)}"
      done
      printf '%s\n' "${line}"
    done < "${example}" > "${target}"
  )
  echo "apply-secrets: generated ${target}"
}

main() {
  local env_name="${1:-}" env_file namespace key value
  [[ "${env_name}" =~ ^(dev|stage|prod)$ ]] || die "Usage: apply-secrets.sh <dev|stage|prod> [env-file]"
  env_file="${2:-${HOME}/.circleguard/k8s-${env_name}.env}"
  namespace="circleguard-${env_name}"
  command -v kubectl >/dev/null || die "kubectl is required"
  command -v openssl >/dev/null || die "openssl is required"

  [[ -f "${env_file}" ]] || generate_env_file "${REPO_ROOT}/deploy/compose/${env_name}/.env.example" "${env_file}"

  TMP_FILE="$(mktemp)"
  for key in "${KEYS[@]}"; do
    value="$(grep -E "^${key}=" "${env_file}" | tail -n 1 | cut -d= -f2-)"
    [[ -n "${value}" && "${value}" != "__GENERATE__" ]] || die "${key} is missing or empty in ${env_file}"
    printf '%s=%s\n' "${key}" "${value}" >> "${TMP_FILE}"
  done

  kubectl create namespace "${namespace}" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  kubectl -n "${namespace}" create secret generic "${SECRET_NAME}" --from-env-file="${TMP_FILE}" \
    --dry-run=client -o yaml | kubectl apply -f -
}

main "$@"
