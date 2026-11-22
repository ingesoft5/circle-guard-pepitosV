def call(Map config = [:]) {
    def service = config.service
    def integration = config.integration == true
    // Integration tests bring up containers and are not part of the coverage gate.
    def coverage = config.coverage == null ? !integration : config.coverage
    def tasks = [integration ? 'integrationTest' : 'test']
    if (coverage) {
        // jacocoTestReport writes the XML that publishCoverage reads; the
        // verification fails the build when a gated service is below 60 %.
        tasks += ['jacocoTestReport', 'jacocoTestCoverageVerification']
    }
    def targets = tasks.collect { service ? ":services:${service}:${it}" : it }
    def testResults = config.testResults ?: (integration
        ? '**/build/test-results/integrationTest/*.xml'
        : '**/build/test-results/test/*.xml')
    def publish = config.publish == null ? true : config.publish

    try {
        // JENKINS_NODE_COOKIE keeps the Gradle daemon alive after the step (see gradleBuild).
        withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
            sh "./gradlew ${targets.join(' ')}"
        }
    } finally {
        if (publish) {
            junit allowEmptyResults: true, testResults: testResults
        }
    }
}