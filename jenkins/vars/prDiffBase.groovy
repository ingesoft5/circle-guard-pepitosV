/**
 * Commit to diff a pull request build against: the tip of its target branch.
 *
 * A PR-Merge build checks out the PR head and merges the target into it, so HEAD^1 is the PR
 * head and HEAD^2 the target. Diffing against HEAD^1 only sees what the target added, which is
 * nothing when the PR is up to date. When the merge is a no-op (no merge commit), the base is the
 * merge-base with the fetched target branch, and HEAD^1 is the last resort.
 */
String call() {
    return withEnv(["PR_TARGET=${env.CHANGE_TARGET ?: ''}"]) {
        return sh(script: '''
            if git rev-parse --verify --quiet 'HEAD^2' >/dev/null; then
              git rev-parse 'HEAD^2'
            elif [ -n "$PR_TARGET" ] && git rev-parse --verify --quiet "refs/remotes/origin/$PR_TARGET" >/dev/null; then
              git merge-base HEAD "refs/remotes/origin/$PR_TARGET"
            else
              git rev-parse --verify --quiet 'HEAD^1' || true
            fi
        ''', returnStdout: true).trim()
    }
}
