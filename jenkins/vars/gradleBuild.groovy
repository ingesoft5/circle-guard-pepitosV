def call(Map config = [:]) {
    def service = config.service
    def tasks = config.tasks ?: 'assemble'
    def target = service ? ":services:${service}:${tasks}" : tasks
    def args = ['./gradlew', '--no-daemon']

    if (config.gradleArgs) {
        args.add(config.gradleArgs)
    }
    args.add(target)

    sh args.join(' ')
}
