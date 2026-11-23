#!/usr/bin/env bash
# Installs Flagger and its load tester in the cluster of $KUBECONFIG. Idempotent.
#   KUBECONFIG=<kubeconfig> deploy/k8s/scripts/install-flagger.sh
# Needs install-linkerd.sh first (Gateway API CRDs and the mesh for the load tester).
# The chart creates the Canary of gateway once the Flagger CRDs exist (canary.enabled).
set -Eeuo pipefail

FLAGGER_CHART_VERSION="${FLAGGER_CHART_VERSION:-1.45.0}"
LOADTESTER_CHART_VERSION="${LOADTESTER_CHART_VERSION:-0.39.0}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
MESH="${ROOT}/deploy/k8s/mesh"

die() { echo "install-flagger: $*" >&2; exit 1; }
command -v kubectl >/dev/null || die "kubectl is required"
command -v helm >/dev/null || die "helm is required"
kubectl get crd httproutes.gateway.networking.k8s.io >/dev/null 2>&1 ||
  die "no Gateway API CRDs; run deploy/k8s/scripts/install-linkerd.sh first"
kubectl -n linkerd get deployment linkerd-destination >/dev/null 2>&1 ||
  die "Linkerd is not installed; run deploy/k8s/scripts/install-linkerd.sh first"

helm repo add flagger https://flagger.app --force-update >/dev/null
helm repo update flagger >/dev/null

podmonitor=false
if kubectl get crd podmonitors.monitoring.coreos.com >/dev/null 2>&1; then
  podmonitor=true
fi

helm upgrade --install flagger flagger/flagger \
  --namespace flagger-system --create-namespace --version "${FLAGGER_CHART_VERSION}" \
  -f "${MESH}/flagger-values.yaml" \
  --set podMonitor.enabled="${podmonitor}" \
  --wait --timeout 5m

helm upgrade --install flagger-loadtester flagger/loadtester \
  --namespace flagger-system --version "${LOADTESTER_CHART_VERSION}" \
  -f "${MESH}/loadtester-values.yaml" \
  --wait --timeout 5m

kubectl -n flagger-system get pods -o wide
