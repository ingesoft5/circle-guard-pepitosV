#!/usr/bin/env bash
# Runs the Playwright suite in a container against one environment and copies the
# HTML report, JUnit file and failure traces to tests/e2e-playwright/results/.
#
# Usage: E2E_BASE_URL=http://<host> E2E_USERNAME=<user> E2E_PASSWORD=<password> run-e2e.sh
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly IMAGE="circleguard-e2e:local"
readonly CONTAINER="circleguard-e2e-$$"
readonly RESULTS_DIR="${SCRIPT_DIR}/results"

die() {
  printf 'ERROR %s\n' "$*" >&2
  exit 1
}

: "${E2E_BASE_URL:?Set E2E_BASE_URL to the web client of the environment under test}"
: "${E2E_USERNAME:?Set E2E_USERNAME}"
: "${E2E_PASSWORD:?Set E2E_PASSWORD}"
[[ "${E2E_BASE_URL}" =~ ^https?://[^/]+ ]] || die "E2E_BASE_URL is not an http(s) URL: ${E2E_BASE_URL}"

cleanup() {
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker build --quiet -t "${IMAGE}" "${SCRIPT_DIR}" >/dev/null
rm -rf "${RESULTS_DIR}"
mkdir -p "${RESULTS_DIR}"

printf 'Running end-to-end tests against %s\n' "${E2E_BASE_URL}"
status=0
docker run --name "${CONTAINER}" --shm-size=1g \
  -e E2E_BASE_URL -e E2E_USERNAME -e E2E_PASSWORD \
  "${IMAGE}" || status=$?

docker cp "${CONTAINER}:/e2e/results/." "${RESULTS_DIR}/" \
  || printf 'WARN No results to copy from the test container\n' >&2

exit "${status}"
