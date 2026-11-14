/**
 * Resuelve tags de imágenes inmutables para ramas de integración y release.
 *
 * dev:           MAJOR.MINOR.PATCH-SNAPSHOT.BUILD.SHA
 * release/X.Y.Z: X.Y.Z-rc.BUILD.SHA
 *
 * La versión base se calcula respecto al último tag estable integrado en origin/main.
 * Los sufijos de build y SHA evitan sobrescribir tags en Nexus (writePolicy allow_once).
 */
def call(Map config = [:]) {
    String branch = (env.CHANGE_BRANCH ?: env.BRANCH_NAME ?: '').trim()
    String build = (env.BUILD_NUMBER ?: '0').replaceAll('[^0-9]', '')
    if (!build) build = '0'
    String sha = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()

    String credentialsId = config.credentialsId ?: 'github-pat'
    String mainBranch = config.mainBranch ?: 'main'

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

    error("resolvePrereleaseVersion: rama no soportada '${branch}'. Use dev o release/X.Y.Z.")
}
