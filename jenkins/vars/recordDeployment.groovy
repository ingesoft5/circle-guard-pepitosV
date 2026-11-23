/**
 * Registra un despliegue o rollback de un ambiente (HU-51) en dos lugares:
 *
 *   - Un GitHub Deployment del ambiente (dev, stage o prod) con su estado success o failure.
 *     GitHub lo muestra en la pestaña Deployments y marca como inactivo el anterior.
 *   - Una fila en docs/release-notes/registro-ambientes.md de la rama registro-despliegues:
 *     fecha, ambiente, versión, tipo, quién aprobó, resultado y build.
 *
 *   recordDeployment(environment: 'prod', version: '0.7.0', kind: 'deploy',
 *                    approver: 'admin', result: 'success')
 *
 * kind es deploy (versión estable), rc (release candidate en stage) o rollback. main y dev
 * están protegidas, así que el archivo vive en su propia rama, sin PR: cada registro agrega
 * una fila y la publica. Si el registro falla, el build queda UNSTABLE; el despliegue no se
 * toca. Usa la credencial github-pat, que necesita Contents y Deployments con escritura.
 */
def call(Map config = [:]) {
    Map record = [
        environment  : config.environment.toString(),
        version      : (config.version ?: 'unknown').toString(),
        kind         : (config.kind ?: 'deploy').toString(),
        approver     : (config.approver ?: 'unknown').toString(),
        result       : config.result == 'success' ? 'success' : 'failure',
        credentialsId: (config.credentialsId ?: 'github-pat').toString(),
        branch       : (config.branch ?: env.DEPLOY_LOG_BRANCH ?: 'registro-despliegues').toString(),
        buildUrl     : (env.BUILD_URL ?: '').toString(),
        buildName    : "${env.JOB_NAME ?: 'build'} #${env.BUILD_NUMBER ?: '?'}".toString(),
        host         : (env."K8S_HOST_${config.environment.toString().toUpperCase()}" ?: '').toString()
    ]
    record.ref = config.ref ?: (semver.isVersion(record.version) ? "v${record.version}" :
            (env.CHANGE_BRANCH ?: env.BRANCH_NAME ?: 'main'))
    record.date = sh(script: 'date -u "+%Y-%m-%d %H:%M UTC"', returnStdout: true).trim()

    echo "recordDeployment: ${record.environment} ${record.version} (${record.kind}) " +
            "approver=${record.approver} result=${record.result}"
    try {
        githubDeployment(record)
    } catch (Exception e) {
        unstable("recordDeployment: no se pudo crear el GitHub Deployment: ${e.message}")
    }
    try {
        appendToLog(record)
    } catch (Exception e) {
        unstable("recordDeployment: no se pudo agregar la fila a ${record.branch}: ${e.message}")
    }
}

/** GitHub Deployment del ambiente y su estado. Si GitHub no conoce el ref (una rama release ya borrada), usa main. */
def githubDeployment(Map record) {
    String repo = releaseNotes.repository()
    String api = env.GITHUB_API_URL ?: 'https://api.github.com'
    List<String> refs = record.ref == 'main' ? ['main'] : [record.ref.toString(), 'main']

    withEnv(["GH_REPO_API=${api}/repos/${repo}"]) {
        withCredentials([usernamePassword(credentialsId: record.credentialsId,
                usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
            try {
                String id = null
                for (String ref : refs) {
                    writeFile file: '.deployment.json', text: deploymentPayload(record, ref)
                    String status = sh(script: '''
                        curl -sS -o .deployment-response.json -w '%{http_code}' -X POST \
                          -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" \
                          -H "Content-Type: application/json" --data @.deployment.json "$GH_REPO_API/deployments"
                    ''', returnStdout: true).trim()
                    String response = readFile('.deployment-response.json')
                    if (status == '201') {
                        id = publishGithubRelease.jsonField(response, 'id')
                        break
                    }
                    if (status != '422') {
                        error("GitHub respondió HTTP ${status}: ${response.take(300)}")
                    }
                    echo "recordDeployment: GitHub no reconoce el ref ${ref} (HTTP 422)"
                }
                if (!id) {
                    error("ningún ref válido entre ${refs}")
                }

                writeFile file: '.deployment-status.json', text: statusPayload(record)
                String status
                withEnv(["DEPLOYMENT_ID=${id}"]) {
                    status = sh(script: '''
                        curl -sS -o .deployment-response.json -w '%{http_code}' -X POST \
                          -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" \
                          -H "Content-Type: application/json" --data @.deployment-status.json \
                          "$GH_REPO_API/deployments/$DEPLOYMENT_ID/statuses"
                    ''', returnStdout: true).trim()
                }
                if (!(status ==~ /2\d\d/)) {
                    error("GitHub respondió HTTP ${status} al estado: ${readFile('.deployment-response.json').take(300)}")
                }
                echo "recordDeployment: GitHub Deployment ${id} de ${record.environment} en ${record.result}"
            } finally {
                sh 'rm -f .deployment.json .deployment-status.json .deployment-response.json'
            }
        }
    }
}

/**
 * Agrega la fila al registro en un clon aparte, para no tocar el checkout del build. Si otro
 * build publicó la rama entre el fetch y el push, vuelve a empezar (hasta 3 intentos).
 */
def appendToLog(Map record) {
    String remote = sh(script: 'git remote get-url origin', returnStdout: true).trim()
    String repoUrl = "https://github.com/${releaseNotes.repository()}"
    String worktree = '.deploy-log'
    String file = "${worktree}/docs/release-notes/registro-ambientes.md"

    withEnv(["LOG_REMOTE=${remote}", "LOG_BRANCH=${record.branch}", "WT=${worktree}",
             "COMMIT_TITLE=chore(registro): ${record.kind} ${record.version} en ${record.environment} (${record.result})"]) {
        withCredentials([gitUsernamePassword(credentialsId: record.credentialsId)]) {
            try {
                boolean pushed = false
                for (int attempt = 1; attempt <= 3 && !pushed; attempt++) {
                    sh '''
                        rm -rf "$WT" && git init --quiet "$WT"
                        cd "$WT"
                        git remote add origin "$LOG_REMOTE"
                        if git ls-remote --exit-code --heads origin "$LOG_BRANCH" >/dev/null; then
                          git fetch --quiet --depth 1 origin "$LOG_BRANCH"
                          git checkout --quiet -B "$LOG_BRANCH" FETCH_HEAD
                        else
                          git symbolic-ref HEAD "refs/heads/$LOG_BRANCH"
                        fi
                        mkdir -p docs/release-notes
                    '''
                    String current = fileExists(file) ? readFile(file) : logHeader(repoUrl)
                    writeFile file: file, text: current.replaceAll(/\s*$/, '') + '\n' + logRow(record) + '\n'
                    sh '''
                        cd "$WT"
                        git add docs/release-notes/registro-ambientes.md
                        git -c user.name="CircleGuard CI" -c user.email="ci@circleguard.local" commit --quiet \
                          -m "$COMMIT_TITLE" -m "Generated by the Jenkins pipeline (HU-51)."
                    '''
                    pushed = sh(script: 'cd "$WT" && git push --quiet origin "HEAD:refs/heads/$LOG_BRANCH"',
                            returnStatus: true) == 0
                    if (!pushed) {
                        echo "recordDeployment: la rama ${record.branch} cambió durante el registro, intento ${attempt} de 3"
                    }
                }
                if (!pushed) {
                    error('el push fue rechazado 3 veces')
                }
                echo "recordDeployment: fila agregada en ${record.branch}:docs/release-notes/registro-ambientes.md"
            } finally {
                sh 'rm -rf "$WT"'
            }
        }
    }
}

@NonCPS
String logHeader(String repoUrl) {
    return """# Registro de despliegues por ambiente

Lo escribe el pipeline de Jenkins en cada despliegue y rollback de dev, stage y prod (HU-51):
no se edita a mano. Cómo se llena y quién aparece como aprobador:
[docs/release-notes/trazabilidad.md](${repoUrl}/blob/main/docs/release-notes/trazabilidad.md).
El mismo registro está en GitHub, en [Deployments](${repoUrl}/deployments).

| Fecha | Ambiente | Versión | Tipo | Aprobó | Resultado | Build |
|:---|:---|:---|:---|:---|:---|:---|
"""
}

@NonCPS
String logRow(Map record) {
    String kind = [deploy: 'despliegue', rc: 'release candidate', rollback: 'rollback'][record.kind] ?: record.kind
    String result = record.result == 'success' ? 'éxito' : 'fallo'
    String build = record.buildUrl ? "[${record.buildName}](${record.buildUrl})" : record.buildName
    List<String> cells = [record.date, record.environment, record.version, kind, record.approver, result, build]
    return '| ' + cells.collect { it.toString().replace('|', '\\|').replaceAll(/\s+/, ' ') }.join(' | ') + ' |'
}

@NonCPS
String deploymentPayload(Map record, String ref) {
    return groovy.json.JsonOutput.toJson([
        ref                   : ref,
        environment           : record.environment,
        description           : "${record.kind} ${record.version} · ${record.approver}".toString().take(140),
        auto_merge            : false,
        required_contexts     : [],
        production_environment: record.environment == 'prod',
        transient_environment : false,
        payload               : [
            version : record.version,
            kind    : record.kind,
            approver: record.approver,
            build   : record.buildUrl
        ]
    ])
}

@NonCPS
String statusPayload(Map record) {
    Map status = [
        state      : record.result,
        description: "${record.result == 'success' ? 'Desplegado' : 'Falló'}: ${record.version} · ${record.approver}".toString().take(140)
    ]
    if (record.buildUrl) {
        status.log_url = record.buildUrl
    }
    if (record.host) {
        status.environment_url = "http://${record.host}".toString()
    }
    return groovy.json.JsonOutput.toJson(status)
}
