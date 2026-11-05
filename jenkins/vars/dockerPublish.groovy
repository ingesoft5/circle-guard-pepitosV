/**
 * Construye la imagen Docker de un componente y la publica en el registro (HU-07).
 *
 *   dockerPublish(service: 'circleguard-auth-service', version: '1.4.0')
 *   dockerPublish(image: 'web-client', dockerfile: 'mobile/Dockerfile', context: 'mobile', version: '1.4.0')
 *
 * Reglas:
 *  - La imagen se publica con un único tag: la versión MAJOR.MINOR.PATCH (sin "v").
 *  - "latest" o cualquier tag que no sea MAJOR.MINOR.PATCH hace fallar la etapa.
 *  - Una versión publicada es inmutable: si el tag ya existe con otro commit, falla.
 *    Si existe con el mismo commit (re-ejecución del build), se omite sin error.
 *
 * Devuelve la referencia publicada, por ejemplo localhost:5000/circleguard/auth-service:1.4.0
 */
def call(Map config = [:]) {
    String version = (config.version ?: '').toString().trim()
    validateTag(version)

    String service = config.service
    String image = config.image ?: (service ? service.replaceFirst(/^circleguard-/, '') : null)
    if (!image) {
        error('dockerPublish: indique "service" (p. ej. circleguard-auth-service) o "image"')
    }
    String dockerfile = config.dockerfile ?: (service ? "services/${service}/Dockerfile" : null)
    if (!dockerfile) {
        error('dockerPublish: indique "dockerfile" cuando no usa "service"')
    }
    String buildContext = config.context ?: '.'
    String registry = config.registry ?: env.NEXUS_REGISTRY
    if (!registry) {
        error('dockerPublish: falta el registro (parámetro "registry" o variable global NEXUS_REGISTRY en JCasC)')
    }
    String namespace = config.namespace ?: (env.IMAGE_NAMESPACE ?: 'circleguard')
    String credentialsId = config.credentialsId ?: 'nexus-ci'
    String revision = env.GIT_COMMIT ?: sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
    String ref = "${registry}/${namespace}/${image}:${version}"

    withEnv([
        "IMAGE_REF=${ref}",
        "REGISTRY=${registry}",
        "DOCKERFILE=${dockerfile}",
        "BUILD_CONTEXT=${buildContext}",
        "IMAGE_VERSION=${version}",
        "REVISION=${revision}",
        // Credenciales del login aisladas por build, no en el home de Jenkins
        "DOCKER_CONFIG=${env.WORKSPACE}/.docker-publish"
    ]) {
        try {
            withCredentials([usernamePassword(credentialsId: credentialsId,
                    usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
                sh 'printf "%s" "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin'
            }

            String published = publishedRevision()
            if (published == revision) {
                echo "dockerPublish: ${ref} ya está publicada desde ${revision}; se omite"
            } else if (published) {
                error("dockerPublish: ${ref} ya existe (commit ${published}). Las versiones publicadas son " +
                        'inmutables: genere una versión nueva en lugar de sobrescribirla.')
            } else {
                sh '''
                    docker build --pull \
                      -f "$DOCKERFILE" \
                      --build-arg VERSION="$IMAGE_VERSION" \
                      --label org.opencontainers.image.revision="$REVISION" \
                      -t "$IMAGE_REF" \
                      "$BUILD_CONTEXT"
                    docker push "$IMAGE_REF"
                '''
                echo "dockerPublish: publicada ${ref}"
            }
        } finally {
            sh 'docker logout "$REGISTRY" >/dev/null 2>&1 || true; rm -rf "$DOCKER_CONFIG"'
        }
    }
    return ref
}

/** Falla con un mensaje explicativo si el tag no es una versión MAJOR.MINOR.PATCH. */
def validateTag(String tag) {
    if (!tag) {
        error('dockerPublish: falta "version" (formato MAJOR.MINOR.PATCH, p. ej. 1.4.0)')
    }
    if (tag.equalsIgnoreCase('latest')) {
        error("dockerPublish: el tag 'latest' no está permitido. Cada imagen se publica solo con su " +
                'versión MAJOR.MINOR.PATCH para poder desplegar y revertir versiones exactas (HU-07).')
    }
    if (!(tag ==~ /\d+\.\d+\.\d+/)) {
        error("dockerPublish: '${tag}' no es una versión MAJOR.MINOR.PATCH (p. ej. 1.4.0, sin prefijo 'v').")
    }
}

/**
 * Commit con el que se publicó IMAGE_REF, o '' si el tag no existe en el registro.
 * Usa el daemon Docker (no la API HTTP) porque el registro solo es alcanzable
 * como localhost desde el host CI.
 */
def publishedRevision() {
    int status = sh(script: 'docker pull -q "$IMAGE_REF" >/dev/null 2>&1', returnStatus: true)
    if (status != 0) {
        return ''
    }
    String label = sh(
        script: 'docker image inspect -f \'{{ index .Config.Labels "org.opencontainers.image.revision" }}\' "$IMAGE_REF"',
        returnStdout: true
    ).trim()
    return label ?: 'desconocido'
}
