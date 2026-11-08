def call(Map config = [:]) {
    def server = config.server ?: 'SonarQube'
    def service = config.service
    def target = service ? ":services:${service}:sonar" : 'sonar'
    def args = ['./gradlew', '--no-daemon']

    if (config.projectKey) {
        args.add("-Dsonar.projectKey=${config.projectKey}")
    }
    if (config.projectName) {
        args.add("-Dsonar.projectName=${config.projectName}")
    }
    if (config.extraArgs) {
        args.add(config.extraArgs)
    }

    args.add(target)

    withSonarQubeEnv(server) {
        sh args.join(' ')
    }
}
