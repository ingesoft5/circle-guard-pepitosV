@Library('circleguard-shared-library') _

def SERVICES = [
    'circleguard-auth-service',
    'circleguard-identity-service',
    'circleguard-promotion-service',
    'circleguard-notification-service',
    'circleguard-form-service',
    'circleguard-file-service',
    'circleguard-gateway-service',
    'circleguard-dashboard-service'
]

pipeline {
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
        skipDefaultCheckout(true)
    }

    environment {
        GRADLE_USER_HOME = "${WORKSPACE}/.gradle-ci"
        GRADLE_OPTS = '-Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx1g'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'chmod +x gradlew'
            }
        }

        stage('Compile') {
            steps {
                gradleBuild()
            }
        }

        stage('Unit tests') {
            steps {
                runTests()
            }
        }

        stage('Publish images') {
            when {
                branch 'main'
            }
            steps {
                script {
                    def version = resolveReleaseVersion()
                    if (!version) {
                        echo 'HEAD no tiene un tag de release vMAJOR.MINOR.PATCH: no se publican imágenes.'
                        return
                    }
                    currentBuild.description = "v${version}"

                    for (String service : SERVICES) {
                        dockerPublish(service: service, version: version)
                    }
                    dockerPublish(image: 'web-client', dockerfile: 'mobile/Dockerfile',
                            context: 'mobile', version: version)
                }
            }
        }
    }

    post {
        failure {
            echo 'Pipeline failed. Review the stage logs and published test results.'
        }
    }
}
