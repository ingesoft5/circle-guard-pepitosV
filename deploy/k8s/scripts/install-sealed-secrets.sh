#!/usr/bin/env bash
# Installs the Sealed Secrets controller in kube-system. Idempotent.
#   deploy/k8s/scripts/install-sealed-secrets.sh
# apply-secrets.sh calls this when the controller is missing. The chart still
# reads the Secret circleguard-secrets; only the controller may create it.
set -Eeuo pipefail

VERSION="${SEALED_SECRETS_VERSION:-0.41.2}"

die() { echo "install-sealed-secrets: $*" >&2; exit 1; }

command -v kubectl >/dev/null || die "kubectl is required"

url="https://github.com/bitnami/sealed-secrets/releases/download/v${VERSION}/controller.yaml"
kubectl apply -f "${url}"
kubectl -n kube-system rollout status deployment/sealed-secrets-controller --timeout=180s
