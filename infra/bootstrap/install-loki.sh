#!/usr/bin/env bash
# Installs single-binary Loki, Promtail and the Grafana datasources (Loki plus the
# Jaeger link from a log line). Idempotent. The observability module uploads this
# script; it can also be run by hand:
#   KUBECONFIG=/path/kubeconfig bash infra/bootstrap/install-loki.sh \
#     monitoring dev 6.55.0 6.17.1 \
#     observability/loki/values.yaml observability/promtail/values.yaml \
#     observability/grafana/datasources.yaml
set -Eeuo pipefail

NAMESPACE="${1:?Usage: install-loki.sh <namespace> <environment> <loki-chart> <promtail-chart> <loki-values> <promtail-values> <datasources>}"
ENVIRONMENT="${2:?Missing environment}"
LOKI_CHART="${3:?Missing Loki chart version}"
PROMTAIL_CHART="${4:?Missing Promtail chart version}"
LOKI_VALUES="${5:?Missing Loki values}"
PROMTAIL_VALUES="${6:?Missing Promtail values}"
DATASOURCES="${7:?Missing datasource manifest}"

export KUBECONFIG="${KUBECONFIG:-/etc/rancher/k3s/k3s.yaml}"

if command -v kubectl >/dev/null 2>&1; then
  KUBECTL=(kubectl)
else
  KUBECTL=(k3s kubectl)
fi

if ! command -v helm >/dev/null 2>&1; then
  curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
fi

for _ in $(seq 1 60); do
  "${KUBECTL[@]}" get nodes >/dev/null 2>&1 && break
  sleep 5
done
"${KUBECTL[@]}" get nodes >/dev/null

helm repo add grafana https://grafana.github.io/helm-charts --force-update >/dev/null
helm repo update >/dev/null

helm upgrade --install loki grafana/loki \
  --namespace "${NAMESPACE}" \
  --create-namespace \
  --version "${LOKI_CHART}" \
  --values "${LOKI_VALUES}" \
  --wait \
  --timeout 15m

helm upgrade --install promtail grafana/promtail \
  --namespace "${NAMESPACE}" \
  --version "${PROMTAIL_CHART}" \
  --values "${PROMTAIL_VALUES}" \
  --wait \
  --timeout 10m

rendered="$(mktemp)"
sed -e "s/__NAMESPACE__/${NAMESPACE}/g" -e "s/__ENVIRONMENT__/${ENVIRONMENT}/g" "${DATASOURCES}" > "${rendered}"
"${KUBECTL[@]}" apply -f "${rendered}"
rm -f "${rendered}"
