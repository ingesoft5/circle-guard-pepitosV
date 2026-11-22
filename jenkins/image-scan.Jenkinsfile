// Daily scan of the images prod runs: the latest stable tag in the registry.
// A HIGH or CRITICAL finding fails the build and Discord gets the failure.
library "circleguard-shared-library@dev"

def IMAGES = [
    'auth-service',
    'identity-service',
    'promotion-service',
    'notification-service',
    'form-service',
    'file-service',
    'gateway-service',
    'dashboard-service',
    'web-client'
]

pipeline {
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        timeout(time: 90, unit: 'MINUTES')
    }

    stages {
        stage('Scan prod images') {
            steps {
                script {
                    String version = resolveLatestStableVersion()
                    if (!version) {
                        error('No stable version is published yet, so prod has nothing to scan')
                    }
                    String registry = env.NEXUS_REGISTRY
                    if (!registry) {
                        error('NEXUS_REGISTRY is not set')
                    }
                    withEnv(["REGISTRY=${registry}", "IMAGE_VERSION=${version}"]) {
                        withCredentials([usernamePassword(credentialsId: 'nexus-ci',
                                usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
                            sh 'printf "%s" "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin'
                        }
                        try {
                            for (String image : IMAGES) {
                                String ref = "${registry}/circleguard/${image}:${version}"
                                sh "docker pull '${ref}'"
                                trivyImageScan(image: ref)
                            }
                        } finally {
                            sh 'docker logout "$REGISTRY" >/dev/null 2>&1 || true'
                        }
                    }
                }
            }
        }
    }

    post {
        failure {
            script {
                notifyDiscord(status: 'FAILED', service: 'prod-images', stage: 'Scan prod images', branch: 'prod')
            }
        }
    }
}
