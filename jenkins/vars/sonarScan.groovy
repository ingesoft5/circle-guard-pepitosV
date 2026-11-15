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

    // withSonarQubeEnv y waitForQualityGate toman el primer report-task.txt que
    // encuentran en el workspace. Se borran los de análisis anteriores para que
    // el gate consulte la tarea de este servicio y no la del primero que se analizó.
    sh 'rm -f build/sonar/report-task.txt services/*/build/sonar/report-task.txt'

    withSonarQubeEnv(server) {
        sh args.join(' ')
    }
}
