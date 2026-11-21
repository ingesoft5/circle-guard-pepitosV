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

    String buildContext = config.context ?: (service ? serviceContext(service) : '.')
    String namespace = config.namespace ?: (env.IMAGE_NAMESPACE ?: 'circleguard')
    String revision = env.GIT_COMMIT ?: sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
    String sourceTree = sh(script: 'git rev-parse HEAD^{tree}', returnStdout: true).trim()
    String labels = imageLabels(image: image, version: version, revision: revision, sourceTree: sourceTree)
    String imageRef = "${namespace}/${image}:${version}"
    String pullFlag = (config.pull == false) ? '' : '--pull'
    // The build only proves that the image builds: dockerPublish builds the published one.
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
        try {
            sh """
                docker build \$DOCKER_PULL \\
                  -f "\$DOCKERFILE" \\
                  --build-arg VERSION="\$IMAGE_VERSION" \\
                  --build-arg PACKAGES_DATE="\$(date -u +%F)" \\
                  ${labels} \\
                  -t "\$IMAGE_REF" \\
                  "\$BUILD_CONTEXT"
            """
            echo "dockerBuild: imagen construida ${imageRef}"
            trivyImageScan(image: imageRef)
        } finally {
            if (!keep) {
                sh 'docker image rm "$IMAGE_REF" >/dev/null 2>&1 || true'
                echo "dockerBuild: removed ${imageRef} from the CI host"
            }
        }
    }

    return imageRef
}

/**
 * Docker build context of a service: build/docker, where the Gradle task dockerContext (run by
 * assemble in the Compile stage) leaves the boot JAR as app.jar. The image packages the JAR the
 * pipeline compiled and tested instead of compiling again inside Docker.
 */
String serviceContext(String service) {
    String dir = "services/${service}/build/docker"
    if (!fileExists("${dir}/app.jar")) {
        error("${dir}/app.jar no existe: la imagen empaqueta el JAR de la etapa Compile " +
                "(./gradlew :services:${service}:assemble). ¿Se compiló este servicio en este build?")
    }
    return dir
}
