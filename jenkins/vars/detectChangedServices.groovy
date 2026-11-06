def call(Map config = [:]) {
    List<String> allServices = (config.services ?: [
        'circleguard-auth-service',
        'circleguard-identity-service',
        'circleguard-promotion-service',
        'circleguard-notification-service',
        'circleguard-form-service',
        'circleguard-file-service',
        'circleguard-gateway-service',
        'circleguard-dashboard-service'
    ]).collect { it.toString() }

    if (config.buildAll == true || env.BUILD_ALL == 'true') {
        echo 'detectChangedServices: BUILD_ALL activo; se procesan los 8 servicios y el web client.'
        return [services: allServices.sort(), web: true, all: true, base: '']
    }

    String base = (config.baseCommit ?: '').toString().trim()

    if (!base && env.CHANGE_ID) {
        base = sh(
            script: 'git rev-parse --verify --quiet HEAD^1 || true',
            returnStdout: true
        ).trim()
        String target = env.CHANGE_TARGET ?: ''
        if (!base && target) {
            base = sh(
                script: "git merge-base HEAD 'origin/${target}' || true",
                returnStdout: true
            ).trim()
        }
    }

    if (!base && !env.CHANGE_ID) {
        try {
            def previous = currentBuild.previousSuccessfulBuild
            if (previous) {
                def buildData = previous.rawBuild?.getAction(hudson.plugins.git.util.BuildData.class)
                base = (buildData?.lastBuiltRevision?.sha1 ?: '').toString().trim()
            }
        } catch (Exception ignored) {
            base = ''
        }
    }

    if (!base) {
        base = (env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: '').toString().trim()
    }

    if (!base) {
        echo 'detectChangedServices: sin commit base; se procesan los 8 servicios y el web client.'
        return [services: allServices.sort(), web: true, all: true, base: '']
    }

    boolean exists = sh(
        script: "git cat-file -e '${base}^{commit}'",
        returnStatus: true
    ) == 0
    if (!exists) {
        echo "detectChangedServices: el commit base ${base} no está en el clon; se procesan todos."
        return [services: allServices.sort(), web: true, all: true, base: base]
    }

    boolean isAncestor = sh(
        script: "git merge-base --is-ancestor '${base}' HEAD",
        returnStatus: true
    ) == 0
    if (!isAncestor) {
        echo "detectChangedServices: ${base} no es ancestro de HEAD; se procesan todos."
        return [services: allServices.sort(), web: true, all: true, base: base]
    }

    String diff = sh(
        script: "git diff --name-only '${base}' HEAD",
        returnStdout: true
    ).trim()

    if (!diff) {
        echo "detectChangedServices: sin cambios entre ${base} y HEAD."
        return [services: [], web: false, all: false, base: base]
    }

    List<String> changedFiles = diff.split(/\r?\n/).collect { it.trim() }.findAll { it }
    Set<String> selected = new LinkedHashSet<String>()
    boolean web = false
    boolean allServicesChanged = false

    List<String> exactShared = [
        'build.gradle.kts',
        'settings.gradle.kts',
        'gradle.properties',
        'gradlew',
        'gradlew.bat'
    ]
    List<String> prefixShared = [
        'gradle/',
        'buildSrc/',
        'libs/'
    ]
    List<String> pipelinePaths = [
        'Jenkinsfile',
        'jenkins/',
        'infra/compose/ci/jenkins/'
    ]

    for (String file : changedFiles) {
        if (file.startsWith('mobile/')) {
            web = true
            continue
        }

        if (file.startsWith('docs/') || file == 'README.md' || file.endsWith('.md')) {
            continue
        }

        if (file.startsWith('services/')) {
            List<String> parts = file.tokenize('/')
            if (parts.size() >= 2) {
                String service = parts[1]
                if (allServices.contains(service)) {
                    selected.add(service)
                } else {
                    allServicesChanged = true
                }
            } else {
                allServicesChanged = true
            }
            continue
        }

        if (exactShared.contains(file) || prefixShared.any { file.startsWith(it) }) {
            allServicesChanged = true
            continue
        }

        if (pipelinePaths.any { file.startsWith(it) }) {
            allServicesChanged = true
            web = true
            continue
        }

        allServicesChanged = true
        web = true
    }

    if (allServicesChanged) {
        selected = new LinkedHashSet<String>(allServices)
    }

    List<String> result = selected.toList().sort()
    echo "detectChangedServices: base=${base}, archivos=${changedFiles.size()}, servicios=${result}, web=${web}"
    return [services: result, web: web, all: allServicesChanged, base: base]
}
