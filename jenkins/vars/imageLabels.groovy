/**
 * Etiquetas (labels) de auditoría de una imagen, como argumentos --label de docker build.
 *
 *   String labels = imageLabels(image: 'auth-service', version: '0.5.0-rc.9f3a1c2',
 *                               revision: env.GIT_COMMIT, sourceTree: tree)
 *   sh "docker build ${labels} ..."
 *
 * Las etiquetas viajan dentro de la configuración de la imagen: se fijan al construir y
 * la promoción RC -> estable no las toca, así que el digest no cambia. Permiten saber, sin
 * el pipeline, de qué commit, rama, build y versión base sale cada imagen del registro:
 *
 *   docker image inspect -f '{{ json .Config.Labels }}' <registro>/circleguard/auth-service:0.5.0
 *
 * Estándar OCI (org.opencontainers.image.*): title, description, vendor, source, url,
 * revision, version, ref.name y created.
 * Propias (io.circleguard.*): service, base-version, source-tree, branch, change-id,
 * build-number, build-url y job.
 */
def call(Map config = [:]) {
    String image = config.image ?: error('imageLabels: falta "image"')
    String version = (config.version ?: '').toString()
    String revision = config.revision ?: ''
    String sourceTree = config.sourceTree ?: ''

    String source = repositoryUrl()
    Map<String, String> labels = [
        'org.opencontainers.image.title'      : "circleguard/${image}",
        'org.opencontainers.image.description': "CircleGuard · ${image}",
        'org.opencontainers.image.vendor'     : 'CircleGuard',
        'org.opencontainers.image.source'     : source,
        'org.opencontainers.image.url'        : source,
        'org.opencontainers.image.revision'   : revision,
        'org.opencontainers.image.version'    : version,
        'org.opencontainers.image.ref.name'   : version,
        'org.opencontainers.image.created'    : new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC')),
        'io.circleguard.service'              : image,
        'io.circleguard.base-version'         : version.replaceFirst(/-.*$/, ''),
        'io.circleguard.source-tree'          : sourceTree,
        'io.circleguard.branch'               : env.CHANGE_BRANCH ?: (env.BRANCH_NAME ?: ''),
        'io.circleguard.change-id'            : env.CHANGE_ID ?: '',
        'io.circleguard.build-number'         : env.BUILD_NUMBER ?: '',
        'io.circleguard.build-url'            : env.BUILD_URL ?: '',
        'io.circleguard.job'                  : env.JOB_NAME ?: ''
    ]
    return labels.findAll { it.value }.collect { k, v -> "--label ${k}=${shellQuote(v)}" }.join(' ')
}

/** URL pública del repositorio, sin credenciales embebidas ni sufijo .git. */
String repositoryUrl() {
    if (env.GITHUB_OWNER && env.GITHUB_REPO) {
        return "https://github.com/${env.GITHUB_OWNER}/${env.GITHUB_REPO}"
    }
    String url = env.GIT_URL ?: ''
    return url.replaceFirst(/^(https?:\/\/)[^@\/]+@/, '$1').replaceFirst(/\.git$/, '')
}

@NonCPS
String shellQuote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}
