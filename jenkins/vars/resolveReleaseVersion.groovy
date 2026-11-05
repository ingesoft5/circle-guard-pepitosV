/**
 * Versión de release del commit actual, a partir del tag de Git vMAJOR.MINOR.PATCH
 * que apunta a HEAD. Devuelve la versión sin "v" (p. ej. '1.4.0'), o null si HEAD
 * no tiene un tag de release.
 *
 *   def version = resolveReleaseVersion()
 *
 * Hasta la HU-15 el tag se crea a mano (git tag v1.0.0 && git push origin v1.0.0);
 * después lo creará el paso de versionado automático antes de publicar.
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

    List<String> versions = []
    for (String tag : (tags ? tags.split('\\s+') : [])) {
        String candidate = tag.substring(1)
        if (candidate ==~ /\d+\.\d+\.\d+/) {
            versions.add(candidate)
        }
    }
    if (versions.isEmpty()) {
        return null
    }

    // Si un commit tiene varios tags de release, se usa el mayor.
    return versions.max { String v ->
        List<Integer> parts = v.tokenize('.').collect { it as int }
        parts[0] * 1000000 + parts[1] * 1000 + parts[2]
    }
}
