#!/usr/bin/env bash
# Installs linkerd-viz (the Linkerd dashboard) and publishes it through Traefik. Idempotent.
#   KUBECONFIG=<kubeconfig> deploy/k8s/scripts/install-linkerd-viz.sh [host]
# Default host: linkerd.<node-external-ip>.sslip.io. Needs install-linkerd.sh, the monitoring
# stack and the BasicAuth Secret of infra/bootstrap/load-monitoring-auth.sh.
set -Eeuo pipefail

LINKERD_CHART_VERSION="${LINKERD_CHART_VERSION:-2025.11.3}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
MESH="${ROOT}/deploy/k8s/mesh"

die() { echo "install-linkerd-viz: $*" >&2; exit 1; }
command -v kubectl >/dev/null || die "kubectl is required"
command -v helm >/dev/null || die "helm is required"
kubectl -n linkerd get deployment linkerd-destination >/dev/null 2>&1 ||
  die "Linkerd is not installed; run deploy/k8s/scripts/install-linkerd.sh first"
kubectl -n monitoring get secret monitoring-basic-auth >/dev/null 2>&1 ||
  die "no monitoring/monitoring-basic-auth; run infra/bootstrap/load-monitoring-auth.sh first"

host="${1:-}"
if [[ -z "${host}" ]]; then
  ip="$("${ROOT}/deploy/k8s/scripts/node-external-ip.sh" | tr -d '[:space:]')"
  [[ -n "${ip}" ]] || die "could not read the node external IP; pass the host as an argument"
  host="linkerd.${ip}.sslip.io"
fi
[[ "${host}" =~ ^linkerd\.[0-9.]+\.sslip\.io$ ]] ||
  echo "install-linkerd-viz: ${host} does not match dashboard.enforcedHostRegexp; update linkerd-viz-values.yaml" >&2

helm repo add linkerd-edge https://helm.linkerd.io/edge --force-update >/dev/null
helm repo update linkerd-edge >/dev/null
helm upgrade --install linkerd-viz linkerd-edge/linkerd-viz \
  --namespace linkerd-viz --create-namespace --version "${LINKERD_CHART_VERSION}" \
  -f "${MESH}/linkerd-viz-values.yaml" \
  --wait --timeout 10m

# Same users as /prometheus and /alertmanager. The htpasswd stays inside the cluster.
tmp="$(mktemp)"
trap 'rm -f "${tmp}"' EXIT
kubectl -n monitoring get secret monitoring-basic-auth -o jsonpath='{.data.users}' | base64 -d >"${tmp}"
kubectl -n linkerd-viz create secret generic viz-basic-auth --from-file=users="${tmp}" \
  --dry-run=client -o yaml | kubectl apply -f -

sed "s/__HOST__/${host}/g" "${MESH}/linkerd-viz-ingress.yaml" | kubectl apply -f -
kubectl -n linkerd-viz wait certificate/linkerd-viz-tls --for=condition=Ready --timeout=120s
kubectl -n linkerd-viz get pods -o wide
echo "Linkerd dashboard: https://${host}/ (same user as /prometheus)"
