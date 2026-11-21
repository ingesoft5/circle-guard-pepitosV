#!/usr/bin/env bash
# Installs kube-prometheus-stack (Prometheus, Grafana and Alertmanager) in an existing k3s
# cluster. The Terraform module observability uploads this script and runs it over SSH; it can
# also be run by hand or from Jenkins with any kubeconfig:
#   KUBECONFIG=/path/kubeconfig bash infra/bootstrap/install-observability.sh \
#     monitoring 65.5.1 observability/prometheus/values.yaml
set -Eeuo pipefail

NAMESPACE="${1:?Usage: install-observability.sh <namespace> <chart-version> <values-file> [grafana-password-base64]}"
CHART_VERSION="${2:?Missing chart version}"
VALUES_FILE="${3:?Missing values file}"
GRAFANA_PASSWORD_B64="${4:-}"

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

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update >/dev/null
helm repo update >/dev/null

args=(
  upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack
  --namespace "${NAMESPACE}"
  --create-namespace
  --version "${CHART_VERSION}"
  --values "${VALUES_FILE}"
  --wait
  --timeout 20m
)

if [[ -n "${GRAFANA_PASSWORD_B64}" ]]; then
  args+=(--set-string "grafana.adminPassword=$(printf '%s' "${GRAFANA_PASSWORD_B64}" | base64 -d)")
fi

helm "${args[@]}"
