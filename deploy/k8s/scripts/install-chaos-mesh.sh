#!/usr/bin/env bash
# Installs Chaos Mesh and enables experiments in the given namespaces. Idempotent.
#   install-chaos-mesh.sh circleguard-dev [circleguard-stage ...]
# Only for dev and stage: the chaos daemon runs privileged on the node.
set -Eeuo pipefail
CHAOS_MESH_VERSION="${CHAOS_MESH_VERSION:-2.7.3}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
[[ $# -gt 0 ]] || { echo "Usage: install-chaos-mesh.sh <namespace>..." >&2; exit 1; }
for ns in "$@"; do
  [[ "${ns}" == *-prod ]] && { echo "install-chaos-mesh: refusing ${ns}" >&2; exit 1; }
done
# The controller dies with "too many open files" under Ubuntu's default inotify limit (128 instances):
# Linkerd, Flagger and the app already use most of them. Raise it on the node and persist it.
if [[ $(sysctl -n fs.inotify.max_user_instances) -lt 1024 ]]; then
  printf 'fs.inotify.max_user_instances=1024\nfs.inotify.max_user_watches=524288\n' \
    > /etc/sysctl.d/90-inotify.conf
  sysctl -q -p /etc/sysctl.d/90-inotify.conf
fi
helm repo add chaos-mesh https://charts.chaos-mesh.org --force-update >/dev/null
helm repo update chaos-mesh >/dev/null
helm upgrade --install chaos-mesh chaos-mesh/chaos-mesh -n chaos-mesh --create-namespace \
  --version "${CHAOS_MESH_VERSION}" -f "${ROOT}/deploy/k8s/chaos/values.yaml" --wait --timeout 10m
for ns in "$@"; do
  kubectl annotate namespace "${ns}" chaos-mesh.org/inject=enabled --overwrite
done
# helm --wait does not catch a crashing controller; the webhook needs it Ready before any experiment
kubectl -n chaos-mesh rollout status deployment/chaos-controller-manager --timeout=5m
kubectl -n chaos-mesh rollout status daemonset/chaos-daemon --timeout=5m
kubectl -n chaos-mesh get pods -o wide
