#!/usr/bin/env bash
# Waits for the Flagger analysis started by a deploy and fails if Flagger rolled it back.
#   deploy/k8s/scripts/wait-canary.sh <namespace> [canary] [timeout-seconds]
# Exits 0 when there is no Canary, when the deploy did not change the workload (Flagger does
# not start an analysis) or when the new version was promoted.
set -Eeuo pipefail

NAMESPACE="${1:?Usage: wait-canary.sh <namespace> [canary] [timeout-seconds]}"
NAME="${2:-gateway}"
TIMEOUT="${3:-900}"
# Flagger looks at a Canary once per analysis interval (1m in the chart), so a new revision can
# take that long to show up as Progressing.
DETECT="${CANARY_DETECT_SECONDS:-150}"

if ! kubectl -n "${NAMESPACE}" get canary "${NAME}" >/dev/null 2>&1; then
  echo "wait-canary: no Canary ${NAME} in ${NAMESPACE}; nothing to wait for"
  exit 0
fi

phase() { kubectl -n "${NAMESPACE}" get canary "${NAME}" -o jsonpath='{.status.phase}' 2>/dev/null || true; }

start="$(date +%s)"
started=false
last=""
while :; do
  current="$(phase)"
  elapsed=$(( $(date +%s) - start ))
  if [[ "${current}" != "${last}" ]]; then
    echo "wait-canary: ${NAME} ${current:-<no status>} (${elapsed}s)"
    last="${current}"
  fi
  case "${current}" in
    Initializing|Progressing|Promoting|Finalising|Waiting|WaitingPromotion)
      started=true
      ;;
    Initialized)
      # First reconcile after enabling the canary: the primary copies the current version.
      if [[ "${started}" == true ]]; then
        exit 0
      fi
      ;;
    Succeeded)
      if [[ "${started}" == true ]]; then
        echo "wait-canary: ${NAME} promoted"
        exit 0
      fi
      ;;
    Failed)
      if [[ "${started}" == true ]]; then
        kubectl -n "${NAMESPACE}" describe canary "${NAME}" | tail -n 25 || true
        echo "wait-canary: Flagger rolled ${NAME} back; the previous version keeps serving" >&2
        exit 1
      fi
      ;;
  esac
  if [[ "${started}" == false && "${elapsed}" -ge "${DETECT}" ]]; then
    if [[ "${current}" == Failed ]]; then
      echo "wait-canary: the last analysis of ${NAME} failed and this deploy did not start a new one" >&2
      exit 1
    fi
    echo "wait-canary: no new revision of ${NAME} to analyze"
    exit 0
  fi
  if [[ "${elapsed}" -ge "${TIMEOUT}" ]]; then
    echo "wait-canary: ${NAME} still ${current} after ${TIMEOUT}s" >&2
    exit 1
  fi
  sleep 10
done
