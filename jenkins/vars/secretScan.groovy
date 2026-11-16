/**
 * Fails the build when gitleaks finds a credential in the git history.
 *
 * Jenkins runs inside a container and talks to the host Docker daemon, so a bind
 * mount of the workspace is empty on the host. The repository is copied into the
 * scanner, the same way the shared-library syntax check does it.
 *
 * Local application.yml placeholders are allowlisted in .gitleaks.toml.
 * A new secret anywhere else fails the stage.
 */
def call(Map config = [:]) {
    String report = config.report ?: 'build/reports/gitleaks.sarif'
    try {
        withEnv(["GITLEAKS_REPORT=${report}"]) {
            sh '''
                mkdir -p "$(dirname "$GITLEAKS_REPORT")"
                cid="$(docker create ghcr.io/gitleaks/gitleaks:v8.21.2 \
                    detect \
                    --source /repo \
                    --config /repo/.gitleaks.toml \
                    --redact \
                    --exit-code 1 \
                    --report-format sarif \
                    --report-path "/repo/$GITLEAKS_REPORT")"
                cleanup() { docker rm "$cid" >/dev/null 2>&1 || true; }
                trap cleanup EXIT
                docker cp . "$cid":/repo
                rc=0
                docker start -a "$cid" || rc=$?
                docker cp "$cid":"/repo/$GITLEAKS_REPORT" "$GITLEAKS_REPORT" || true
                exit "$rc"
            '''
        }
    } finally {
        archiveArtifacts allowEmptyArchive: true, artifacts: report
    }
}
