/**
 * Publishes a Trivy vulnerability report for Gradle and npm dependencies.
 * The report is informational: image scanning is the build-breaking gate.
 */
def call(Map config = [:]) {
    String report = config.report ?: 'build/reports/trivy-fs.json'
    withEnv(["TRIVY_FS_REPORT=${report}"]) {
        sh '''
            mkdir -p "$(dirname "$TRIVY_FS_REPORT")"
            docker run --rm \
              -v "$PWD":/repo \
              aquasec/trivy:0.57.1 fs \
              --scanners vuln \
              --severity CRITICAL,HIGH \
              --exit-code 0 \
              --format json \
              --output "/repo/$TRIVY_FS_REPORT" \
              /repo
        '''
    }
    archiveArtifacts allowEmptyArchive: true, artifacts: report
}
