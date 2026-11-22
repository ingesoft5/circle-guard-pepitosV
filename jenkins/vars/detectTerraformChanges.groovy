/**
 * Returns true when the build changes non-Markdown files under infra/terraform.
 *
 * On a PR the base is the target branch (prDiffBase). On main it is HEAD^1, the previous
 * commit on the protected branch, so a merge commit only sees what its PR brought. If the
 * base cannot be resolved, it returns true: it is safer to plan than to miss an apply.
 */
def call(Map config = [:]) {
    String base = (config.base ?: '').toString().trim()

    if (!base && env.CHANGE_ID) {
        base = prDiffBase()
        if (!base && env.CHANGE_TARGET) {
            base = sh(script: "git merge-base HEAD 'origin/${env.CHANGE_TARGET}' || true", returnStdout: true).trim()
        }
    }
    if (!base && !env.CHANGE_ID) {
        base = sh(script: "git rev-parse --verify --quiet 'HEAD^1' || true", returnStdout: true).trim()
    }
    if (!base) {
        return true
    }

    boolean exists = sh(script: "git cat-file -e '${base}^{commit}'", returnStatus: true) == 0
    if (!exists) {
        return true
    }
    boolean isAncestor = sh(script: "git merge-base --is-ancestor '${base}' HEAD", returnStatus: true) == 0
    if (!isAncestor) {
        return true
    }

    String diff = sh(script: "git diff --name-only '${base}' HEAD -- infra/terraform", returnStdout: true).trim()
    if (!diff) {
        return false
    }
    return diff.split(/\r?\n/).collect { it.trim() }.any { it && !it.endsWith('.md') }
}
