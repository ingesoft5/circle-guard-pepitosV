/**
 * Applies the saved Terraform plan of one environment (HU-32).
 *
 *   terraformApply(environment: 'dev')
 *
 * The plan was created by terraformPlan() and stashed with the name terraform-<env>.
 * It is applied as-is: if the state changed after the plan, Terraform fails with
 * "Saved plan is stale" instead of applying something nobody reviewed.
 */
def call(Map config = [:]) {
    String environment = config.environment
    String planFile = (config.planFile ?: "tfplan-${environment}").toString()
    String awsCredentialsId = (config.awsCredentialsId ?: 'aws-terraform').toString()
    String sshCredentialsId = (config.sshCredentialsId ?: 'prometheus-ssh-key').toString()
    String grafanaCredentialsId = (config.grafanaCredentialsId ?: 'prometheus-grafana-password').toString()

    if (!(environment in ['dev', 'stage', 'prod'])) {
        error("terraformApply: unknown environment '${environment}'")
    }

    String sshHost = (config.sshHost ?: env."K8S_HOST_${environment.toUpperCase()}")?.toString()?.trim()
    if (!sshHost) {
        error("terraformApply: set K8S_HOST_${environment.toUpperCase()} or pass sshHost")
    }
    String region = (config.region ?: env.AWS_REGION ?: 'us-east-1').toString()

    unstash "terraform-${environment}"
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
                sh '''
                    set -euo pipefail
                    export TF_VAR_prometheus_ssh_host="$PROMETHEUS_SSH_HOST"
                    export TF_VAR_prometheus_ssh_private_key="$(cat "$PROMETHEUS_SSH_KEY_FILE")"
                    export TF_VAR_prometheus_grafana_admin_password="$PROMETHEUS_GRAFANA_PASSWORD"
                    terraform init -input=false -reconfigure -backend-config=backend.hcl
                    terraform apply -input=false -auto-approve -lock-timeout=5m "$PLAN_FILE"
                '''
            }
        }
    }
    echo "terraformApply: ${environment} applied"
}
