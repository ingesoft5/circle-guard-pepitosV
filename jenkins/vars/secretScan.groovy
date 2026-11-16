/**
 * Fails the build when gitleaks finds a credential in the git history.
 *
 * Local application.yml placeholders are allowlisted in .gitleaks.toml.
 * A new secret anywhere else fails the stage.
 */
def call(Map config = [:]) {
    String report = config.report ?: 'build/reports/gitleaks.sarif'
    withEnv(["GITLEAKS_REPORT=${report}"]) {
        sh '''
            mkdir -p "$(dirname "$GITLEAKS_REPORT")"
            docker run --rm \
              -v "$PWD":/repo \
              ghcr.io/gitleaks/gitleaks:v8.21.2 \
              detect \
              --source /repo \
              --config /repo/.gitleaks.toml \
              --redact \
              --exit-code 1 \
              --report-format sarif \
              --report-path "/repo/$GITLEAKS_REPORT"
        '''
    }
    archiveArtifacts allowEmptyArchive: true, artifacts: report
}
