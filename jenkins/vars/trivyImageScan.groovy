/**
 * Fails the build when the local image has a CRITICAL or HIGH vulnerability.
 * The JSON report is archived with the build.
 *
 * One scan runs at a time and the vulnerability database stays in a shared
 * volume. Parallel scans were each downloading the Java DB and filled the disk.
 * Only the ignore file is copied in: a bind mount of the workspace is empty.
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
                exec 9>/tmp/trivy-scan.lock
                flock 9
                cid="$(docker create \
                    -v /var/run/docker.sock:/var/run/docker.sock \
                    -v circleguard-trivy-cache:/root/.cache \
                    aquasec/trivy:0.57.1 \
                    image \
                    --scanners vuln \
                    --exit-code 1 \
                    --severity CRITICAL,HIGH \
                    --ignorefile /trivyignore \
                    --format json \
                    --output /tmp/trivy-image.json \
                    "$TRIVY_IMAGE")"
                cleanup() { docker rm "$cid" >/dev/null 2>&1 || true; }
                trap cleanup EXIT
                docker cp .trivyignore "$cid":/trivyignore
                rc=0
                docker start -a "$cid" || rc=$?
                docker cp "$cid":/tmp/trivy-image.json "$TRIVY_IMAGE_REPORT" || true
                exit "$rc"
            '''
        }
    } finally {
        archiveArtifacts allowEmptyArchive: true, artifacts: report
    }
}
