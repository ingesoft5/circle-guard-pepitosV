def call(Map config = [:]) {
    def service = config.service
    def coverage = config.coverage == null ? true : config.coverage
    def tasks = ['test']
    if (coverage) {
        // jacocoTestReport writes the XML that publishCoverage reads; the
        // verification fails the build when a gated service is below 60 %.
        tasks += ['jacocoTestReport', 'jacocoTestCoverageVerification']
    }
    def targets = tasks.collect { service ? ":services:${service}:${it}" : it }
    def testResults = config.testResults ?: '**/build/test-results/test/*.xml'
    def publish = config.publish == null ? true : config.publish

    try {
        sh "./gradlew --no-daemon ${targets.join(' ')}"
    } finally {
        if (publish) {
            junit allowEmptyResults: true, testResults: testResults
        }
    }
}