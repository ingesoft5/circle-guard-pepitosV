/**
 * Fails the build when the local image has a CRITICAL or HIGH vulnerability.
 * The JSON report is archived with the build.
 *
 * Jenkins runs inside a container and talks to the host Docker daemon, so a bind
 * mount of the workspace is empty. The repository is copied into the scanner.
 * The Docker socket stays mounted so Trivy can see the image just built.
 */
def call(Map config = [:]) {
    String image = config.image
    if (!image) {
        error('trivyImageScan: falta "image"')
    }
    String safe = image.replaceAll('[^A-Za-z0-9._-]', '_')
    String report = "build/reports/trivy-image-${safe}.json"
    try {
        withEnv(["TRIVY_IMAGE=${image}", "TRIVY_IMAGE_REPORT=${report}"]) {
            sh '''
                mkdir -p "$(dirname "$TRIVY_IMAGE_REPORT")"
                cid="$(docker create \
                    -v /var/run/docker.sock:/var/run/docker.sock \
                    aquasec/trivy:0.57.1 \
                    image \
                    --exit-code 1 \
                    --severity CRITICAL,HIGH \
                    --ignorefile /repo/.trivyignore \
                    --format json \
                    --output "/repo/$TRIVY_IMAGE_REPORT" \
                    "$TRIVY_IMAGE")"
                cleanup() { docker rm "$cid" >/dev/null 2>&1 || true; }
                trap cleanup EXIT
                docker cp . "$cid":/repo
                rc=0
                docker start -a "$cid" || rc=$?
                docker cp "$cid":"/repo/$TRIVY_IMAGE_REPORT" "$TRIVY_IMAGE_REPORT" || true
                exit "$rc"
            '''
        }
    } finally {
        archiveArtifacts allowEmptyArchive: true, artifacts: report
    }
}
