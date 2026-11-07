/**
 * Versión de release del commit actual, a partir del tag de Git vMAJOR.MINOR.PATCH
 * que apunta a HEAD. Devuelve la versión sin "v" (p. ej. '1.4.0'), o null si HEAD
 * no tiene un tag de release.
 *
 *   def version = resolveReleaseVersion()
 *
 * calculateVersion la usa para que re-ejecutar el build de un commit ya etiquetado
 * devuelva la misma versión en lugar de calcular una nueva.
 */
def call(Map config = [:]) {
    String credentialsId = config.credentialsId ?: 'github-pat'

    // El checkout del job multibranch trae solo la rama: se traen los tags aparte.
    withCredentials([gitUsernamePassword(credentialsId: credentialsId)]) {
        sh 'git fetch --tags --force --quiet origin'
    }

    String tags = sh(
        script: "git tag --points-at HEAD --list 'v[0-9]*'",
        returnStdout: true
    ).trim()

    // Si un commit tiene varios tags de release, se usa el mayor.
    return semver.highest(semver.fromTags(tags))
}
