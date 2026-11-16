/**
 * Publishes a Trivy vulnerability report for Gradle and npm dependencies.
 * The report is informational: image scanning is the build-breaking gate.
 *
 * The repository is copied into the scanner. A bind mount of the Jenkins
 * workspace is empty because the Docker daemon runs on the host.
 */
def call(Map config = [:]) {
    String report = config.report ?: 'build/reports/trivy-fs.json'
    try {
        withEnv(["TRIVY_FS_REPORT=${report}"]) {
            sh '''
                mkdir -p "$(dirname "$TRIVY_FS_REPORT")"
                cid="$(docker create aquasec/trivy:0.57.1 \
                    fs \
                    --scanners vuln \
                    --severity CRITICAL,HIGH \
                    --exit-code 0 \
                    --format json \
                    --output "/repo/$TRIVY_FS_REPORT" \
                    /repo)"
                cleanup() { docker rm "$cid" >/dev/null 2>&1 || true; }
                trap cleanup EXIT
                docker cp . "$cid":/repo
                rc=0
                docker start -a "$cid" || rc=$?
                docker cp "$cid":"/repo/$TRIVY_FS_REPORT" "$TRIVY_FS_REPORT" || true
                exit "$rc"
            '''
        }
    } finally {
        archiveArtifacts allowEmptyArchive: true, artifacts: report
    }
}
