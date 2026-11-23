/**
 * OWASP ZAP baseline against the stage ingress.
 *
 * Runs after a stage deploy. High alerts fail the build. A High finding may be
 * ignored only by a line in tests/security-zap/rules.tsv. WARN does not fail (-I).
 *
 * Jenkins talks to the host Docker daemon, so a bind mount of the workspace is
 * empty. The rules file is copied in and the reports are copied out.
 */
def call() {
    String host = (env.K8S_HOST_STAGE ?: env.DEPLOY_HOST_STAGE ?: '').trim()
    if (!host) {
        echo 'zapBaseline: no stage host is set, skipping.'
        return
    }
    String target = host.startsWith('http') ? host : "http://${host}"

    sh 'mkdir -p build/reports/zap'
    try {
        withEnv(["ZAP_TARGET=${target}"]) {
            sh '''
                cid="$(docker create --network host ghcr.io/zaproxy/zaproxy:stable \
                    zap-baseline.py -t "$ZAP_TARGET" -c rules.tsv \
                        -r zap-baseline.html -J zap-baseline.json -I)"
                cleanup() { docker rm "$cid" >/dev/null 2>&1 || true; }
                trap cleanup EXIT
                # Recent zaproxy images no longer ship /zap/wrk. Stream it in as a tar owned
                # by the zap user (1000), so ZAP can write its reports there.
                wrk="$(mktemp -d)"
                mkdir "$wrk/wrk"
                cp tests/security-zap/rules.tsv "$wrk/wrk/rules.tsv"
                tar -C "$wrk" --owner=1000 --group=1000 -cf - wrk | docker cp -a - "$cid":/zap
                rm -rf "$wrk"
                rc=0
                docker start -a "$cid" || rc=$?
                docker cp "$cid":/zap/wrk/zap-baseline.html build/reports/zap/zap-baseline.html || true
                docker cp "$cid":/zap/wrk/zap-baseline.json build/reports/zap/zap-baseline.json || true
                exit "$rc"
            '''
        }
    } finally {
        archiveArtifacts allowEmptyArchive: true, artifacts: 'build/reports/zap/**'
    }
}
