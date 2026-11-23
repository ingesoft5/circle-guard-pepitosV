#!/usr/bin/env bash
# Runs one experiment of deploy/k8s/chaos/, watches the pods and removes the experiment.
#   chaos-run.sh <namespace> <experiment> [seconds-to-watch]
#   chaos-run.sh circleguard-dev gateway-redis-partition 300
set -Eeuo pipefail
NS="${1:?Usage: chaos-run.sh <namespace> <experiment> [seconds]}"
EXP="${2:?missing experiment, one of: $(ls "$(dirname "$0")/../chaos" | grep -v values | sed 's/.yaml//' | xargs)}"
WATCH="${3:-180}"
FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../chaos" && pwd)/${EXP}.yaml"
[[ -f "${FILE}" ]] || { echo "chaos-run: no ${FILE}" >&2; exit 1; }
[[ "${NS}" == *-prod ]] && { echo "chaos-run: refusing ${NS}" >&2; exit 1; }

kubectl -n chaos-mesh rollout status deployment/chaos-controller-manager --timeout=60s >/dev/null \
  || { echo "chaos-run: chaos-controller-manager is not Ready" >&2; exit 1; }
trap 'kubectl -n "${NS}" delete -f "${FILE}" --ignore-not-found >/dev/null; echo "chaos-run: ${EXP} removed"' EXIT
echo "chaos-run: ${EXP} in ${NS} at $(date -u +%FT%TZ)"
kubectl -n "${NS}" apply -f "${FILE}"
end=$(( $(date +%s) + WATCH ))
while [[ $(date +%s) -lt ${end} ]]; do
  echo "--- $(date +%T)"
  kubectl -n "${NS}" get pods --no-headers \
    -o custom-columns=POD:.metadata.name,READY:.status.containerStatuses[*].ready,RESTARTS:.status.containerStatuses[*].restartCount,PHASE:.status.phase \
    | grep -Ev 'kafka|zookeeper|jaeger|openldap|neo4j' || true
  sleep 15
done
kubectl -n "${NS}" get events --sort-by=.lastTimestamp | tail -n 15
