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
    withEnv(["SMOKE_BASE_URL=http://${host}"]) {
        sh '''
            curl -fsS --retry 10 --retry-delay 6 --retry-all-errors -o /dev/null "$SMOKE_BASE_URL/healthz"
            for service in auth identity promotion form gateway; do
              curl -fsS --retry 10 --retry-delay 6 --retry-all-errors "$SMOKE_BASE_URL/svc/$service/actuator/health" \
                | grep -q '"status":"UP"' || { echo "Smoke test failed for $service"; exit 1; }
            done
            echo "Smoke test OK: $SMOKE_BASE_URL"
        '''
    }
}
