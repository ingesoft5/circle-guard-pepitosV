/**
 * Calcula la siguiente versión semántica a partir de los Conventional Commits (HU-15).
 *
 *   def release = calculateVersion()
 *   // [version: '1.3.0', previous: '1.2.3', bump: 'minor', alreadyTagged: false]
 *
 * Reglas:
 *  - Sin ningún tag vMAJOR.MINOR.PATCH en el historial: 1.0.0.
 *  - Commits desde el último tag: "!" o BREAKING CHANGE -> major, feat -> minor,
 *    fix o perf -> patch. Otros tipos (docs, chore, ci, build...) no generan release
 *    y el paso devuelve null.
 *  - Si HEAD ya tiene un tag de release (re-ejecución del build), devuelve esa
 *    versión con alreadyTagged: true en lugar de calcular otra.
 *
 * Solo calcula: el tag lo crea y publica pushReleaseTag, después de publicar las imágenes.
 */
def call(Map config = [:]) {
    String credentialsId = config.credentialsId ?: 'github-pat'
    String initialVersion = config.initialVersion ?: '1.0.0'

    String current = resolveReleaseVersion(credentialsId: credentialsId)
    if (current) {
        echo "calculateVersion: HEAD ya tiene el tag v${current}; se reutiliza"
        return [version: current, previous: current, bump: 'none', alreadyTagged: true]
    }

    String previous = semver.highest(semver.fromTags(sh(
        script: "git tag --merged HEAD --list 'v[0-9]*'",
        returnStdout: true
    ).trim()))

    if (!previous) {
        echo "calculateVersion: no hay tags de versión; versión inicial ${initialVersion}"
        return [version: initialVersion, previous: null, bump: 'initial', alreadyTagged: false]
    }

    // Mensajes completos (asunto y cuerpo) separados por el carácter RS (0x1e).
    String log = sh(
        script: "git log --no-merges --format='%B%x1e' 'v${previous}..HEAD'",
        returnStdout: true
    )
    List<String> messages = semver.splitMessages(log)

    String bump = semver.releaseType(messages)
    if (!bump) {
        echo "calculateVersion: ${messages.size()} commit(s) desde v${previous} sin feat, fix, perf " +
                'ni cambios incompatibles; no se genera release'
        return null
    }

    String next = semver.bump(previous, bump)
    echo "calculateVersion: v${previous} -> v${next} (${bump}, ${messages.size()} commit(s))"
    return [version: next, previous: previous, bump: bump, alreadyTagged: false]
}
