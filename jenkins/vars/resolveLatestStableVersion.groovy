/**
 * Resuelve la última versión estable publicada en la línea principal (main).
 *
 * Los tags estables se crean exclusivamente durante la promoción aprobada a main.
 * Por eso no se limita la búsqueda a los ancestros de HEAD (que puede ser dev o
 * release/*): se consideran los tags vMAJOR.MINOR.PATCH alcanzables desde origin/main.
 *
 * Devuelve la versión sin prefijo "v", o null si main todavía no tiene releases.
 */
def call(Map config = [:]) {
    String credentialsId = config.credentialsId ?: 'github-pat'
    String mainBranch = config.mainBranch ?: 'main'
    String remoteRef = "refs/remotes/origin/${mainBranch}"

    withCredentials([gitUsernamePassword(credentialsId: credentialsId)]) {
        sh 'git fetch --tags --force --quiet origin'
        sh "git fetch --quiet origin +refs/heads/${mainBranch}:${remoteRef}"
    }

    String tags = sh(
        script: "git tag --merged '${remoteRef}' --list 'v[0-9]*' | grep -E '^v[0-9]+\\.[0-9]+\\.[0-9]+\$' || true",
        returnStdout: true
    ).trim()

    String latest = semver.highest(semver.fromTags(tags))
    echo(latest
        ? "resolveLatestStableVersion: último release estable integrado en ${mainBranch}: v${latest}"
        : "resolveLatestStableVersion: no hay tags estables integrados en ${mainBranch}")

    return latest
}
