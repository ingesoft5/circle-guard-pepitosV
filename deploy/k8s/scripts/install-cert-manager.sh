#!/usr/bin/env bash
# Installs cert-manager and the CircleGuard issuers. Idempotent.
#   deploy/k8s/scripts/install-cert-manager.sh
# cert-manager 1.18 is the newest release that still supports k3s 1.30.
set -Eeuo pipefail

VERSION="${CERT_MANAGER_VERSION:-1.18.6}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

die() { echo "install-cert-manager: $*" >&2; exit 1; }
command -v kubectl >/dev/null || die "kubectl is required"

url="https://github.com/cert-manager/cert-manager/releases/download/v${VERSION}/cert-manager.yaml"
kubectl apply -f "${url}"
kubectl -n cert-manager rollout status deployment/cert-manager --timeout=180s
kubectl -n cert-manager rollout status deployment/cert-manager-webhook --timeout=180s
kubectl -n cert-manager rollout status deployment/cert-manager-cainjector --timeout=180s

kubectl apply -f "${ROOT}/deploy/k8s/cert-manager/issuers.yaml"
kubectl -n cert-manager wait certificate/circleguard-ca --for=condition=Ready --timeout=180s
kubectl wait --for=condition=Ready clusterissuer/circleguard-ca --timeout=120s
