def call(Map config = [:]) {
    def service = config.service
    def target = service ? ":services:${service}:test" : 'test'
    def testResults = config.testResults ?: '**/build/test-results/test/*.xml'
    def publish = config.publish == null ? true : config.publish

    try {
        sh "./gradlew --no-daemon ${target}"
    } finally {
        if (publish) {
            junit allowEmptyResults: true, testResults: testResults
        }
    }
}
