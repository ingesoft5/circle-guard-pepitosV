/**
 * Crea o actualiza el GitHub Release del tag vMAJOR.MINOR.PATCH con las notas dadas (HU-22).
 *
 *   publishGithubRelease(version: '1.3.0', notes: releaseNotes(version: '1.3.0'))
 *
 * Idempotente: si el release del tag ya existe (re-ejecución), actualiza sus notas.
 * El tag debe existir en GitHub (lo publica pushReleaseTag). Usa la credencial
 * github-pat, que necesita permiso de escritura sobre el repositorio.
 *
 * latest: false publica sin marcarlo como "Latest" (el job de auditoría de HU-51 lo usa al
 * completar versiones anteriores). Por defecto es true.
 *
 *   Map found = publishGithubRelease.existing(version: '1.3.0')  // [found: true, body: '...']
 */
def call(Map config = [:]) {
    String version = (config.version ?: '').toString().trim()
    if (!semver.isVersion(version)) {
        error("publishGithubRelease: '${version}' no es una versión MAJOR.MINOR.PATCH")
    }
    String tag = "v${version}"
    String repo = config.repository ?: releaseNotes.repository()
    String api = config.apiUrl ?: (env.GITHUB_API_URL ?: 'https://api.github.com')
    String credentialsId = config.credentialsId ?: 'github-pat'

    boolean latest = config.latest != false
    writeFile file: '.release-payload.json', text: payload(tag, withoutTitle(config.notes ?: ''), latest)

    withEnv(["GH_REPO_API=${api}/repos/${repo}", "RELEASE_TAG=${tag}"]) {
        withCredentials([usernamePassword(credentialsId: credentialsId,
                usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
            try {
                String found = sh(script: '''
                    curl -sS -o .release-existing.json -w '%{http_code}' \
                      -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" \
                      "$GH_REPO_API/releases/tags/$RELEASE_TAG"
                ''', returnStdout: true).trim()

                String method
                String url
                if (found == '200') {
                    method = 'PATCH'
                    url = "${api}/repos/${repo}/releases/${jsonField(readFile('.release-existing.json'), 'id')}"
                } else if (found == '404') {
                    method = 'POST'
                    url = "${api}/repos/${repo}/releases"
                } else {
                    error("publishGithubRelease: GitHub respondió HTTP ${found} al consultar ${tag}")
                }

                String status
                withEnv(["GH_METHOD=${method}", "GH_URL=${url}"]) {
                    status = sh(script: '''
                        curl -sS -o .release-response.json -w '%{http_code}' -X "$GH_METHOD" \
                          -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" \
                          -H "Content-Type: application/json" --data @.release-payload.json "$GH_URL"
                    ''', returnStdout: true).trim()
                }
                String response = readFile('.release-response.json')
                if (!(status ==~ /2\d\d/)) {
                    error("publishGithubRelease: GitHub respondió HTTP ${status}: ${response.take(300)}")
                }
                echo "publishGithubRelease: ${method == 'POST' ? 'creado' : 'actualizado'} ${jsonField(response, 'html_url')}"
            } finally {
                sh 'rm -f .release-payload.json .release-existing.json .release-response.json'
            }
        }
    }
}

/** El GitHub Release del tag vMAJOR.MINOR.PATCH: [found: false] si no existe, o [found: true, body: notas]. */
def existing(Map config = [:]) {
    String version = (config.version ?: '').toString().trim()
    if (!semver.isVersion(version)) {
        error("publishGithubRelease.existing: '${version}' no es una versión MAJOR.MINOR.PATCH")
    }
    String repo = config.repository ?: releaseNotes.repository()
    String api = config.apiUrl ?: (env.GITHUB_API_URL ?: 'https://api.github.com')
    String credentialsId = config.credentialsId ?: 'github-pat'

    withEnv(["GH_REPO_API=${api}/repos/${repo}", "RELEASE_TAG=v${version}"]) {
        withCredentials([usernamePassword(credentialsId: credentialsId,
                usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
            try {
                String status = sh(script: '''
                    curl -sS -o .release-existing.json -w '%{http_code}' \
                      -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" \
                      "$GH_REPO_API/releases/tags/$RELEASE_TAG"
                ''', returnStdout: true).trim()
                if (status == '404') {
                    return [found: false, body: '']
                }
                if (status != '200') {
                    error("publishGithubRelease.existing: GitHub respondió HTTP ${status} al consultar v${version}")
                }
                return [found: true, body: jsonField(readFile('.release-existing.json'), 'body')]
            } finally {
                sh 'rm -f .release-existing.json'
            }
        }
    }
}

/** El GitHub Release ya muestra la versión como título: se quita el "## ..." de la sección. */
@NonCPS
String withoutTitle(String notes) {
    List<String> lines = notes.readLines()
    if (lines && lines[0].startsWith('## ')) {
        lines = lines.drop(1)
    }
    return lines.join('\n').trim() + '\n'
}

@NonCPS
String payload(String tag, String body, boolean latest = true) {
    return groovy.json.JsonOutput.toJson([
        tag_name: tag, name: tag, body: body, draft: false, prerelease: false, make_latest: latest ? 'true' : 'false'
    ])
}

@NonCPS
String jsonField(String json, String field) {
    def value = new groovy.json.JsonSlurperClassic().parseText(json)[field]
    return value == null ? '' : value.toString()
}
