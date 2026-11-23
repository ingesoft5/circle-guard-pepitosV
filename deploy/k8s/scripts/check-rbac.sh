#!/usr/bin/env bash
# Proves the deployer and the viewer with kubectl auth can-i.
#   deploy/k8s/scripts/check-rbac.sh <dev|stage|prod>
# The accounts come from the chart (templates/rbac.yaml) in namespace circleguard-<env>.
set -Eeuo pipefail

env_name="${1:-}"
[[ "${env_name}" =~ ^(dev|stage|prod)$ ]] || { echo "Usage: check-rbac.sh <dev|stage|prod>" >&2; exit 1; }
command -v kubectl >/dev/null || { echo "check-rbac: kubectl is required" >&2; exit 1; }

ns="circleguard-${env_name}"
fail=0

check() {
  local sa="$1" verb="$2" resource="$3" expect="$4" got as
  as="system:serviceaccount:${ns}:${sa}"
  got="$(kubectl auth can-i "${verb}" "${resource}" -n "${ns}" --as "${as}" || true)"
  if [[ "${got}" == "${expect}" ]]; then
    echo "ok ${sa} ${verb} ${resource} -> ${got}"
  else
    echo "FAIL ${sa} ${verb} ${resource} -> ${got}, expected ${expect}" >&2
    fail=1
  fi
}

check circleguard-deployer create deployments yes
check circleguard-deployer create secrets yes
check circleguard-viewer get pods yes
check circleguard-viewer get deployments.apps yes
check circleguard-viewer create deployments no
check circleguard-viewer get secrets no

exit "${fail}"
