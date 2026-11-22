/**
 * Runs terraform init + plan for one environment (HU-32) and stashes the saved plan.
 *
 *   terraformPlan(environment: 'dev')
 *
 * The AWS credentials, the observability SSH key and the Grafana admin password come from
 * Jenkins credentials. The SSH host is the node private IP (K8S_HOST_<ENV>) because the
 * Security Group only opens port 22 to the CI private IP.
 *
 * Returns [hasChanges: boolean, planFile: String]. The plan file is not archived: it can
 * contain sensitive variable values.
 */
def call(Map config = [:]) {
    String environment = config.environment
    String planFile = (config.planFile ?: "tfplan-${environment}").toString()
    String awsCredentialsId = (config.awsCredentialsId ?: 'aws-terraform').toString()
    String sshCredentialsId = (config.sshCredentialsId ?: 'prometheus-ssh-key').toString()
    String grafanaCredentialsId = (config.grafanaCredentialsId ?: 'prometheus-grafana-password').toString()

    if (!(environment in ['dev', 'stage', 'prod'])) {
        error("terraformPlan: unknown environment '${environment}'")
    }

    String sshHost = (config.sshHost ?: env."K8S_HOST_${environment.toUpperCase()}")?.toString()?.trim()
    if (!sshHost) {
        error("terraformPlan: set K8S_HOST_${environment.toUpperCase()} or pass sshHost")
    }
    String region = (config.region ?: env.AWS_REGION ?: 'us-east-1').toString()
    Map result = null

    dir("infra/terraform/envs/${environment}") {
        withCredentials([
            file(credentialsId: awsCredentialsId, variable: 'AWS_SHARED_CREDENTIALS_FILE'),
            sshUserPrivateKey(credentialsId: sshCredentialsId, keyFileVariable: 'PROMETHEUS_SSH_KEY_FILE'),
            string(credentialsId: grafanaCredentialsId, variable: 'PROMETHEUS_GRAFANA_PASSWORD')
        ]) {
            withEnv([
                'AWS_PROFILE=default',
                "AWS_REGION=${region}",
                "AWS_DEFAULT_REGION=${region}",
                'TF_IN_AUTOMATION=1',
                "PROMETHEUS_SSH_HOST=${sshHost}",
                "PLAN_FILE=${planFile}"
            ]) {
                int rc = sh(script: '''
                    set -euo pipefail
                    export TF_VAR_prometheus_ssh_host="$PROMETHEUS_SSH_HOST"
                    export TF_VAR_prometheus_ssh_private_key="$(cat "$PROMETHEUS_SSH_KEY_FILE")"
                    export TF_VAR_prometheus_grafana_admin_password="$PROMETHEUS_GRAFANA_PASSWORD"
                    terraform init -input=false -reconfigure -backend-config=backend.hcl
                    set +e
                    terraform plan -input=false -no-color -lock-timeout=5m \
                        -detailed-exitcode -out="$PLAN_FILE"
                    rc=$?
                    set -e
                    exit $rc
                ''', returnStatus: true)

                if (!(rc in [0, 2])) {
                    error("terraformPlan: plan failed in ${environment} (exit ${rc})")
                }
                stash name: "terraform-${environment}",
                      includes: "infra/terraform/envs/${environment}/${planFile}"
                echo "terraformPlan: ${environment} ${rc == 2 ? 'has changes' : 'has no changes'}"
                result = [hasChanges: rc == 2, planFile: planFile]
            }
        }
    }
    return result
}
