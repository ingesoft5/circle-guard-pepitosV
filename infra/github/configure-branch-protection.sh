#!/usr/bin/env bash
# Adds the Jenkins check to the branch protection of dev and main, idempotently.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/../bootstrap/lib/common.sh"

readonly ENV_FILE="${REPO_ROOT}/infra/compose/ci/.env"
readonly DEFAULT_CONTEXT="continuous-integration/jenkins/pr-merge"

main() {
  require_cmd gh jq

  if [[ -f "${ENV_FILE}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${ENV_FILE}"
    set +a
  fi

  local owner="${GITHUB_OWNER:-ingesoft5}"
  local repo="${GITHUB_REPO:-circle-guard-pepitosV}"
  local context="${JENKINS_CHECK_CONTEXT:-${DEFAULT_CONTEXT}}"
  local branches=("$@")
  if (( ${#branches[@]} == 0 )); then
    branches=(dev main)
  fi

  info "Repositorio: ${owner}/${repo}"
  info "Check requerido: ${context}"

  local branch
  for branch in "${branches[@]}"; do
    apply_protection "${owner}" "${repo}" "${branch}" "${context}"
  done

  info "Protección de ramas actualizada"
}

apply_protection() {
  local owner="$1" repo="$2" branch="$3" context="$4"
  local existing payload

  if existing="$(gh api "repos/${owner}/${repo}/branches/${branch}/protection" 2>/dev/null)"; then
    info "Leyendo la protección actual de ${branch}"
  else
    warn "La rama ${branch} no tiene protección; se creará una configuración base"
    existing='{}'
  fi

  payload="$(jq -n --argjson existing "${existing}" --arg context "${context}" '
    ($existing.required_status_checks.contexts // []) as $contexts
    | {
        required_status_checks: {
          strict: ($existing.required_status_checks.strict // true),
          contexts: (($contexts + [$context]) | unique)
        },
        enforce_admins: {enabled: ($existing.enforce_admins.enabled // true)},
        required_pull_request_reviews: {
          dismiss_stale_reviews: ($existing.required_pull_request_reviews.dismiss_stale_reviews // false),
          require_code_owner_reviews: ($existing.required_pull_request_reviews.require_code_owner_reviews // false),
          required_approving_review_count: ($existing.required_pull_request_reviews.required_approving_review_count // 1)
        },
        restrictions: (
          if $existing.restrictions then
            {
              users: [($existing.restrictions.users // [])[] | .login],
              teams: [($existing.restrictions.teams // [])[] | .slug],
              apps: [($existing.restrictions.apps // [])[] | .slug]
            }
          else null end
        ),
        allow_force_pushes: {enabled: false},
        allow_deletions: {enabled: false},
        required_conversation_resolution: {enabled: ($existing.required_conversation_resolution.enabled // true)}
      }'
  )"

  printf '%s\n' "${payload}" \
    | gh api --method PUT "repos/${owner}/${repo}/branches/${branch}/protection" --input - >/dev/null
  info "Check aplicado en ${branch}"
}

main "$@"
