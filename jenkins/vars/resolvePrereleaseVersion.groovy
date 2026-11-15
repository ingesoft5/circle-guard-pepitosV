/**
 * Resuelve tags de imágenes inmutables para ramas de integración y promoción.
 *
 * dev:                         MAJOR.MINOR.PATCH-SNAPSHOT.BUILD.SHA
 * PR dev -> main (promoción):  X.Y.Z-rc.TREE   (TREE: 7 primeros caracteres del árbol de HEAD)
 * release/X.Y.Z (heredado):    X.Y.Z-rc.BUILD.SHA
 *
 * La versión base se calcula respecto al último tag estable integrado en origin/main.
 * Los SNAPSHOT llevan build y SHA para no sobrescribir tags en Nexus (writePolicy allow_once).
 * El RC de promoción se identifica por contenido: main lo localiza con el árbol de su HEAD y lo
 * promueve a estable con promoteRelease.
 *
 * Devuelve null cuando la promoción no incluye feat, fix, perf ni cambios incompatibles: no hay
 * release que publicar.
 */
def call(Map config = [:]) {
    String branch = (env.CHANGE_BRANCH ?: env.BRANCH_NAME ?: '').trim()
    String build = (env.BUILD_NUMBER ?: '0').replaceAll('[^0-9]', '')
    if (!build) build = '0'
    String sha = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()

    String credentialsId = config.credentialsId ?: 'github-pat'
    String mainBranch = config.mainBranch ?: 'main'

    // PR dev -> main: debe evaluarse antes que la rama dev.
    boolean promotion = env.CHANGE_ID && env.CHANGE_TARGET == mainBranch && branch == 'dev'
    if (promotion) {
        def expected = calculateVersion(
            credentialsId: credentialsId,
            initialVersion: config.initialVersion ?: '1.0.0',
            mainBranch: mainBranch
        )
        if (!expected) {
            echo 'resolvePrereleaseVersion: la promoción no incluye feat, fix, perf ni cambios incompatibles; no hay release candidate.'
            return null
        }
        // El árbol completo, no --short: así no cambia con el tamaño de abreviatura de cada clon.
        String tree = sh(script: 'git rev-parse HEAD^{tree}', returnStdout: true).trim()
        return "${expected.version}-rc.${tree.take(7)}"
    }

    if (branch == 'dev') {
        def calculated = calculateVersion(
            credentialsId: credentialsId,
            initialVersion: config.initialVersion ?: '1.0.0',
            mainBranch: mainBranch
        )

        // Si los commits desde el último estable no requieren release, conserva
        // la versión estable como base del SNAPSHOT.
        String base = calculated?.version
        if (!base) {
            base = resolveLatestStableVersion(
                credentialsId: credentialsId,
                mainBranch: mainBranch
            ) ?: (config.initialVersion ?: '1.0.0')
        }

        return "${base}-SNAPSHOT.${build}.${sha}"
    }

    if (branch ==~ /release\/.+/) {
        String requested = branch.substring('release/'.length())
        if (!semver.isVersion(requested)) {
            error("resolvePrereleaseVersion: la rama '${branch}' debe tener el formato release/X.Y.Z.")
        }

        def expected = calculateVersion(
            credentialsId: credentialsId,
            initialVersion: config.initialVersion ?: '1.0.0',
            mainBranch: mainBranch
        )
        if (!expected) {
            error('resolvePrereleaseVersion: no hay commits feat, fix, perf o breaking changes que justifiquen un nuevo release.')
        }
        if (expected.version != requested) {
            error("resolvePrereleaseVersion: la rama debe llamarse release/${expected.version}; '${requested}' no coincide con el incremento SemVer calculado.")
        }
        return "${requested}-rc.${build}.${sha}"
    }

    error("resolvePrereleaseVersion: rama no soportada '${branch}'. Use dev, un PR dev -> main o release/X.Y.Z.")
}