#!/usr/bin/env bash
# Deploys one image version to an environment and checks that it is healthy.
# Runs on the environment host, as the deploy user:
#   deploy/compose/deploy.sh <dev|stage|prod> <MAJOR.MINOR.PATCH>
# If the version cannot be pulled, the running environment is left untouched.
set -Eeuo pipefail

readonly WAIT_TIMEOUT_SECONDS=600
readonly SMOKE_SERVICES=(auth identity promotion form gateway)

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

env_value() {
  grep -E "^$1=" .env | tail -n 1 | cut -d= -f2-
}

show_diagnostics() {
  docker compose ps -a || true
  docker compose logs --tail 50 || true
}

smoke_test() {
  local base_url="http://localhost:$(env_value HTTP_PORT)" service
  curl -fsS -o /dev/null "${base_url}/healthz" || die "Web client health check failed"
  for service in "${SMOKE_SERVICES[@]}"; do
    curl -fsS "${base_url}/svc/${service}/actuator/health" | grep -q '"status":"UP"' \
      || die "Service ${service} is not UP through the web client"
    printf 'Service %s is UP\n' "${service}"
  done
}

main() {
  local env_name="${1:-}" version="${2:-}"
  [[ "${env_name}" =~ ^(dev|stage|prod)$ ]] || die "Usage: deploy.sh <dev|stage|prod> <version>"
  [[ "${version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "Version must be MAJOR.MINOR.PATCH: '${version}'"

  cd "$(dirname "${BASH_SOURCE[0]}")/${env_name}"
  [[ -f .env ]] || die "Missing .env: run infra/bootstrap/bootstrap-env.sh ${env_name} first"

  printf 'Deploying version %s to %s (current: %s)\n' "${version}" "${env_name}" "$(env_value IMAGE_TAG)"
  # The exported value takes precedence over .env, so a failed pull changes nothing.
  IMAGE_TAG="${version}" docker compose pull --quiet || die "Could not pull version ${version}"

  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=${version}/" .env

  if ! docker compose up -d --wait --wait-timeout "${WAIT_TIMEOUT_SECONDS}"; then
    show_diagnostics
    die "Services did not become healthy within ${WAIT_TIMEOUT_SECONDS} seconds"
  fi
  if ! (smoke_test); then
    show_diagnostics
    die "Smoke test failed"
  fi

  docker compose ps
  docker image prune -f >/dev/null
  printf 'Version %s is healthy on %s\n' "${version}" "${env_name}"
}

main "$@"
