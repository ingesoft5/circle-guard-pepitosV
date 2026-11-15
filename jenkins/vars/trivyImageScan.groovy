/**
 * Fails the build when the local image has a CRITICAL or HIGH vulnerability.
 * The JSON report is archived with the build.
 */
def call(Map config = [:]) {
    String image = config.image
    if (!image) {
        error('trivyImageScan: falta "image"')
    }
    String safe = image.replaceAll('[^A-Za-z0-9._-]', '_')
    String report = "build/reports/trivy-image-${safe}.json"
    withEnv(["TRIVY_IMAGE=${image}", "TRIVY_IMAGE_REPORT=${report}"]) {
        sh '''
            mkdir -p "$(dirname "$TRIVY_IMAGE_REPORT")"
            docker run --rm \
              -v /var/run/docker.sock:/var/run/docker.sock \
              -v "$PWD":/repo \
              aquasec/trivy:0.57.1 image \
              --exit-code 1 \
              --severity CRITICAL,HIGH \
              --ignorefile /repo/.trivyignore \
              --format json \
              --output "/repo/$TRIVY_IMAGE_REPORT" \
              "$TRIVY_IMAGE"
        '''
    }
    archiveArtifacts allowEmptyArchive: true, artifacts: report
}
