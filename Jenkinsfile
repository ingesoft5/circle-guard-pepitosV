// The shared library lives in this repository, so each build loads it from the branch it
// builds (the source branch for a PR). A change to jenkins/vars/ is used by its own PR, with
// no pinned version to move in casc.yaml.
library "circleguard-shared-library@${env.CHANGE_BRANCH ?: env.BRANCH_NAME}"

def SERVICES = [
    'circleguard-auth-service',
    'circleguard-identity-service',
    'circleguard-promotion-service',
    'circleguard-notification-service',
    'circleguard-form-service',
    'circleguard-file-service',
    'circleguard-gateway-service',
    'circleguard-dashboard-service'
]

// Set by 'Release version' (HU-15) on main; promote, tag and deploy stages need it.
def releaseVersion = null
// Set by 'Resolve version': SNAPSHOT on dev, release candidate on a dev -> main promotion PR.
def prereleaseVersion = null
// HU-51: who approved each deploy, for the deployment record. On main it is the author of the
// merge commit (who merged the promotion PR); prod uses the 'Approve production' answer.
def mergeAuthor = null

// PR dev -> main: builds the release candidate that main later promotes to stable.
def promotionPr = env.CHANGE_ID != null && env.CHANGE_TARGET == 'main' && env.CHANGE_BRANCH == 'dev' && env.CHANGE_FORK == null

def changed = [services: [], web: false, all: false, terraform: false, base: '']
// Set by 'Terraform plan': hasChanges and planFile per environment (HU-32).
def terraformChanges = [:]
// A branch whose commit is already in dev (its PR was merged) is not built again.
def alreadyMerged = false

// Set when a service or the web client fails; used by the Discord notification.
def failedService = null
def failedStage = null

pipeline {
    // The controller (CI) orchestrates: checkout, scans, plans, deploys and approvals. The heavy
    // lanes of 'Build, test and package' run on the build workers.
    agent { label 'controller' }

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
        timeout(time: 120, unit: 'MINUTES')
        skipDefaultCheckout(true)
    }

    parameters {
        booleanParam(
            name: 'BUILD_ALL',
            defaultValue: false,
            description: 'Compila, prueba y empaqueta los 8 servicios y el cliente web'
        )
    }

    environment {
        // Shared by every branch: the workspaces are wiped after each build.
        GRADLE_USER_HOME = "${JENKINS_HOME}/gradle-cache"
        // Gradle runs with its daemon: each call reuses a warm JVM instead of paying 20 to 40 s
        // of startup and configuration (three calls per lane). gradle.properties stops an idle
        // daemon after 30 minutes; the helpers keep Jenkins from killing it after each step.
        GRADLE_OPTS = '-Dorg.gradle.jvmargs=-Xmx1g'
        PR_MAX_ADDITIONS = '700'
        PR_MAX_DELETIONS = '300'
        PR_MAX_CHANGED_LINES = '1000'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'chmod +x gradlew'
                sh 'find services -path "*/build/test-results" -type d -prune -exec rm -rf {} +'
                script {
                    alreadyMerged = alreadyMergedBranch()
                    if (alreadyMerged) {
                        currentBuild.description = 'Skipped: already merged into dev'
                    }
                }
            }
        }

        stage('Secret scan') {
            when { expression { !alreadyMerged } }
            steps {
                script {
                    def currentStage = 'Secret scan'
                    try {
                        secretScan()
                    } catch (Exception e) {
                        failedStage = currentStage
                        throw e
                    }
                }
            }
        }

        stage('PR size policy') {
            steps {
                script {
                    validatePrSize()
                }
            }
        }

        // With no changes detected every later stage is skipped by its own condition.
        stage('Detect changes') {
            when { expression { !alreadyMerged } }
            steps {
                script {
                    changed = detectChangedServices(
                        buildAll: (params.BUILD_ALL ?: false) || promotionPr || ((env.BRANCH_NAME ?: '') ==~ /release\/.+/) || ((env.CHANGE_BRANCH ?: '') ==~ /release\/.+/),
                        services: SERVICES
                    )
                    // Terraform has its own base: the PR target, or HEAD^1 on main. The
                    // services diff falls back to "all" too often to drive an apply.
                    changed.terraform = detectTerraformChanges()
                    echo "Base del diff: ${changed.base ?: 'sin base'}"
                    echo "Servicios seleccionados: ${changed.services ?: 'ninguno'}"
                    echo "Cliente web: ${changed.web}"
                    echo "Cambios de Terraform: ${changed.terraform}"
                }
            }
        }

        stage('Resolve version') {
            when {
                expression {
                    promotionPr ||
                        (env.BRANCH_NAME == 'dev' && (!changed.services.isEmpty() || changed.web)) ||
                        ((env.BRANCH_NAME ?: '') ==~ /release\/.+/) ||
                        ((env.CHANGE_BRANCH ?: '') ==~ /release\/.+/)
                }
            }
            steps {
                script {
                    try {
                        prereleaseVersion = resolvePrereleaseVersion()
                        if (prereleaseVersion) {
                            currentBuild.description = prereleaseVersion
                            echo "Versión de imágenes: ${prereleaseVersion}"
                        } else {
                            echo 'Sin release candidate: la promoción no incluye feat, fix, perf ni cambios incompatibles.'
                        }
                    } catch (Exception e) {
                        failedService = 'shared-library'
                        failedStage = 'Resolve version'
                        throw e
                    }
                }
            }
        }

        // The checks of the controller (dependency and IaC scans, library validation, Terraform
        // plans) run alongside the worker lanes instead of before them. failFast: the first
        // failure, in a check or a lane, stops the rest.
        stage('Checks, build and package') {
            failFast true
            parallel {
                stage('Static checks') {
                    stages {
                stage('Dependency scan') {
                    when { expression { !alreadyMerged } }
                    steps {
                        script {
                            trivyFsScan()
                        }
                    }
                }

                // The syntax check runs in a throwaway container fed with docker cp: Jenkins runs in a
                // container with docker.sock, so a -v of the workspace would be resolved on the host.
                stage('Validate shared library') {
                    when {
                        allOf {
                            changeRequest()
                            expression { changed.all }
                        }
                    }
                    steps {
                        writeFile file: '.check-vars.groovy', text: '''
                            import org.codehaus.groovy.control.*
                            def failed = false
                            new File("/vars").listFiles().findAll { it.name.endsWith(".groovy") }.each { f ->
                                try {
                                    def cu = new CompilationUnit()
                                    cu.addSource(f)
                                    cu.compile(Phases.CONVERSION)
                                } catch (Exception e) {
                                    println "ERROR en " + f.name + ": " + e.message
                                    failed = true
                                }
                            }
                            System.exit(failed ? 1 : 0)
                        '''
                        sh '''
                            cid="$(docker create groovy:4.0-jdk21 groovy /check.groovy)"
                            docker cp .check-vars.groovy "$cid":/check.groovy
                            docker cp jenkins/vars "$cid":/vars
                            rc=0
                            docker start -a "$cid" || rc=$?
                            docker rm "$cid" >/dev/null
                            rm -f .check-vars.groovy
                            exit $rc
                        '''
                    }
                }

                stage('IaC scan') {
                    when { expression { !alreadyMerged } }
                    steps {
                        script {
                            trivyConfigScan()
                        }
                    }
                }

                // HU-32: a PR that changes infra/terraform gets the three plans in its build log.
                stage('Terraform plan (PR)') {
                    when {
                        allOf {
                            changeRequest()
                            expression { changed.terraform }
                        }
                    }
                    steps {
                        script {
                            terraformPlan(environment: 'dev')
                            terraformPlan(environment: 'stage')
                            terraformPlan(environment: 'prod')
                        }
                    }
                }
                    }
                }

                // Heavy work runs on the build workers (label 'worker'), one lane per component, all in
                // parallel: each worker takes as many lanes as it has executors and the rest queue. A
                // lane is self-contained (checkout, compile, test, Sonar, image), so no JAR moves between
                // machines. The controller only orchestrates, deploys and approves.
                stage('Build, test and package') {
                    steps {
                        script {
                            boolean publish = prereleaseVersion != null
                            boolean packageOnly = !publish && env.BRANCH_NAME != 'main'
                            // SNAPSHOT publishes only the affected components. A promotion PR (or a
                            // release branch) publishes the full set: the RC is what main promotes.
                            String sourceBranch = env.CHANGE_BRANCH ?: env.BRANCH_NAME
                            boolean onlyChanged = !promotionPr && sourceBranch == 'dev'
                            List<String> buildServices = publish && !onlyChanged ? SERVICES : changed.services
                            boolean buildWeb = publish && !onlyChanged ? true : changed.web

                            def lanes = [:]
                            buildServices.each { service ->
                                lanes[service] = {
                                    node('worker') {
                                        def currentStage = "Compile ${service}"
                                        try {
                                            checkout scm
                                            sh 'chmod +x gradlew'
                                            stage(currentStage) {
                                                gradleBuild(service: service)
                                            }
                                            currentStage = "Test ${service}"
                                            stage(currentStage) {
                                                runTests(service: service, publish: true)
                                                publishCoverage(services: [service])
                                            }
                                            // The scanner waits for the quality gate itself and fails the
                                            // lane when it is red, so parallel lanes never read each
                                            // other's analysis (waitForQualityGate takes the last one).
                                            currentStage = "Sonar ${service}"
                                            stage(currentStage) {
                                                sonarScan(
                                                    service: service,
                                                    extraArgs: '-Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=600'
                                                )
                                            }
                                            if (publish) {
                                                currentStage = "Publish ${service}"
                                                stage(currentStage) {
                                                    dockerPublish(service: service, version: prereleaseVersion)
                                                }
                                            } else if (packageOnly) {
                                                currentStage = "Package ${service}"
                                                stage(currentStage) {
                                                    dockerBuild(service: service)
                                                }
                                            }
                                        } catch (Exception e) {
                                            if (!failedService) {
                                                failedService = service
                                                failedStage = currentStage
                                            }
                                            throw e
                                        } finally {
                                            cleanWs()
                                        }
                                    }
                                }
                            }

                            if (buildWeb && (publish || packageOnly)) {
                                lanes['web-client'] = {
                                    node('worker') {
                                        String currentStage = publish ? 'Publish web client' : 'Package web client'
                                        try {
                                            checkout scm
                                            stage(currentStage) {
                                                if (publish) {
                                                    dockerPublish(image: 'web-client', dockerfile: 'mobile/Dockerfile', context: 'mobile', version: prereleaseVersion)
                                                } else {
                                                    dockerBuild(image: 'web-client', dockerfile: 'mobile/Dockerfile', context: 'mobile')
                                                }
                                            }
                                        } catch (Exception e) {
                                            if (!failedService) {
                                                failedService = 'web-client'
                                                failedStage = currentStage
                                            }
                                            throw e
                                        } finally {
                                            cleanWs()
                                        }
                                    }
                                }
                            }

                            boolean integration = changed.all || changed.services.any { service ->
                                service in ['circleguard-auth-service', 'circleguard-form-service',
                                            'circleguard-promotion-service', 'circleguard-gateway-service']
                            }
                            if (integration) {
                                lanes['integration-tests'] = {
                                    node('worker') {
                                        try {
                                            checkout scm
                                            sh 'chmod +x gradlew'
                                            stage('Integration tests') {
                                                runTests(integration: true, coverage: false, publish: true)
                                            }
                                        } catch (Exception e) {
                                            if (!failedService) {
                                                failedService = 'integration-tests'
                                                failedStage = 'Integration tests'
                                            }
                                            throw e
                                        } finally {
                                            cleanWs()
                                        }
                                    }
                                }
                            }

                            // The longest lanes ask for a worker first, so the short ones fill the gaps
                            // at the end instead of a long one starting last (measured test times:
                            // promotion 3.5 min, identity 1.7, notification 1.5, the rest 1.1 to 1.3).
                            def priority = ['integration-tests', 'web-client',
                                            'circleguard-promotion-service', 'circleguard-identity-service',
                                            'circleguard-notification-service', 'circleguard-gateway-service',
                                            'circleguard-form-service']
                            def ordered = [:]
                            priority.each { name ->
                                if (lanes.containsKey(name)) {
                                    ordered[name] = lanes[name]
                                }
                            }
                            lanes.each { name, lane ->
                                if (!ordered.containsKey(name)) {
                                    ordered[name] = lane
                                }
                            }
                            if (lanes) {
                                // The first failed lane stops the others.
                                ordered.failFast = true
                                parallel ordered
                            } else {
                                echo 'No hay servicios ni cliente web modificados.'
                            }
                        }
                    }
                }
            }
        }

        // Before the merge: checks that main will be able to find this RC (read only).
        stage('Verify promotion path') {
            when { expression { promotionPr && prereleaseVersion != null } }
            steps {
                script {
                    try {
                        promoteRelease(
                            version: prereleaseVersion.substring(0, prereleaseVersion.indexOf('-rc.')),
                            services: SERVICES,
                            dryRun: true
                        )
                    } catch (Exception e) {
                        failedService = 'registry'
                        failedStage = 'Verify promotion path'
                        throw e
                    }
                }
            }
        }

        // The release candidate comes from the dev -> main promotion PR. Its Jenkins check stays
        // pending until RC publication, stage deployment and E2E pass.
        stage('Deploy RC to stage') {
            when {
                expression {
                    prereleaseVersion != null &&
                        (promotionPr || (((env.CHANGE_BRANCH ?: env.BRANCH_NAME) ?: '') ==~ /release\/\d+\.\d+\.\d+/))
                }
            }
            steps {
                script {
                    deployK8s(
                        environment: 'stage',
                        version: prereleaseVersion,
                        approver: env.CHANGE_ID ?
                                "automatic: PR #${env.CHANGE_ID} by ${env.CHANGE_AUTHOR ?: 'unknown'}" :
                                "automatic: ${env.BRANCH_NAME}"
                    )
                }
            }
        }

        stage('E2E tests on stage') {
            when {
                expression {
                    prereleaseVersion != null &&
                        (promotionPr || (((env.CHANGE_BRANCH ?: env.BRANCH_NAME) ?: '') ==~ /release\/\d+\.\d+\.\d+/))
                }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'e2e-user',
                    usernameVariable: 'E2E_USERNAME',
                    passwordVariable: 'E2E_PASSWORD'
                )]) {
                    withEnv(["E2E_BASE_URL=https://${env.K8S_HOST_STAGE}"]) {
                        sh 'bash tests/e2e-playwright/run-e2e.sh'
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                        testResults: 'tests/e2e-playwright/results/junit.xml'
                    archiveArtifacts allowEmptyArchive: true,
                        artifacts: 'tests/e2e-playwright/results/**'
                }
                failure {
                    script {
                        failedService = failedService ?: 'web-client'
                        failedStage = failedStage ?: 'E2E tests on stage'
                    }
                }
            }
        }

        stage('ZAP baseline on stage') {
            when {
                expression {
                    prereleaseVersion != null &&
                        (promotionPr || (((env.CHANGE_BRANCH ?: env.BRANCH_NAME) ?: '') ==~ /release\/\d+\.\d+\.\d+/))
                }
            }
            steps {
                zapBaseline()
            }
            post {
                failure {
                    script { failedStage = failedStage ?: 'ZAP baseline on stage' }
                }
            }
        }

        stage('Release version') {
            when {
                branch 'main'
            }
            steps {
                script {
                    def release = calculateVersion()
                    if (!release) {
                        echo 'Sin commits feat, fix, perf ni cambios incompatibles desde el último release: ' +
                                'no se publica una versión nueva.'
                        return
                    }
                    releaseVersion = release.version
                    currentBuild.description = "v${releaseVersion}"
                    mergeAuthor = sh(script: "git log -1 --format='%an'", returnStdout: true).trim() ?: 'unknown'
                }
            }
        }

        // The stable images are not rebuilt: each one is the release candidate that passed stage
        // and E2E in the promotion PR, published under the stable tag with the same digest.
        stage('Promote release candidate') {
            when {
                allOf {
                    branch 'main'
                    expression { releaseVersion != null }
                }
            }
            steps {
                script {
                    try {
                        promoteRelease(version: releaseVersion, services: SERVICES)
                    } catch (Exception e) {
                        failedService = 'registry'
                        failedStage = 'Promote release candidate'
                        throw e
                    }
                    sh 'docker image prune -f && docker builder prune -f --filter until=24h'
                }
            }
        }

        // After the images: a vX.Y.Z tag on GitHub always means its images exist.
        stage('Tag release') {
            when {
                allOf {
                    branch 'main'
                    expression { releaseVersion != null }
                }
            }
            steps {
                pushReleaseTag(version: releaseVersion)
            }
        }

        // HU-32: a merge to main plans the three environments and applies dev, then stage.
        // The plan file is applied as-is; a stale plan fails instead of drifting.
        stage('Terraform plan (main)') {
            when {
                allOf {
                    branch 'main'
                    expression { changed.terraform }
                }
            }
            steps {
                script {
                    terraformChanges.dev = terraformPlan(environment: 'dev')
                    terraformChanges.stage = terraformPlan(environment: 'stage')
                    terraformChanges.prod = terraformPlan(environment: 'prod')
                }
            }
        }

        stage('Terraform apply dev') {
            when {
                allOf {
                    branch 'main'
                    expression { changed.terraform && terraformChanges.dev?.hasChanges }
                }
            }
            steps {
                terraformApply(environment: 'dev')
            }
        }

        stage('Terraform apply stage') {
            when {
                allOf {
                    branch 'main'
                    expression { changed.terraform && terraformChanges.stage?.hasChanges }
                }
            }
            steps {
                terraformApply(environment: 'stage')
            }
        }

        stage('Deploy to dev') {
            when {
                allOf {
                    branch 'main'
                    expression { releaseVersion != null }
                }
            }
            steps {
                deployK8s(environment: 'dev', version: releaseVersion, approver: "automatic: merge by ${mergeAuthor}")
            }
        }

        // HU-22: GitHub Release with the notes, and CHANGELOG.md through a PR to dev
        // (main and dev are protected, so the pipeline never pushes to them directly).
        // Before the E2E tests: the tag and the images already exist, so the release
        // is documented even if the E2E tests fail.
        stage('Release notes') {
            when {
                allOf {
                    branch 'main'
                    expression { releaseVersion != null }
                }
            }
            steps {
                script {
                    String notes = releaseNotes(version: releaseVersion)
                    publishGithubRelease(version: releaseVersion, notes: notes)
                    openChangelogPr(version: releaseVersion, changelog: releaseNotes.changelog())
                }
            }
        }

        stage('E2E tests on dev') {
            when {
                allOf {
                    branch 'main'
                    expression { releaseVersion != null }
                }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'e2e-user',
                    usernameVariable: 'E2E_USERNAME',
                    passwordVariable: 'E2E_PASSWORD'
                )]) {
                    withEnv(["E2E_BASE_URL=https://${env.K8S_HOST_DEV}"]) {
                        sh 'bash tests/e2e-playwright/run-e2e.sh'
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                        testResults: 'tests/e2e-playwright/results/junit.xml'
                    archiveArtifacts allowEmptyArchive: true,
                        artifacts: 'tests/e2e-playwright/results/**'
                }
                failure {
                    script {
                        failedService = failedService ?: 'web-client'
                        failedStage = failedStage ?: 'E2E tests on dev'
                    }
                }
            }
        }

        stage('Deploy to stage') {
            when {
                allOf {
                    branch 'main'
                    expression { releaseVersion != null }
                }
            }
            steps {
                deployK8s(environment: 'stage', version: releaseVersion, approver: "automatic: merge by ${mergeAuthor}")
            }
        }

        stage('ZAP baseline after stage deploy') {
            when {
                allOf {
                    branch 'main'
                    expression { releaseVersion != null }
                }
            }
            steps {
                zapBaseline()
            }
            post {
                failure {
                    script { failedStage = failedStage ?: 'ZAP baseline after stage deploy' }
                }
            }
        }

        stage('Approve production') {
            when {
                allOf {
                    branch 'main'
                    expression { releaseVersion != null }
                }
            }
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    script {
                        env.PROD_APPROVER = input(
                            message: "Deploy v${releaseVersion} to prod",
                            ok: 'Deploy to prod',
                            // Only the admin account approves prod (docs/release-notes/politica-cambios.md)
                            submitter: 'admin',
                            submitterParameter: 'APPROVER'
                        )
                        echo "Production deployment approved by ${env.PROD_APPROVER}"
                    }
                }
            }
        }

        // HU-32: the prod Terraform plan needs its own admin approval. The application
        // approval above is a separate decision.
        stage('Approve Terraform prod') {
            when {
                allOf {
                    branch 'main'
                    expression { changed.terraform && terraformChanges.prod?.hasChanges }
                }
            }
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    script {
                        env.TERRAFORM_PROD_APPROVER = input(
                            message: 'Apply the saved Terraform plan to prod',
                            ok: 'Apply to prod',
                            submitter: 'admin',
                            submitterParameter: 'APPROVER'
                        )
                        echo "Terraform prod approved by ${env.TERRAFORM_PROD_APPROVER}"
                    }
                }
            }
        }

        stage('Terraform apply prod') {
            when {
                allOf {
                    branch 'main'
                    expression { changed.terraform && terraformChanges.prod?.hasChanges }
                }
            }
            steps {
                terraformApply(environment: 'prod')
            }
        }

        stage('Deploy to prod') {
            when {
                allOf {
                    branch 'main'
                    expression { releaseVersion != null }
                }
            }
            steps {
                deployK8s(environment: 'prod', version: releaseVersion, approver: env.PROD_APPROVER)
            }
        }
    }

    post {
        failure {
            notifyDiscord(
                status: 'FAILED',
                service: failedService ?: 'N/A',
                stage: failedStage ?: env.STAGE_NAME ?: 'unknown'
            )
            echo 'Pipeline failed. Review the stage logs and published test results.'
        }
        unstable {
            notifyDiscord(
                status: 'UNSTABLE',
                service: failedService ?: 'N/A',
                stage: failedStage ?: env.STAGE_NAME ?: 'unknown'
            )
        }
        success {
            script {
                if (currentBuild.previousBuild?.result in ['FAILURE', 'UNSTABLE']) {
                    notifyDiscord(
                        status: 'RECOVERED',
                        service: 'N/A',
                        stage: 'N/A'
                    )
                }
            }
        }
        cleanup {
            cleanWs()
        }
    }
}
