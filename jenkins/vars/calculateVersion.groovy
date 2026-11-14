/**
 * Calcula la siguiente versión semántica a partir de los Conventional Commits (HU-15).
 *
 * La referencia es el último tag estable alcanzable desde origin/main, porque los
 * releases estables se crean exclusivamente durante la promoción aprobada a main.
 * Esto permite que dev y release/* calculen versiones aunque el tag no sea ancestro
 * directo de su HEAD.
 *
 * Reglas:
 *  - Sin releases estables en main: utiliza initialVersion (por defecto 1.0.0).
 *  - Commits desde el último release: "!" o BREAKING CHANGE -> major,
 *    feat -> minor, fix o perf -> patch. Otros tipos no generan release.
 *  - Si HEAD ya tiene un tag de release, devuelve esa versión (idempotencia).
 *
 * Solo calcula: el tag lo crea y publica pushReleaseTag después de publicar imágenes.
 */
def call(Map config = [:]) {
    String credentialsId = config.credentialsId ?: 'github-pat'
    String initialVersion = config.initialVersion ?: '1.0.0'

    String current = resolveReleaseVersion(credentialsId: credentialsId)
    if (current) {
        echo "calculateVersion: HEAD ya tiene el tag v${current}; se reutiliza"
        return [version: current, previous: current, bump: 'none', alreadyTagged: true]
    }

    String previous = resolveLatestStableVersion(
        credentialsId: credentialsId,
        mainBranch: config.mainBranch ?: 'main'
    )

    if (!previous) {
        echo "calculateVersion: main no tiene tags de versión; versión inicial ${initialVersion}"
        return [version: initialVersion, previous: null, bump: 'initial', alreadyTagged: false]
    }

    // El rango funciona aunque el tag estable y HEAD estén en ramas divergentes:
    // incluye commits alcanzables desde HEAD que no están contenidos en el tag.
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
