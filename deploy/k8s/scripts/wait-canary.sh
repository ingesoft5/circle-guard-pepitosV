#!/usr/bin/env bash
# Waits for the Flagger analysis started by a deploy; fails if Flagger rolled it back.
#   wait-canary.sh <namespace> [canary] [timeout-seconds]
# Exits 0 when there is no Canary, when the deploy did not change the workload or on promotion.
set -Eeuo pipefail
NAMESPACE="${1:?Usage: wait-canary.sh <namespace> [canary] [timeout-seconds]}"
NAME="${2:-gateway}"
TIMEOUT="${3:-900}"
# Flagger checks a Canary once per analysis interval (1m), so a new revision can take that long.
DETECT="${CANARY_DETECT_SECONDS:-150}"

if ! kubectl -n "${NAMESPACE}" get canary "${NAME}" >/dev/null 2>&1; then
  echo "wait-canary: no Canary ${NAME} in ${NAMESPACE}"; exit 0
fi
start="$(date +%s)"; started=false; last=""
while :; do
  phase="$(kubectl -n "${NAMESPACE}" get canary "${NAME}" -o jsonpath='{.status.phase}' 2>/dev/null || true)"
  elapsed=$(( $(date +%s) - start ))
  [[ "${phase}" != "${last}" ]] && echo "wait-canary: ${NAME} ${phase:-<no status>} (${elapsed}s)" && last="${phase}"
  case "${phase}" in
    Initializing|Progressing|Promoting|Finalising|Waiting|WaitingPromotion) started=true ;;
    Initialized|Succeeded) [[ "${started}" == true ]] && exit 0 ;;
    Failed)
      if [[ "${started}" == true ]]; then
        kubectl -n "${NAMESPACE}" describe canary "${NAME}" | tail -n 25 || true
        echo "wait-canary: Flagger rolled ${NAME} back; the previous version keeps serving" >&2; exit 1
      fi ;;
  esac
  if [[ "${started}" == false && "${elapsed}" -ge "${DETECT}" ]]; then
    [[ "${phase}" == Failed ]] && { echo "wait-canary: the last analysis of ${NAME} failed and this deploy did not start a new one" >&2; exit 1; }
    echo "wait-canary: no new revision of ${NAME} to analyze"; exit 0
  fi
  [[ "${elapsed}" -ge "${TIMEOUT}" ]] && { echo "wait-canary: ${NAME} still ${phase} after ${TIMEOUT}s" >&2; exit 1; }
  sleep 10
done
