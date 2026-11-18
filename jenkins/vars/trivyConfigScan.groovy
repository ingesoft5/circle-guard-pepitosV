/**
 * Scans Terraform and Kubernetes manifests when those directories exist.
 * A HIGH or CRITICAL misconfiguration fails the build.
 *
 * Uses the same shared vulnerability cache as the image scan, and copies only
 * the directories that exist. A bind mount of the Jenkins workspace is empty.
 */
def call(Map config = [:]) {
    List<String> paths = config.paths ?: ['infra/terraform', 'deploy/k8s']
    List<String> present = paths.findAll { path -> fileExists(path) }
    if (!present) {
        echo "trivyConfigScan: aún no existen ${paths.join(' ni ')}; el escaneo de IaC queda pendiente."
        return
    }
    // withIndex() loses the index under the pipeline CPS transform, so the
    // counter stays a plain integer.
    List<String> mounts = []
    int index = 1
    for (String path : present) {
        mounts.add("/scan${index}")
        index = index + 1
    }
    String mounted = mounts.join(' ')
    String report = config.report ?: 'build/reports/trivy-config.json'
    try {
        withEnv(["TRIVY_CONFIG_PATHS=${mounted}", "TRIVY_CONFIG_REPORT=${report}", "TRIVY_HOST_PATHS=${present.join(' ')}"]) {
            sh '''
                mkdir -p "$(dirname "$TRIVY_CONFIG_REPORT")"
                exec 9>/tmp/trivy-scan.lock
                flock 9
                cid="$(docker create \
                    -v circleguard-trivy-cache:/root/.cache \
                    aquasec/trivy:0.57.1 \
                    config \
                    --exit-code 1 \
                    --severity HIGH,CRITICAL \
                    --ignorefile /trivyignore \
                    --format json \
                    --output /tmp/trivy-config.json \
                    $TRIVY_CONFIG_PATHS)"
                cleanup() { docker rm "$cid" >/dev/null 2>&1 || true; }
                trap cleanup EXIT
                docker cp .trivyignore "$cid":/trivyignore
                index=1
                for path in $TRIVY_HOST_PATHS; do
                    docker cp "$path" "$cid":/scan"$index"
                    index=$((index + 1))
                done
                rc=0
                docker start -a "$cid" || rc=$?
                docker cp "$cid":/tmp/trivy-config.json "$TRIVY_CONFIG_REPORT" || true
                exit "$rc"
            '''
        }
    } finally {
        archiveArtifacts allowEmptyArchive: true, artifacts: report
    }
}
