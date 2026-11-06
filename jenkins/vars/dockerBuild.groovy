def call(Map config = [:]) {
    String version = (config.version ?: "ci-${env.BUILD_NUMBER ?: 'local'}").toString().trim()
    String service = config.service
    String image = config.image ?: (service ? service.replaceFirst(/^circleguard-/, '') : null)
    if (!image) {
        error('dockerBuild: indique "service" o "image"')
    }

    String dockerfile = config.dockerfile ?: (service ? "services/${service}/Dockerfile" : null)
    if (!dockerfile) {
        error('dockerBuild: indique "dockerfile" cuando no usa "service"')
    }

    String buildContext = config.context ?: '.'
    String namespace = config.namespace ?: (env.IMAGE_NAMESPACE ?: 'circleguard')
    String revision = env.GIT_COMMIT ?: sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
    String imageRef = "${namespace}/${image}:${version}"
    String pullFlag = (config.pull == false) ? '' : '--pull'
    // The build only proves that the image builds: dockerPublish rebuilds it on main.
    // Keeping it would leave one image per service and build on the CI disk.
    boolean keep = config.keep == true

    withEnv([
        "IMAGE_REF=${imageRef}",
        "DOCKERFILE=${dockerfile}",
        "BUILD_CONTEXT=${buildContext}",
        "IMAGE_VERSION=${version}",
        "REVISION=${revision}",
        "DOCKER_PULL=${pullFlag}"
    ]) {
        sh '''
            docker build $DOCKER_PULL \
              -f "$DOCKERFILE" \
              --build-arg VERSION="$IMAGE_VERSION" \
              --label org.opencontainers.image.revision="$REVISION" \
              -t "$IMAGE_REF" \
              "$BUILD_CONTEXT"
        '''
        echo "dockerBuild: imagen construida ${imageRef}"
        if (!keep) {
            sh 'docker image rm "$IMAGE_REF" >/dev/null'
            echo "dockerBuild: removed ${imageRef} from the CI host"
        }
    }

    return imageRef
}
