/**
 * Deploys one published version to an environment host over SSH.
 *
 *   deployCompose(environment: 'dev', version: '1.4.0')
 *
 * Copies deploy/compose to the host, keeping its .env file, and runs deploy.sh there.
 * The host comes from the global variable DEPLOY_HOST_<ENVIRONMENT> and the SSH key from
 * the Jenkins credential deploy-ssh-<environment>.
 */
def call(Map config = [:]) {
    String environment = config.environment
    String version = config.version
    if (!(environment in ['dev', 'stage', 'prod'])) {
        error("deployCompose: unknown environment '${environment}'")
    }
    if (!(version ==~ /\d+\.\d+\.\d+/)) {
        error("deployCompose: '${version}' is not a MAJOR.MINOR.PATCH version")
    }
    String host = env."DEPLOY_HOST_${environment.toUpperCase()}"
    if (!host) {
        error("deployCompose: set DEPLOY_HOST_${environment.toUpperCase()} in infra/compose/ci/.env")
    }

    withEnv([
        "DEPLOY_TARGET=deploy@${host}",
        "DEPLOY_ENV=${environment}",
        "DEPLOY_VERSION=${version}",
        'DEPLOY_ROOT=/opt/circleguard',
        'SSH_OPTS=-o StrictHostKeyChecking=accept-new -o ConnectTimeout=15'
    ]) {
        sshagent(credentials: ["deploy-ssh-${environment}"]) {
            sh '''
                tar czf - --exclude=.env deploy/compose \
                  | ssh $SSH_OPTS "$DEPLOY_TARGET" "tar xzf - -C $DEPLOY_ROOT"
                ssh $SSH_OPTS "$DEPLOY_TARGET" \
                  "$DEPLOY_ROOT/deploy/compose/deploy.sh $DEPLOY_ENV $DEPLOY_VERSION"
            '''
        }
    }
}
