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

// PR dev -> main: builds the release candidate that main later promotes to stable.
def promotionPr = env.CHANGE_ID != null && env.CHANGE_TARGET == 'main' && env.CHANGE_BRANCH == 'dev' && env.CHANGE_FORK == null

def changed = [services: [], web: false, all: false, base: '']
// A branch whose commit is already in dev (its PR was merged) is not built again.
def alreadyMerged = false

// Set when a service or the web client fails; used by the Discord notification.
def failedService = null
def failedStage = null

pipeline {
    agent any

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
        GRADLE_OPTS = '-Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx1g'
        MAX_PARALLEL_SERVICES = '3'
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
                    echo "Base del diff: ${changed.base ?: 'sin base'}"
                    echo "Servicios seleccionados: ${changed.services ?: 'ninguno'}"
                    echo "Cliente web: ${changed.web}"
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

        stage('Build and test') {
            steps {
                script {
                    int maxParallel = (env.MAX_PARALLEL_SERVICES ?: '3') as int
                    if (maxParallel < 1) {
                        maxParallel = 1
                    }

                    if (!changed.services && !changed.web) {
                        echo 'No hay servicios ni cliente web modificados.'
                    }

                    def services = changed.services
                    for (int i = 0; i < services.size(); i += maxParallel) {
                        int end = i + maxParallel
                        if (end > services.size()) {
                            end = services.size()
                        }
                        def batch = []
                        for (int j = i; j < end; j++) {
                            batch.add(services[j])
                        }
                        def branches = [:]
                        batch.each { service ->
                            branches[service] = {
                                def currentStage = "Compile ${service}"
                                try {
                                    stage(currentStage) {
                                        gradleBuild(service: service)
                                    }
                                    currentStage = "Test ${service}"
                                    stage(currentStage) {
                                        runTests(service: service, publish: false)
                                    }
                                } catch (Exception e) {
                                    if (!failedService) {
                                        failedService = service
                                        failedStage = currentStage
                                    }
                                    throw e
                                }
                            }
                        }
                        parallel branches
                    }

                }
            }
        }

        stage('Sonar analysis and quality gate') {
            when {
                expression { !changed.services.isEmpty() }
            }
            steps {
                script {
                    for (String service : changed.services) {
                        def currentStage = "Sonar ${service}"
                        try {
                            stage(currentStage) {
                                sonarScan(service: service)
                            }

                            currentStage = "Quality gate ${service}"
                            stage(currentStage) {
                                timeout(time: 10, unit: 'MINUTES') {
                                    waitForQualityGate abortPipeline: true
                                }
                            }
                        } catch (Exception e) {
                            failedService = service
                            failedStage = currentStage
                            throw e
                        }
                    }
                }
            }
        }

        // Proves that the images build when nothing is published afterwards (PRs to dev).
        // Where images are published, dockerPublish already builds them; on main they are
        // promoted from the release candidate, so building them here would be wasted work.
        stage('Package images') {
            when {
                expression { prereleaseVersion == null && env.BRANCH_NAME != 'main' }
            }
            steps {
                script {
                    int maxParallel = (env.MAX_PARALLEL_SERVICES ?: '3') as int
                    if (maxParallel < 1) {
                        maxParallel = 1
                    }

                    def services = changed.services
                    for (int i = 0; i < services.size(); i += maxParallel) {
                        int end = Math.min(i + maxParallel, services.size())
                        def batch = []
                        for (int j = i; j < end; j++) {
                            batch.add(services[j])
                        }

                        def branches = [:]
                        batch.each { service ->
                            branches[service] = {
                                try {
                                    stage("Package ${service}") {
                                        dockerBuild(service: service)
                                    }
                                } catch (Exception e) {
                                    failedService = service
                                    failedStage = "Package ${service}"
                                    throw e
                                }
                            }
                        }

                        parallel branches
                    }

                    if (changed.web) {
                        try {
                            stage('Package web client') {
                                dockerBuild(
                                    image: 'web-client',
                                    dockerfile: 'mobile/Dockerfile',
                                    context: 'mobile'
                                )
                            }
                        } catch (Exception e) {
                            failedService = 'web-client'
                            failedStage = 'Package web client'
                            throw e
                        }
                    }
                }
            }
        }

        stage('Integration tests') {
            when {
                expression {
                    changed.all || changed.services.any { service ->
                        service == 'circleguard-auth-service' ||
                            service == 'circleguard-form-service' ||
                            service == 'circleguard-promotion-service' ||
                            service == 'circleguard-gateway-service'
                    }
                }
            }
            steps {
                script {
                    def currentStage = 'Integration tests'
                    try {
                        runTests(integration: true, coverage: false, publish: true)
                    } catch (Exception e) {
                        if (!failedService) {
                            failedService = 'integration-tests'
                            failedStage = currentStage
                        }
                        throw e
                    }
                }
            }
        }

        stage('Publish prerelease images') {
            when { expression { prereleaseVersion != null } }
            steps {
                script {
                    // SNAPSHOT publishes only the affected components. A promotion PR (or a
                    // release branch) publishes the full set: the RC is what main promotes.
                    String sourceBranch = env.CHANGE_BRANCH ?: env.BRANCH_NAME
                    boolean onlyChanged = !promotionPr && sourceBranch == 'dev'
                    publishImages(
                        onlyChanged ? changed.services : SERVICES,
                        onlyChanged ? changed.web : true,
                        prereleaseVersion
                    )

                    // Before the merge: checks that main will be able to find this RC (read only).
                    if (promotionPr) {
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
                deployCompose(environment: 'stage', version: prereleaseVersion)
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
                    withEnv(["E2E_BASE_URL=http://${env.DEPLOY_HOST_STAGE}"]) {
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

        stage('Deploy to dev') {
            when {
                allOf {
                    branch 'main'
                    expression { releaseVersion != null }
                }
            }
            steps {
                deployCompose(environment: 'dev', version: releaseVersion)
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
                    withEnv(["E2E_BASE_URL=http://${env.DEPLOY_HOST_DEV}"]) {
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
                deployCompose(environment: 'stage', version: releaseVersion)
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

        stage('Deploy to prod') {
            when {
                allOf {
                    branch 'main'
                    expression { releaseVersion != null }
                }
            }
            steps {
                deployCompose(environment: 'prod', version: releaseVersion)
            }
        }
    }

    post {
        always {
            script {
                if (changed.services) {
                    def patterns = changed.services.collect {
                        "services/${it}/build/test-results/test/*.xml"
                    }.join(',')
                    junit allowEmptyResults: true, testResults: patterns
                    publishCoverage(services: changed.services)
                }
            }
        }
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

// Publishes the images in parallel batches of MAX_PARALLEL_SERVICES. Each image is built from the
// JAR of the Compile stage, so a batch is mostly the push to the registry.
def publishImages(List<String> services, boolean web, String version) {
    int maxParallel = Math.max((env.MAX_PARALLEL_SERVICES ?: '3') as int, 1)
    def jobs = [:]
    services.each { service ->
        jobs[service] = { dockerPublish(service: service, version: version) }
    }
    if (web) {
        jobs['web-client'] = {
            dockerPublish(image: 'web-client', dockerfile: 'mobile/Dockerfile', context: 'mobile', version: version)
        }
    }
    List<String> names = jobs.keySet() as List
    for (int i = 0; i < names.size(); i += maxParallel) {
        def batch = [:]
        names.subList(i, Math.min(i + maxParallel, names.size())).each { name ->
            batch["Publish ${name}"] = jobs[name]
        }
        parallel batch
    }
}
