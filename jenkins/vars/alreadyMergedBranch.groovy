/**
 * True when a plain branch build has nothing new to check: its commit is already part of the
 * target branch (dev by default). That happens when a pull request is merged or closed and its
 * branch is still there: Jenkins stops building PR-<n> and builds the branch once more.
 *
 * Never true for pull requests, dev, main or release/* branches. A branch whose head is exactly
 * the target tip (just created, no commits yet) is still built: skipping it would publish a
 * second status for the same commit the target build reports on.
 */
boolean call(Map config = [:]) {
    String branch = env.BRANCH_NAME ?: ''
    if (env.CHANGE_ID || !branch || branch in ['dev', 'main'] || branch ==~ /release\/.+/) {
        return false
    }
    String target = config.target ?: 'dev'
    String credentialsId = config.credentialsId ?: 'github-pat'

    int status = 1
    withEnv(["MERGE_TARGET=${target}"]) {
        withCredentials([gitUsernamePassword(credentialsId: credentialsId)]) {
            sh 'git fetch --quiet --no-tags origin "+refs/heads/$MERGE_TARGET:refs/remotes/origin/$MERGE_TARGET"'
        }
        status = sh(script: '''
            target_tip="$(git rev-parse "refs/remotes/origin/$MERGE_TARGET")"
            [ "$(git rev-parse HEAD)" != "$target_tip" ] \
              && git merge-base --is-ancestor HEAD "$target_tip"
        ''', returnStatus: true)
    }
    if (status == 0) {
        echo "alreadyMergedBranch: ${branch} is already in ${target}; nothing to build"
    }
    return status == 0
}
