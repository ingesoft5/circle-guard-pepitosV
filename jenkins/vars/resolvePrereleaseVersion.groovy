/**
 * Resuelve tags de imágenes inmutables para ramas de integración y release.
 *
 * dev:           MAJOR.MINOR.PATCH-SNAPSHOT.BUILD.SHA
 * release/X.Y.Z (including CHANGE_BRANCH on a promotion PR): X.Y.Z-rc.BUILD.SHA
 *
 * Los sufijos de build y SHA evitan sobrescribir tags en Nexus (writePolicy allow_once).
 */
def call(Map config = [:]) {
    String branch = (env.CHANGE_BRANCH ?: env.BRANCH_NAME ?: '').trim()
    String build = (env.BUILD_NUMBER ?: '0').replaceAll('[^0-9]', '')
    if (!build) build = '0'
    String sha = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()

    if (branch == 'dev') {
        withCredentials([gitUsernamePassword(credentialsId: config.credentialsId ?: 'github-pat')]) {
            sh 'git fetch --tags --force --quiet origin'
        }
        String tags = sh(
            script: "git tag --merged HEAD --list 'v[0-9]*' | grep -E '^v[0-9]+\\.[0-9]+\\.[0-9]+\$' || true",
            returnStdout: true
        ).trim()
        String previous = semver.highest(semver.fromTags(tags))
        String base = previous ?: (config.initialVersion ?: '1.0.0')

        if (previous) {
            String log = sh(
                script: "git log --no-merges --format='%B%x1e' 'v${previous}..HEAD'",
                returnStdout: true
            )
            String bump = semver.releaseType(semver.splitMessages(log))
            if (bump) base = semver.bump(previous, bump)
        }
        return "${base}-SNAPSHOT.${build}.${sha}"
    }

    if (branch ==~ /release\/.+/) {
        withCredentials([gitUsernamePassword(credentialsId: config.credentialsId ?: 'github-pat')]) {
            sh 'git fetch --tags --force --quiet origin'
        }
        String requested = branch.substring('release/'.length())
        if (!semver.isVersion(requested)) {
            error("resolvePrereleaseVersion: la rama '${branch}' debe tener el formato release/X.Y.Z.")
        }
        String stableTags = sh(
            script: "git tag --merged HEAD --list 'v[0-9]*' | grep -E '^v[0-9]+\\.[0-9]+\\.[0-9]+\$' || true",
            returnStdout: true
        ).trim()
        String latestStable = semver.highest(semver.fromTags(stableTags))
        if (latestStable && semver.compare(requested, latestStable) <= 0) {
            error("resolvePrereleaseVersion: ${requested} debe ser mayor que el último release ${latestStable}.")
        }
        def expected = calculateVersion()
        if (!expected) {
            error('resolvePrereleaseVersion: no hay commits feat, fix, perf o breaking changes que justifiquen un nuevo release.')
        }
        if (expected.version != requested) {
            error("resolvePrereleaseVersion: la rama debe llamarse release/${expected.version}; '${requested}' no coincide con el incremento SemVer calculado.")
        }
        return "${requested}-rc.${build}.${sha}"
    }

    error("resolvePrereleaseVersion: rama no soportada '${branch}'. Use dev o release/X.Y.Z.")
}
