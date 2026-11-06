/**
 * Crea el tag anotado vMAJOR.MINOR.PATCH sobre HEAD y lo publica en GitHub (HU-15).
 *
 *   pushReleaseTag(version: '1.3.0')
 *
 * Es idempotente: si el tag ya existe en HEAD (re-ejecución) solo lo vuelve a publicar.
 * Falla si el tag existe sobre otro commit, para no mover una versión ya publicada.
 * Usa la credencial github-pat, que necesita permiso de escritura sobre el repositorio.
 */
def call(Map config = [:]) {
    String version = (config.version ?: '').toString().trim()
    if (!semver.isVersion(version)) {
        error("pushReleaseTag: '${version}' no es una versión MAJOR.MINOR.PATCH")
    }
    String credentialsId = config.credentialsId ?: 'github-pat'
    String tag = "v${version}"

    withEnv(["RELEASE_TAG=${tag}"]) {
        String head = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
        int exists = sh(script: 'git rev-parse -q --verify "refs/tags/$RELEASE_TAG" >/dev/null', returnStatus: true)

        if (exists == 0) {
            String tagged = sh(script: 'git rev-parse "$RELEASE_TAG^{commit}"', returnStdout: true).trim()
            if (tagged != head) {
                error("pushReleaseTag: ${tag} ya existe sobre el commit ${tagged}, no sobre ${head}. " +
                        'Una versión publicada no se mueve.')
            }
            echo "pushReleaseTag: ${tag} ya existe en HEAD"
        } else {
            sh '''
                git -c user.name="CircleGuard CI" -c user.email="ci@circleguard.local" \
                  tag -a "$RELEASE_TAG" -m "Release $RELEASE_TAG"
            '''
        }

        withCredentials([gitUsernamePassword(credentialsId: credentialsId)]) {
            sh 'git push origin "refs/tags/$RELEASE_TAG"'
        }
        echo "pushReleaseTag: ${tag} publicado en GitHub"
    }
    return tag
}
