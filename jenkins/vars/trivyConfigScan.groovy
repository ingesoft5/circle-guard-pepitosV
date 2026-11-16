/**
 * Scans Terraform and Kubernetes manifests when those directories exist.
 * A HIGH or CRITICAL misconfiguration fails the build.
 *
 * The repository is copied into the scanner. A bind mount of the Jenkins
 * workspace is empty because the Docker daemon runs on the host.
 */
def call(Map config = [:]) {
    List<String> paths = config.paths ?: ['infra/terraform', 'deploy/k8s']
    List<String> present = paths.findAll { path -> fileExists(path) }
    if (!present) {
        echo "trivyConfigScan: aún no existen ${paths.join(' ni ')}; el escaneo de IaC queda pendiente."
        return
    }
    String mounted = present.collect { path -> "/repo/${path}" }.join(' ')
    String report = config.report ?: 'build/reports/trivy-config.json'
    try {
        withEnv(["TRIVY_CONFIG_PATHS=${mounted}", "TRIVY_CONFIG_REPORT=${report}"]) {
            sh '''
                mkdir -p "$(dirname "$TRIVY_CONFIG_REPORT")"
                cid="$(docker create aquasec/trivy:0.57.1 \
                    config \
                    --exit-code 1 \
                    --severity HIGH,CRITICAL \
                    --ignorefile /repo/.trivyignore \
                    --format json \
                    --output "/repo/$TRIVY_CONFIG_REPORT" \
                    $TRIVY_CONFIG_PATHS)"
                cleanup() { docker rm "$cid" >/dev/null 2>&1 || true; }
                trap cleanup EXIT
                docker cp . "$cid":/repo
                rc=0
                docker start -a "$cid" || rc=$?
                docker cp "$cid":"/repo/$TRIVY_CONFIG_REPORT" "$TRIVY_CONFIG_REPORT" || true
                exit "$rc"
            '''
        }
    } finally {
        archiveArtifacts allowEmptyArchive: true, artifacts: report
    }
}
