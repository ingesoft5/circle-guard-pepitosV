def call(Map config = [:]) {
    def service = config.service
    def target = service ? ":services:${service}:test" : 'test'
    def testResults = config.testResults ?: '**/build/test-results/test/*.xml'

    try {
        sh "./gradlew --no-daemon ${target}"
    } finally {
        junit allowEmptyResults: true, testResults: testResults
    }
}
