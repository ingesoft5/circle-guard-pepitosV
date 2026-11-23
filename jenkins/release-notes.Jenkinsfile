// Release notes audit (HU-51): checks that every vX.Y.Z tag has its GitHub Release with the notes
// that releaseNotes generates from the Conventional Commits (HU-22), and completes the missing ones.
// Job "circleguard-release-notes", declared in infra/compose/ci/jenkins/casc.yaml.
//   APPLY off (default) -> only reports: OK, MISSING (no release) or DIFFERENT (other notes).
//   APPLY on            -> creates the missing releases and replaces the different notes.
// The highest version stays "Latest"; the older ones are published without that mark.
library "circleguard-shared-library@main"

pipeline {
    agent { label 'controller' }

    options {
        disableConcurrentBuilds()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        booleanParam(name: 'APPLY', defaultValue: false,
                description: 'Crea los releases que faltan y reemplaza las notas distintas. Sin marcar, solo informa.')
    }

    stages {
        stage('Fetch tags') {
            steps {
                withCredentials([gitUsernamePassword(credentialsId: 'github-pat')]) {
                    sh 'git fetch --quiet --force --tags origin'
                }
            }
        }

        stage('Audit release notes') {
            steps {
                script {
                    Map ctx = releaseNotes.notesContext('HEAD')
                    List<String> versions = semver.sortDesc(ctx.versions)
                    if (!versions) {
                        error('No hay tags vX.Y.Z en main')
                    }

                    List<Map> rows = []
                    for (String version : versions) {
                        // Same context the main pipeline used when it published the version.
                        Map versionCtx = releaseNotes.notesContext("v${version}")
                        String expected = publishGithubRelease.withoutTitle(releaseNotes.section(version, versionCtx))
                        Map current = publishGithubRelease.existing(version: version)
                        String state = !current.found ? 'MISSING' :
                                (normalized(current.body) == normalized(expected) ? 'OK' : 'DIFFERENT')
                        if (params.APPLY && state != 'OK') {
                            publishGithubRelease(version: version, notes: expected, latest: version == versions[0])
                            state = state == 'MISSING' ? 'CREATED' : 'UPDATED'
                        }
                        rows << [version: version, date: ctx.dates["v${version}"] ?: '', state: state]
                    }

                    String report = auditReport(rows, ctx.repoUrl)
                    writeFile file: 'release-notes-audit.md', text: report
                    archiveArtifacts artifacts: 'release-notes-audit.md'
                    echo report

                    List<String> pending = pendingVersions(rows)
                    currentBuild.description = pending ? "Pendientes: ${pending.join(', ')}" : "${rows.size()} versiones con notas"
                    if (pending) {
                        unstable("Versiones sin sus release notes: ${pending.join(', ')}. Ejecuta el job con APPLY.")
                    }
                }
            }
        }
    }
}

@NonCPS
String normalized(String text) {
    return (text ?: '').replace('\r\n', '\n').trim()
}

@NonCPS
List<String> pendingVersions(List<Map> rows) {
    return rows.findAll { it.state in ['MISSING', 'DIFFERENT'] }.collect { "v${it.version}".toString() }
}

@NonCPS
String auditReport(List<Map> rows, String repoUrl) {
    StringBuilder out = new StringBuilder('# Auditoría de release notes\n\n')
    out.append('| Versión | Fecha del tag | GitHub Release | Estado |\n|:---|:---|:---|:---|\n')
    for (Map row : rows) {
        out.append("| v${row.version} | ${row.date} | ${repoUrl}/releases/tag/v${row.version} | ${row.state} |\n")
    }
    return out.toString()
}
