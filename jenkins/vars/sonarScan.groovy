def call(Map config = [:]) {
    def server = config.server ?: 'SonarQube'
    def projectKey = config.projectKey ?: env.JOB_NAME
    def projectName = config.projectName ?: projectKey
    def args = [
        './gradlew',
        '--no-daemon',
        'sonar',
        "-Dsonar.projectKey=${projectKey}",
        "-Dsonar.projectName=${projectName}"
    ]

    if (config.extraArgs) {
        args.add(config.extraArgs)
    }

    withSonarQubeEnv(server) {
        sh args.join(' ')
    }
}
