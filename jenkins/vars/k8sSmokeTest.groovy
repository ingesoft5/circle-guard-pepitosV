/**
 * Smoke test of one environment through its Ingress, the same checks as deploy/compose/deploy.sh:
 * the web client answers and every core service reports UP behind /svc/<service>/.
 *
 *   k8sSmokeTest(environment: 'stage')
 *
 * The address comes from K8S_HOST_<ENVIRONMENT>.
 */
def call(Map config = [:]) {
    String environment = config.environment
    String host = env."K8S_HOST_${(environment ?: '').toUpperCase()}"
    if (!host) {
        error("k8sSmokeTest: set K8S_HOST_${(environment ?: '').toUpperCase()} in infra/compose/ci/.env")
    }
    // HTTPS: port 80 answers every path with a 301 to HTTPS (TLS, HU-47), so over HTTP the
    // health body is "Moved Permanently". -k because the certificate comes from the lab CA of
    // cert-manager and is issued for <ip>.sslip.io, not for the private address used here.
    withEnv(["SMOKE_BASE_URL=https://${host}"]) {
        sh '''
            curl -kfsS --retry 10 --retry-delay 6 --retry-all-errors -o /dev/null "$SMOKE_BASE_URL/healthz"
            for service in auth identity promotion form gateway; do
              curl -kfsS --retry 10 --retry-delay 6 --retry-all-errors "$SMOKE_BASE_URL/svc/$service/actuator/health" \
                | grep -q '"status":"UP"' || { echo "Smoke test failed for $service"; exit 1; }
            done
            echo "Smoke test OK: $SMOKE_BASE_URL"
        '''
    }
}
