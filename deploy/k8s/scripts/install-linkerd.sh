#!/usr/bin/env bash
# Installs the Linkerd service mesh in the cluster of $KUBECONFIG. Idempotent.
#   KUBECONFIG=<kubeconfig> deploy/k8s/scripts/install-linkerd.sh
#
# Versions: Linkerd 2.20 and the edge releases after it need Kubernetes 1.31, and the nodes run
# k3s 1.30, so this pins edge-25.11.3 (Linkerd 2.19 line, Kubernetes 1.29 to 1.35). Linkerd 2.19
# accepts Gateway API 1.1.1 to 1.2.1 and no longer installs those CRDs itself.
# Only the application pods join the mesh (linkerd.io/inject in the chart, mesh.enabled).
set -Eeuo pipefail

LINKERD_CHART_VERSION="${LINKERD_CHART_VERSION:-2025.11.3}"
GATEWAY_API_VERSION="${GATEWAY_API_VERSION:-v1.2.1}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
MESH="${ROOT}/deploy/k8s/mesh"

die() { echo "install-linkerd: $*" >&2; exit 1; }
command -v kubectl >/dev/null || die "kubectl is required"
command -v helm >/dev/null || die "helm is required"

# 1. Gateway API CRDs (HTTPRoute is what Flagger edits for the canary).
current="$(kubectl get crd httproutes.gateway.networking.k8s.io \
  -o jsonpath='{.metadata.annotations.gateway\.networking\.k8s\.io/bundle-version}' 2>/dev/null || true)"
case "${current}" in
  "")
    kubectl apply --server-side -f \
      "https://github.com/kubernetes-sigs/gateway-api/releases/download/${GATEWAY_API_VERSION}/standard-install.yaml"
    kubectl wait --for=condition=Established crd/httproutes.gateway.networking.k8s.io --timeout=60s
    ;;
  v1.1.1|v1.2.0|v1.2.1)
    echo "Gateway API ${current} already installed"
    ;;
  *)
    die "Gateway API ${current} is installed; Linkerd 2.19 supports v1.1.1 to v1.2.1"
    ;;
esac

# 2. Identity: trust anchor and issuer from cert-manager (deploy/k8s/mesh/linkerd-identity.yaml).
"${ROOT}/deploy/k8s/scripts/install-cert-manager.sh"
kubectl create namespace linkerd --dry-run=client -o yaml | kubectl apply -f -
kubectl label namespace linkerd --overwrite \
  linkerd.io/is-control-plane=true config.linkerd.io/admission-webhooks=disabled
kubectl apply -f "${MESH}/linkerd-identity.yaml"
kubectl -n linkerd wait certificate/linkerd-trust-anchor --for=condition=Ready --timeout=120s
kubectl -n linkerd wait certificate/linkerd-identity-issuer --for=condition=Ready --timeout=120s

tmp="$(mktemp -d)"
trap 'rm -rf "${tmp}"' EXIT
kubectl -n linkerd get secret linkerd-trust-anchor -o jsonpath='{.data.tls\.crt}' | base64 -d >"${tmp}/ca.crt"
[[ -s "${tmp}/ca.crt" ]] || die "the Secret linkerd-trust-anchor has no certificate"

# 3. CRDs and control plane.
helm repo add linkerd-edge https://helm.linkerd.io/edge --force-update >/dev/null
helm repo update linkerd-edge >/dev/null

helm upgrade --install linkerd-crds linkerd-edge/linkerd-crds \
  --namespace linkerd --version "${LINKERD_CHART_VERSION}" \
  --set installGatewayAPI=false --wait

podmonitor=false
if kubectl get crd podmonitors.monitoring.coreos.com >/dev/null 2>&1; then
  podmonitor=true
fi
helm upgrade --install linkerd-control-plane linkerd-edge/linkerd-control-plane \
  --namespace linkerd --version "${LINKERD_CHART_VERSION}" \
  -f "${MESH}/linkerd-values.yaml" \
  --set-file identityTrustAnchorsPEM="${tmp}/ca.crt" \
  --set podMonitor.enabled="${podmonitor}" \
  --wait --timeout 10m

kubectl -n linkerd get pods -o wide
if command -v linkerd >/dev/null 2>&1; then
  linkerd check
else
  echo "linkerd CLI not found; skipping 'linkerd check'"
fi
echo "Pods already running join the mesh on their next restart:"
echo "  kubectl -n circleguard-<env> rollout restart deployment"
