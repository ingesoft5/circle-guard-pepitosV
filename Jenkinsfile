pipeline {
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
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
                sh './gradlew --no-daemon assemble'
            }
        }

        stage('Unit tests') {
            steps {
                sh './gradlew --no-daemon test'
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
        }
        failure {
            echo 'Pipeline failed. Review the stage logs and published test results.'
        }
    }
}
