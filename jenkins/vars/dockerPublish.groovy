/**
 * Construye la imagen Docker de un componente y la publica en el registro (HU-07).
 *
 *   dockerPublish(service: 'circleguard-auth-service', version: '0.4.1-SNAPSHOT.12.a83f21c')
 *   dockerPublish(image: 'web-client', dockerfile: 'mobile/Dockerfile', context: 'mobile', version: '0.4.1-rc.9f3a1c2')
 *
 * Reglas:
 *  - Acepta versiones estables MAJOR.MINOR.PATCH y pre-releases: SNAPSHOT y rc únicos por build
 *    (X.Y.Z-SNAPSHOT.BUILD.SHA, X.Y.Z-rc.BUILD.SHA) o rc identificado por contenido (X.Y.Z-rc.TREE),
 *    el release candidate de una promoción dev -> main.
 *  - "latest" y tags fuera del formato versionado permitido hacen fallar la etapa.
 *  - Una versión publicada es inmutable: si el tag ya existe con otro commit, falla.
 *    Si existe con el mismo commit (re-ejecución del build), se omite sin error.
 *    Para un rc por contenido se compara el árbol de código (label io.circleguard.source-tree):
 *    una re-ejecución sobre el mismo contenido reutiliza el RC ya publicado.
 *  - Las versiones estables de main no se construyen aquí: las crea promoteRelease por retag
 *    del RC aprobado.
 *
 * Devuelve la referencia publicada, por ejemplo localhost:5000/circleguard/auth-service:0.4.1-rc.9f3a1c2
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
    String buildContext = config.context ?: (service ? dockerBuild.serviceContext(service) : '.')
    String registry = config.registry ?: env.NEXUS_REGISTRY
    if (!registry) {
        error('dockerPublish: falta el registro (parámetro "registry" o variable global NEXUS_REGISTRY en JCasC)')
    }
    String namespace = config.namespace ?: (env.IMAGE_NAMESPACE ?: 'circleguard')
    String credentialsId = config.credentialsId ?: 'nexus-ci'
    String revision = env.GIT_COMMIT ?: sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
    // Árbol de código de HEAD: identifica el contenido aunque cambie el commit (p. ej. el merge
    // temporal que Jenkins crea en cada build de un PR).
    String sourceTree = sh(script: 'git rev-parse HEAD^{tree}', returnStdout: true).trim()
    boolean byContent = isContentTag(version)
    String ref = "${registry}/${namespace}/${image}:${version}"
    String labels = imageLabels(image: image, version: version, revision: revision, sourceTree: sourceTree)

    withEnv([
        "IMAGE_REF=${ref}",
        "REGISTRY=${registry}",
        "DOCKERFILE=${dockerfile}",
        "BUILD_CONTEXT=${buildContext}",
        "IMAGE_VERSION=${version}",
        "REVISION=${revision}",
        "SOURCE_TREE=${sourceTree}",
        // Login isolated per build and per image (publications run in parallel), not in Jenkins' home
        "DOCKER_CONFIG=${env.WORKSPACE}/.docker-publish-${image}"
    ]) {
        try {
            withCredentials([usernamePassword(credentialsId: credentialsId,
                    usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
                sh 'printf "%s" "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin'
            }

            String identity = byContent ? sourceTree : revision
            String published = publishedLabel(byContent ? 'io.circleguard.source-tree' : 'org.opencontainers.image.revision')
            if (published == identity) {
                echo "dockerPublish: ${ref} ya está publicada desde ${byContent ? 'el árbol' : 'el commit'} ${identity}; se omite"
            } else if (published) {
                error("dockerPublish: ${ref} ya existe (${byContent ? 'árbol' : 'commit'} ${published}). Las versiones publicadas son " +
                        'inmutables: genere una versión nueva en lugar de sobrescribirla.')
            } else {
                sh """
                    docker build --pull \\
                      -f "\$DOCKERFILE" \\
                      --build-arg VERSION="\$IMAGE_VERSION" \\
                      ${labels} \\
                      -t "\$IMAGE_REF" \\
                      "\$BUILD_CONTEXT"
                    docker push "\$IMAGE_REF"
                """
                echo "dockerPublish: publicada ${ref}"
            }
        } finally {
            sh 'docker logout "$REGISTRY" >/dev/null 2>&1 || true; rm -rf "$DOCKER_CONFIG"'
            // The image is already in the registry; the local copy would only fill the CI disk.
            sh 'docker image rm "$IMAGE_REF" >/dev/null 2>&1 || true'
        }
    }
    return ref
}

/** rc identificado por contenido: X.Y.Z-rc.<7 caracteres hexadecimales del árbol>. */
boolean isContentTag(String tag) {
    return tag ==~ /\d+\.\d+\.\d+-rc\.[0-9a-f]{7}/
}

/** Falla con un mensaje explicativo si el tag no es una versión MAJOR.MINOR.PATCH o pre-release. */
def validateTag(String tag) {
    if (!tag) {
        error('dockerPublish: falta "version" (formato MAJOR.MINOR.PATCH o pre-release)')
    }
    if (tag.equalsIgnoreCase('latest')) {
        error("dockerPublish: el tag 'latest' no está permitido. Cada imagen se publica solo con su " +
                'versión MAJOR.MINOR.PATCH para poder desplegar y revertir versiones exactas (HU-07).')
    }
    if (!(tag ==~ /\d+\.\d+\.\d+(?:-(?:SNAPSHOT|rc)\.\d+\.[0-9a-f]{7}|-rc\.[0-9a-f]{7})?/)) {
        error("dockerPublish: '${tag}' no es una versión estable o pre-release permitida (p. ej. 1.4.0, " +
                '1.4.0-SNAPSHOT.12.a83f21c, 1.4.0-rc.12.a83f21c o 1.4.0-rc.9f3a1c2).')
    }
}

/**
 * Valor del label "key" con el que se publicó IMAGE_REF, '' si el tag no existe en el registro,
 * o 'desconocido' si existe sin ese label.
 * Usa el daemon Docker (no la API HTTP) porque el registro solo es alcanzable
 * como localhost desde el host CI.
 */
def publishedLabel(String key) {
    int status = sh(script: 'docker pull -q "$IMAGE_REF" >/dev/null 2>&1', returnStatus: true)
    if (status != 0) {
        return ''
    }
    String json = sh(
        script: 'docker image inspect -f \'{{ json .Config.Labels }}\' "$IMAGE_REF"',
        returnStdout: true
    ).trim()
    return labelValue(json, key) ?: 'desconocido'
}

@NonCPS
String labelValue(String json, String key) {
    def labels = new groovy.json.JsonSlurperClassic().parseText(json ?: 'null')
    def value = labels instanceof Map ? labels[key] : null
    return value == null ? '' : value.toString()
}