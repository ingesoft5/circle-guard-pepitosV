def call(Map config = [:]) {
    def service = config.service
    def tasks = config.tasks ?: 'assemble'
    def target = service ? ":services:${service}:${tasks}" : tasks
    def args = ['./gradlew']

    if (config.gradleArgs) {
        args.add(config.gradleArgs)
    }
    args.add(target)

    // JENKINS_NODE_COOKIE: Jenkins kills the processes a step leaves behind; this keeps the Gradle
    // daemon alive for the next call (see GRADLE_OPTS in the Jenkinsfile).
    withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
        sh args.join(' ')
    }
}
