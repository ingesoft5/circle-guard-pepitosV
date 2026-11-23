#!/usr/bin/env bash
# Installs the service mesh in the cluster of $KUBECONFIG (docs/arquitectura/service-mesh.md).
# Idempotent: run it again to upgrade or to change the values in deploy/k8s/mesh/.
#   install-mesh.sh                      Linkerd only (mTLS), as in prod
#   install-mesh.sh --canary             plus Flagger and its load tester
#   install-mesh.sh --canary --viz HOST  plus the Linkerd dashboard at https://HOST
# Linkerd 2.20 and later edge releases need Kubernetes 1.31; the nodes run k3s 1.30, so this pins
# edge-25.11.3 (Linkerd 2.19 line), which accepts Gateway API 1.1.1 to 1.2.1.
set -Eeuo pipefail

LINKERD_CHART_VERSION="${LINKERD_CHART_VERSION:-2025.11.3}"
GATEWAY_API_VERSION="${GATEWAY_API_VERSION:-v1.2.1}"
FLAGGER_CHART_VERSION="${FLAGGER_CHART_VERSION:-1.45.0}"
LOADTESTER_CHART_VERSION="${LOADTESTER_CHART_VERSION:-0.39.0}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
MESH="${ROOT}/deploy/k8s/mesh"

die() { echo "install-mesh: $*" >&2; exit 1; }
canary=false
viz_host=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --canary) canary=true ;;
    --viz) viz_host="${2:?--viz needs a host, e.g. linkerd.<node-ip>.sslip.io}"; shift ;;
    *) die "unknown option $1" ;;
  esac
  shift
done
command -v kubectl >/dev/null || die "kubectl is required"
command -v helm >/dev/null || die "helm is required"
kubectl get nodes >/dev/null || die "cannot reach the cluster (KUBECONFIG=${KUBECONFIG:-unset})"
podmonitor=false
kubectl get crd podmonitors.monitoring.coreos.com >/dev/null 2>&1 && podmonitor=true

# 1. Gateway API CRDs: Flagger edits an HTTPRoute; Linkerd 2.19 no longer installs them.
current="$(kubectl get crd httproutes.gateway.networking.k8s.io \
  -o jsonpath='{.metadata.annotations.gateway\.networking\.k8s\.io/bundle-version}' 2>/dev/null || true)"
case "${current}" in
  "") kubectl apply --server-side -f \
        "https://github.com/kubernetes-sigs/gateway-api/releases/download/${GATEWAY_API_VERSION}/standard-install.yaml"
      kubectl wait --for=condition=Established crd/httproutes.gateway.networking.k8s.io --timeout=60s ;;
  v1.1.1|v1.2.0|v1.2.1) echo "Gateway API ${current} already installed" ;;
  *) die "Gateway API ${current} is installed; Linkerd 2.19 supports v1.1.1 to v1.2.1" ;;
esac

# 2. Identity from cert-manager (deploy/k8s/mesh/linkerd-identity.yaml). No key leaves the cluster.
bash "${ROOT}/deploy/k8s/scripts/install-cert-manager.sh"
kubectl create namespace linkerd --dry-run=client -o yaml | kubectl apply -f -
kubectl label namespace linkerd --overwrite \
  linkerd.io/is-control-plane=true config.linkerd.io/admission-webhooks=disabled
kubectl apply -f "${MESH}/linkerd-identity.yaml"
kubectl -n linkerd wait certificate/linkerd-trust-anchor certificate/linkerd-identity-issuer \
  --for=condition=Ready --timeout=120s
tmp="$(mktemp -d)"
trap 'rm -rf "${tmp}"' EXIT
kubectl -n linkerd get secret linkerd-trust-anchor -o jsonpath='{.data.tls\.crt}' | base64 -d >"${tmp}/ca.crt"
[[ -s "${tmp}/ca.crt" ]] || die "the Secret linkerd-trust-anchor has no certificate"

# 3. Linkerd control plane.
helm repo add linkerd-edge https://helm.linkerd.io/edge --force-update >/dev/null
helm repo add flagger https://flagger.app --force-update >/dev/null
helm repo update linkerd-edge flagger >/dev/null
helm upgrade --install linkerd-crds linkerd-edge/linkerd-crds -n linkerd \
  --version "${LINKERD_CHART_VERSION}" --set installGatewayAPI=false --wait
helm upgrade --install linkerd-control-plane linkerd-edge/linkerd-control-plane -n linkerd \
  --version "${LINKERD_CHART_VERSION}" -f "${MESH}/linkerd-values.yaml" \
  --set-file identityTrustAnchorsPEM="${tmp}/ca.crt" --set podMonitor.enabled="${podmonitor}" \
  --wait --timeout 10m

# 4. Flagger and its load tester (canary of gateway).
if [[ "${canary}" == true ]]; then
  helm upgrade --install flagger flagger/flagger -n flagger-system --create-namespace \
    --version "${FLAGGER_CHART_VERSION}" -f "${MESH}/flagger-values.yaml" \
    --set podMonitor.enabled="${podmonitor}" --wait --timeout 5m
  helm upgrade --install flagger-loadtester flagger/loadtester -n flagger-system \
    --version "${LOADTESTER_CHART_VERSION}" -f "${MESH}/loadtester-values.yaml" --wait --timeout 5m
fi

# 5. Linkerd dashboard behind the BasicAuth user of /prometheus (infra/bootstrap/load-monitoring-auth.sh).
if [[ -n "${viz_host}" ]]; then
  kubectl -n monitoring get secret monitoring-basic-auth >/dev/null 2>&1 ||
    die "no monitoring/monitoring-basic-auth; run infra/bootstrap/load-monitoring-auth.sh first"
  helm upgrade --install linkerd-viz linkerd-edge/linkerd-viz -n linkerd-viz --create-namespace \
    --version "${LINKERD_CHART_VERSION}" -f "${MESH}/linkerd-viz-values.yaml" --wait --timeout 10m
  kubectl -n monitoring get secret monitoring-basic-auth -o jsonpath='{.data.users}' | base64 -d >"${tmp}/users"
  kubectl -n linkerd-viz create secret generic viz-basic-auth --from-file=users="${tmp}/users" \
    --dry-run=client -o yaml | kubectl apply -f -
  sed "s/__HOST__/${viz_host}/g" "${MESH}/linkerd-viz-ingress.yaml" | kubectl apply -f -
  kubectl -n linkerd-viz wait certificate/linkerd-viz-tls --for=condition=Ready --timeout=120s
  echo "Linkerd dashboard: https://${viz_host}/ (same user as /prometheus)"
fi

kubectl get pods -A -o wide | grep -E '^(linkerd|linkerd-viz|flagger-system) ' || true
echo "Running pods join the mesh on their next restart: kubectl -n circleguard-<env> rollout restart deployment"
