# Branching strategy

CircleGuard uses **GitHub Flow**: `main` is the only long-lived branch, and every change reaches it through a reviewed pull request.

## Why GitHub Flow

- **Fits the team and the timeline.** A small team with short delivery cycles benefits from one integration branch and short-lived feature branches.
- **Environments are not branches.** `dev`, `stage` and `prod` are deployment targets. The pipeline promotes the same versioned build from `main` through them, so no `develop` or `release/*` branches are needed.
- **Why not GitFlow.** Its `develop`, `release/*` and `hotfix/*` branches add merges and coordination that a single supported version does not need.
- **Why not trunk-based.** Committing directly to the trunk or using very short-lived branches relies on feature flags and a mature test suite, which the project does not have yet. It would also make peer review harder to enforce.

## Branches

| Branch | Purpose |
|:---|:---|
| `main` | Protected and always deployable. Semantic version tags are created from it. |
| `<type>/<CGR-id>-<short-description>` | Short-lived work branch, created from `main`. |

- `<type>` is a Conventional Commits type, such as `feat`, `fix` or `docs`.
- `<CGR-id>` is the Jira issue key.
- `<short-description>` is lowercase, in English and separated by hyphens.

Example: `docs/CGR-53-branching-strategy`. Branches created before this document keep their names.

## Pull request flow

1. Create a branch from an up-to-date `main`.
2. Commit using Conventional Commits.
3. Open a pull request to `main` and complete the template.
4. Get an approval from another team member. Authors cannot approve their own pull requests.
5. Wait for all required checks to pass.
6. Merge the pull request and delete the branch.

## `main` protection rules

| Rule | Value |
|:---|:---|
| Pull request required | Yes, direct pushes are rejected |
| Required approvals | 1 |
| Required status checks | `commitlint` |
| Applies to administrators | Yes |
| Force pushes and branch deletion | Blocked |

The Jenkins pipeline check becomes a required status check once the pipeline is running.

## Commit messages

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) and are written in English:

```text
<type>(<scope>)!: <description>
```

The first line has a maximum of 72 characters. Messages drive semantic versioning and release notes:

| Message | Version bump |
|:---|:---|
| `fix(gateway): correct signed QR expiration` | PATCH |
| `feat(promotion): add state cascade with 14-day TTL` | MINOR |
| `feat(auth)!: replace role schema` | MAJOR |

Messages are validated in two places:

- **Pull request:** the `commitlint` check validates every commit in the pull request. This check is required and cannot be bypassed.
- **Local hook (optional):** the `commit-msg` hook rejects an invalid message before the commit is created, which avoids having to rewrite it later.

### Enable the local hook

Requires Node.js 22.12 or later. Run once from the repository root in each clone:

```bash
npm install
```

This installs commitlint and sets `core.hooksPath` to `.githooks` for that clone.
