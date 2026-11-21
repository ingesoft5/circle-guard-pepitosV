// Rollback (HU-17, HU-52): takes one environment back on its k3s cluster.
// Job "circleguard-rollback", declared in infra/compose/ci/jenkins/casc.yaml.
//   VERSION empty -> helm rollback to the previous revision (the usual case after a bad release).
//   VERSION set   -> redeploy that version, which must already be in the registry.
library "circleguard-shared-library@${env.ROLLBACK_BRANCH ?: 'main'}"

// Set while the pipeline runs; used for the build description and the summary.
String targetVersion = null
String targetRevision = null
String currentVersion = null
String requestedBy = null

// Image tag the environment runs now, or of one Helm revision.
String releaseImageTag(String namespace, String revision = null) {
    withEnv(["NS=${namespace}", "REV=${revision ?: ''}"]) {
        return sh(
            script: 'helm -n "$NS" get values circleguard --all ${REV:+--revision "$REV"} ' +
                    '| awk \'/^image:/{f=1; next} f && /^  tag:/{print $2; exit} /^[^ ]/{f=0}\'',
            returnStdout: true
        ).trim()
    }
}

pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '50'))
    }

    parameters {
        choice(name: 'ENVIRONMENT', choices: ['stage', 'dev', 'prod'],
                description: 'Environment to roll back')
        string(name: 'VERSION', defaultValue: '',
                description: 'Empty: previous Helm revision. Or a version already in Nexus, e.g. 0.1.0 or v0.1.0')
    }

    environment {
        NAMESPACE = "circleguard-${params.ENVIRONMENT}"
    }

    stages {
        stage('Read current release') {
            steps {
                script {
                    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    requestedBy = cause ? "${cause[0].userName} (${cause[0].userId})" : 'unknown'

                    withCredentials([file(credentialsId: "kubeconfig-${params.ENVIRONMENT}", variable: 'KUBECONFIG')]) {
                        sh 'helm -n "$NAMESPACE" history circleguard --max 10'
                        currentVersion = releaseImageTag(env.NAMESPACE) ?: 'none'
                        if (!params.VERSION.trim()) {
                            // Last superseded revision older than the deployed one.
                            targetRevision = sh(
                                script: 'helm -n "$NAMESPACE" history circleguard --max 50 ' +
                                        '| awk -F \'\\t\' \'$1 + 0 > 0 && $3 ~ /superseded/ {r = $1 + 0} END {if (r) print r}\'',
                                returnStdout: true
                            ).trim()
                            if (!targetRevision) {
                                error("No previous Helm revision in ${env.NAMESPACE}; pass a VERSION instead")
                            }
                            targetVersion = releaseImageTag(env.NAMESPACE, targetRevision)
                        }
                    }
                }
            }
        }

        stage('Validate version') {
            when { expression { params.VERSION.trim() } }
            steps {
                script {
                    targetVersion = params.VERSION.trim().replaceFirst(/^v/, '')
                    if (!(targetVersion ==~ /\d+\.\d+\.\d+/)) {
                        error("VERSION must be MAJOR.MINOR.PATCH, got '${params.VERSION}'")
                    }
                    // Every application image of the chart.
                    List<String> images = sh(
                        script: "awk '/^services:/{f=1} /^middleware:/{f=0} f && /^    image: /{print \"circleguard/\" \$2}' " +
                                'deploy/k8s/circleguard/values.yaml | sort -u',
                        returnStdout: true
                    ).trim().split('\n').findAll { it }
                    if (!images) {
                        error('No application images found in deploy/k8s/circleguard/values.yaml')
                    }

                    // Registry API inside the CI network; nothing is pulled to the CI disk.
                    String api = env.NEXUS_REGISTRY_API ?: 'http://nexus:5000'
                    List<String> missing = []
                    withCredentials([usernamePassword(credentialsId: 'nexus-ci',
                            usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
                        for (String image : images) {
                            withEnv(["MANIFEST_URL=${api}/v2/${image}/manifests/${targetVersion}"]) {
                                String code = sh(
                                    script: 'curl -s -o /dev/null -w "%{http_code}" -I ' +
                                            '-u "$REGISTRY_USER:$REGISTRY_PASSWORD" ' +
                                            '-H "Accept: application/vnd.docker.distribution.manifest.v2+json, ' +
                                            'application/vnd.oci.image.manifest.v1+json, ' +
                                            'application/vnd.oci.image.index.v1+json" "$MANIFEST_URL"',
                                    returnStdout: true
                                ).trim()
                                if (code != '200') {
                                    missing << "${image}:${targetVersion} (HTTP ${code})"
                                }
                            }
                        }
                    }
                    if (missing) {
                        error("Version ${targetVersion} is not in the registry, the environment was not " +
                                "touched. Missing: ${missing.join(', ')}")
                    }
                    echo "All ${images.size()} images of ${targetVersion} are in the registry"
                }
            }
        }

        stage('Approve production') {
            when { expression { params.ENVIRONMENT == 'prod' } }
            steps {
                // Only the admin account approves prod (docs/release-notes/politica-cambios.md)
                input message: "Roll back prod from ${currentVersion} to ${targetVersion}?", ok: 'Roll back',
                        submitter: 'admin'
            }
        }

        stage('Roll back') {
            steps {
                script {
                    String target = targetRevision ? "revision ${targetRevision} (${targetVersion})" : targetVersion
                    currentBuild.description = "${params.ENVIRONMENT}: ${currentVersion} -> ${target} by ${requestedBy}"
                    echo "Rollback of ${params.ENVIRONMENT} from ${currentVersion} to ${target}, requested by ${requestedBy}"
                    if (targetRevision) {
                        withEnv(["TARGET_REVISION=${targetRevision}"]) {
                            withCredentials([file(credentialsId: "kubeconfig-${params.ENVIRONMENT}", variable: 'KUBECONFIG')]) {
                                sh '''
                                    helm -n "$NAMESPACE" rollback circleguard "$TARGET_REVISION" --wait --timeout 10m
                                    kubectl -n "$NAMESPACE" get pods -o wide
                                    helm -n "$NAMESPACE" history circleguard --max 5
                                '''
                            }
                        }
                        k8sSmokeTest(environment: params.ENVIRONMENT)
                    } else {
                        // Helm upgrade with --atomic, rollout wait and smoke test.
                        deployK8s(environment: params.ENVIRONMENT, version: targetVersion)
                    }
                }
            }
        }
    }

    post {
        success {
            echo "ROLLBACK OK | environment=${params.ENVIRONMENT} | from=${currentVersion} | " +
                    "to=${targetVersion} | revision=${targetRevision ?: 'new'} | by=${requestedBy}"
        }
        failure {
            echo "ROLLBACK FAILED | environment=${params.ENVIRONMENT} | from=${currentVersion ?: 'unknown'} | " +
                    "to=${targetVersion ?: params.VERSION} | by=${requestedBy ?: 'unknown'}"
        }
    }
}
