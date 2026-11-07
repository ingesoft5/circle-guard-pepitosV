// Rollback (HU-17): redeploys a version that is already in the registry to one environment.
// Job "circleguard-rollback", declared in infra/compose/ci/jenkins/casc.yaml.
library "circleguard-shared-library@${env.ROLLBACK_BRANCH ?: 'main'}"

// Set while the pipeline runs; used for the build description and the summary.
String targetVersion = null
String currentVersion = null
String requestedBy = null

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
                description: 'Version already published in Nexus, e.g. 0.1.0 or v0.1.0')
    }

    stages {
        stage('Validate version') {
            steps {
                script {
                    targetVersion = params.VERSION.trim().replaceFirst(/^v/, '')
                    if (!(targetVersion ==~ /\d+\.\d+\.\d+/)) {
                        error("VERSION must be MAJOR.MINOR.PATCH, got '${params.VERSION}'")
                    }
                    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    requestedBy = cause ? "${cause[0].userName} (${cause[0].userId})" : 'unknown'

                    // Every image the environment runs, read from the stack definition.
                    List<String> images = sh(
                        script: "grep -oE 'circleguard/[a-z-]+:[\$][{]IMAGE_TAG' deploy/compose/stack.yml " +
                                "| cut -d: -f1 | sort -u",
                        returnStdout: true
                    ).trim().split('\n').findAll { it }
                    if (!images) {
                        error('No application images found in deploy/compose/stack.yml')
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

        stage('Read current version') {
            steps {
                script {
                    String host = env."DEPLOY_HOST_${params.ENVIRONMENT.toUpperCase()}"
                    if (!host) {
                        error("Set DEPLOY_HOST_${params.ENVIRONMENT.toUpperCase()} in infra/compose/ci/.env")
                    }
                    withEnv(["DEPLOY_TARGET=deploy@${host}",
                             "ENV_FILE=/opt/circleguard/deploy/compose/${params.ENVIRONMENT}/.env",
                             'SSH_OPTS=-o StrictHostKeyChecking=accept-new -o ConnectTimeout=15']) {
                        sshagent(credentials: ["deploy-ssh-${params.ENVIRONMENT}"]) {
                            currentVersion = sh(
                                script: 'ssh $SSH_OPTS "$DEPLOY_TARGET" "grep -E \'^IMAGE_TAG=\' $ENV_FILE | tail -n 1 | cut -d= -f2-"',
                                returnStdout: true
                            ).trim() ?: 'none'
                        }
                    }
                    currentBuild.description = "${params.ENVIRONMENT}: ${currentVersion} -> ${targetVersion} " +
                            "by ${requestedBy}"
                    echo "Rollback of ${params.ENVIRONMENT} from ${currentVersion} to ${targetVersion}, " +
                            "requested by ${requestedBy}"
                }
            }
        }

        stage('Approve production') {
            when { expression { params.ENVIRONMENT == 'prod' } }
            steps {
                input message: "Roll back prod from ${currentVersion} to ${targetVersion}?", ok: 'Roll back'
            }
        }

        stage('Redeploy') {
            steps {
                // deploy.sh pulls first (a failed pull changes nothing), then waits for every
                // service to be healthy and runs the smoke test through the web client.
                deployCompose(environment: params.ENVIRONMENT, version: targetVersion)
            }
        }
    }

    post {
        success {
            echo "ROLLBACK OK | environment=${params.ENVIRONMENT} | from=${currentVersion} | " +
                    "to=${targetVersion} | by=${requestedBy}"
        }
        failure {
            echo "ROLLBACK FAILED | environment=${params.ENVIRONMENT} | from=${currentVersion ?: 'unknown'} | " +
                    "to=${targetVersion ?: params.VERSION} | by=${requestedBy ?: 'unknown'}"
        }
    }
}
