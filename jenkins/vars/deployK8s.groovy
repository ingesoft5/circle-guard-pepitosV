/**
 * Deploys one published version to the k3s cluster of an environment with the Helm chart in
 * deploy/k8s/circleguard (HU-34).
 *
 *   deployK8s(environment: 'dev', version: '1.4.0')
 *   deployK8s(environment: 'stage', version: '1.4.0-rc.a83f21c')
 *
 * Same version rules as deployCompose: stable versions everywhere, RC versions only on stage.
 * The kubeconfig comes from the Jenkins credential kubeconfig-<environment>, the images from
 * NEXUS_REGISTRY (reached by the nodes through the VPC peering) and the smoke test goes to
 * K8S_HOST_<ENVIRONMENT>, the node address the Ingress answers on.
 *
 * helm --atomic --wait: if any rollout does not finish within the timeout the release goes back to
 * its previous revision and the stage fails, so the pipeline turns FAILED and notifies.
 *
 * After the rollout it applies the Grafana dashboards of observability/grafana (HU-40) and the
 * alert rules of observability/alerts (HU-41).
 *
 * HU-51: every call is recorded with recordDeployment, success or failure (GitHub Deployment and
 * a row in registro-ambientes.md), and the Helm revision carries the same data in its
 * description (helm history). approver says who approved it; record: false leaves the record to
 * the caller (the rollback job records once for both of its paths).
 *
 *   deployK8s(environment: 'prod', version: '1.4.0', approver: env.PROD_APPROVER)
 */
def call(Map config = [:]) {
    String environment = config.environment
    String version = config.version
    String timeout = config.timeout ?: '10m'
    String approver = (config.approver ?: 'unknown').toString()
    boolean record = config.record != false
    if (!(environment in ['dev', 'stage', 'prod'])) {
        error("deployK8s: unknown environment '${environment}'")
    }
    boolean stableVersion = semver.isVersion(version)
    boolean releaseCandidate = version ==~ /\d+\.\d+\.\d+-rc\.(?:\d+\.)?[0-9a-f]{7}/
    if (!stableVersion && !(environment == 'stage' && releaseCandidate)) {
        error("deployK8s: '${version}' is not allowed for ${environment}; only stable versions are allowed except RC versions on stage")
    }
    String host = env."K8S_HOST_${environment.toUpperCase()}"
    if (!host) {
        error("deployK8s: set K8S_HOST_${environment.toUpperCase()} in infra/compose/ci/.env")
    }
    if (!env.NEXUS_REGISTRY) {
        error('deployK8s: NEXUS_REGISTRY is not set')
    }
    String kind = config.kind ?: (releaseCandidate ? 'rc' : 'deploy')
    String description = "${kind} ${version} | approver: ${approver} | ${env.JOB_NAME ?: 'build'} #${env.BUILD_NUMBER ?: '?'}"

    String result = 'failure'
    try {
        rollout(environment, version, timeout, description)
        k8sSmokeTest(environment: environment)
        result = 'success'
    } finally {
        if (record) {
            recordDeployment(environment: environment, version: version, kind: kind,
                    approver: approver, result: result)
        }
    }
}

def rollout(String environment, String version, String timeout, String description) {
    withEnv([
        "DEPLOY_ENV=${environment}",
        "DEPLOY_NAMESPACE=circleguard-${environment}",
        "DEPLOY_VERSION=${version}",
        "DEPLOY_TIMEOUT=${timeout}",
        "DEPLOY_DESCRIPTION=${description}"
    ]) {
        withCredentials([file(credentialsId: "kubeconfig-${environment}", variable: 'KUBECONFIG')]) {
            try {
                sh '''
                    deploy/k8s/scripts/install-cert-manager.sh
                    deploy/k8s/scripts/apply-secrets.sh "$DEPLOY_ENV"
                    dns="$(deploy/k8s/scripts/node-external-ip.sh | tr -d '[:space:]')"
                    set --
                    if [ -n "$dns" ]; then
                      set -- --set "ingress.tls.dnsName=${dns}.sslip.io"
                    fi
                    helm upgrade --install circleguard deploy/k8s/circleguard \
                      --namespace "$DEPLOY_NAMESPACE" --create-namespace \
                      -f "deploy/k8s/circleguard/values-$DEPLOY_ENV.yaml" \
                      --set image.registry="$NEXUS_REGISTRY" \
                      --set image.tag="$DEPLOY_VERSION" \
                      --description "$DEPLOY_DESCRIPTION" \
                      "$@" \
                      --atomic --wait --timeout "$DEPLOY_TIMEOUT"
                    kubectl -n "$DEPLOY_NAMESPACE" wait certificate/circleguard-tls \
                      --for=condition=Ready --timeout=180s
                    kubectl -n "$DEPLOY_NAMESPACE" get pods -o wide
                    helm -n "$DEPLOY_NAMESPACE" history circleguard --max 5
                '''
            } catch (err) {
                sh '''
                    kubectl -n "$DEPLOY_NAMESPACE" get pods -o wide || true
                    kubectl -n "$DEPLOY_NAMESPACE" get events --sort-by=.lastTimestamp | tail -n 30 || true
                    helm -n "$DEPLOY_NAMESPACE" history circleguard --max 5 || true
                '''
                error("deployK8s: rollout of ${version} to ${environment} did not finish in ${timeout}; " +
                        'Helm restored the previous revision')
            }
            // Dashboards (HU-40) and alert rules (HU-41) as code: Grafana and Prometheus run in
            // the monitoring namespace of the same cluster. A cluster without the monitoring stack
            // skips the step, and a failure here marks the stage UNSTABLE instead of failing a
            // deploy that already finished.
            catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE',
                    message: 'deployK8s: the Grafana dashboards or the alert rules were not applied') {
                sh '''
                    if kubectl get namespace monitoring >/dev/null 2>&1; then
                      kubectl apply -f observability/tls/monitoring-certificate.yaml
                      kubectl apply -k observability/grafana
                      kubectl apply -k observability/alerts
                    else
                      echo "No monitoring namespace in this cluster; dashboards and alerts skipped"
                    fi
                '''
            }
        }
    }
}
