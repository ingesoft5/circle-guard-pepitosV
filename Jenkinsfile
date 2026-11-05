@Library('circleguard-shared-library') _

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
                gradleBuild()
            }
        }

        stage('Unit tests') {
            steps {
                runTests()
            }
        }
    }

    post {
        failure {
            echo 'Pipeline failed. Review the stage logs and published test results.'
        }
    }
}
