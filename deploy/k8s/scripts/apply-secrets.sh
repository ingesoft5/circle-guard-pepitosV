#!/usr/bin/env bash
# Creates or updates the circleguard-secrets Secret of one environment, idempotently:
#   deploy/k8s/scripts/apply-secrets.sh <dev|stage|prod> [env-file]
# The env file (default ~/.circleguard/k8s-<env>.env, mode 600) is generated on the first run
# from deploy/compose/<env>/.env.example with random values, like the Compose bootstrap, and
# reused afterwards so the database passwords never change under a running volume. No value
# reaches argv, the chart or the repository. The cluster receives a SealedSecret; the
# controller in kube-system is what materializes circleguard-secrets. Uses the current
# kubectl context (KUBECONFIG).
set -Eeuo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
readonly KEYS=(POSTGRES_USER POSTGRES_PASSWORD NEO4J_USER NEO4J_PASSWORD REDIS_PASSWORD
  LDAP_ADMIN_PASSWORD JWT_SECRET QR_SECRET VAULT_SECRET VAULT_SALT VAULT_HASH_SALT)
readonly SECRET_NAME="${SECRET_NAME:-circleguard-secrets}"

TMP_FILE=""
trap 'rm -f "${TMP_FILE}"' EXIT

die() { echo "apply-secrets: $*" >&2; exit 1; }

# 48 hex characters, like gen_password in infra/bootstrap/lib/common.sh.
gen_password() {
  if command -v openssl >/dev/null; then openssl rand -hex 24; else od -An -tx1 -N24 /dev/urandom | tr -d ' \n'; fi
}

# kubeseal talks to the API server with the same kubeconfig as kubectl.
ensure_kubeseal() {
  command -v kubeseal >/dev/null && return 0
  local version="${KUBESEAL_VERSION:-0.41.2}" dest archive
  dest="${HOME}/.local/bin/kubeseal"
  archive="$(mktemp)"
  install -d -m 700 "$(dirname "${dest}")"
  curl -fsSL -o "${archive}" \
    "https://github.com/bitnami/sealed-secrets/releases/download/v${version}/kubeseal-${version}-linux-amd64.tar.gz"
  tar -xzf "${archive}" -C "$(dirname "${dest}")" kubeseal
  rm -f "${archive}"
  chmod 700 "${dest}"
  export PATH="$(dirname "${dest}"):${PATH}"
}

# The deployment is created by install-sealed-secrets.sh, but an existing
# deployment can still be rolling out or have no endpoints. kubeseal talks to
# its Service, so wait for both before asking for the certificate.
wait_for_sealed_secrets() {
  local timeout="${SEALED_SECRETS_TIMEOUT:-180}" deadline=$((SECONDS + timeout)) endpoints
  echo "apply-secrets: waiting for sealed-secrets-controller to be ready"
  while (( SECONDS < deadline )); do
    if kubectl --request-timeout=10s -n kube-system rollout status \
        deployment/sealed-secrets-controller --timeout=10s >/dev/null 2>&1; then
      endpoints="$(kubectl --request-timeout=10s -n kube-system get endpoints \
        sealed-secrets-controller -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null || true)"
      [[ -n "${endpoints}" ]] && return 0
    fi
    sleep 2
  done
  kubectl -n kube-system get pods,deploy,endpoints -l name=sealed-secrets-controller >&2 || true
  die "sealed-secrets-controller has no ready endpoints after ${timeout}s; kubeseal cannot fetch its certificate"
}

generate_env_file() {
  local example="$1" target="$2" line
  [[ -r "${example}" ]] || die "Template not found: ${example}"
  install -d -m 700 "$(dirname "${target}")"
  (
    umask 077
    while IFS= read -r line || [[ -n "${line}" ]]; do
      while [[ "${line}" == *"__GENERATE__"* ]]; do
        line="${line/__GENERATE__/$(gen_password)}"
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

  [[ -f "${env_file}" ]] || generate_env_file "${REPO_ROOT}/deploy/compose/${env_name}/.env.example" "${env_file}"

  TMP_FILE="$(mktemp)"
  for key in "${KEYS[@]}"; do
    value="$(grep -E "^${key}=" "${env_file}" | tail -n 1 | cut -d= -f2-)"
    [[ -n "${value}" && "${value}" != "__GENERATE__" ]] || die "${key} is missing or empty in ${env_file}"
    printf '%s=%s\n' "${key}" "${value}" >> "${TMP_FILE}"
  done

  kubectl create namespace "${namespace}" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  if ! kubectl -n kube-system get deployment sealed-secrets-controller >/dev/null 2>&1; then
    bash "${REPO_ROOT}/deploy/k8s/scripts/install-sealed-secrets.sh"
  fi
  ensure_kubeseal
  wait_for_sealed_secrets

  local plain sealed attempt
  plain="$(mktemp)"
  sealed="$(mktemp)"
  # shellcheck disable=SC2064
  trap "rm -f '${TMP_FILE}' '${plain}' '${sealed}'" EXIT
  kubectl -n "${namespace}" create secret generic "${SECRET_NAME}" --from-env-file="${TMP_FILE}" \
    --dry-run=client -o yaml > "${plain}"
  for attempt in $(seq 1 10); do
    if kubeseal --controller-name sealed-secrets-controller --controller-namespace kube-system \
        --namespace "${namespace}" --scope namespace-wide --format yaml \
        < "${plain}" > "${sealed}"; then
      break
    fi
    [[ "${attempt}" -lt 10 ]] || die "kubeseal could not fetch the sealed-secrets certificate"
    sleep 2
  done
  # A Secret created earlier by kubectl is not owned by the controller. Drop it so the
  # SealedSecret can recreate the same keys. The env file is unchanged, so the values are too.
  if kubectl -n "${namespace}" get secret "${SECRET_NAME}" >/dev/null 2>&1 \
      && ! kubectl -n "${namespace}" get secret "${SECRET_NAME}" -o jsonpath='{.metadata.ownerReferences[0].kind}' \
        | grep -q SealedSecret; then
    kubectl -n "${namespace}" delete secret "${SECRET_NAME}"
  fi
  kubectl apply -f "${sealed}"
  local _attempt
  for _attempt in $(seq 1 30); do
    kubectl -n "${namespace}" get secret "${SECRET_NAME}" >/dev/null 2>&1 && return 0
    sleep 2
  done
  die "SealedSecret applied but ${SECRET_NAME} was not created in ${namespace}"
}

main "$@"
