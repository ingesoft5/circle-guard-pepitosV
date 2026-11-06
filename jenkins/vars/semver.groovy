/**
 * Funciones puras de versionado semántico usadas por calculateVersion y
 * resolveReleaseVersion (HU-15). No ejecutan pasos de Jenkins, por eso son @NonCPS.
 *
 *   semver.releaseType(['feat(promotion): ...', 'fix: ...'])  // 'minor'
 *   semver.bump('1.2.3', 'minor')                              // '1.3.0'
 *   semver.highest(['1.9.9', '1.10.0'])                        // '1.10.0'
 */

/**
 * Incremento que corresponde a una lista de mensajes de commit (Conventional Commits):
 * 'major' si alguno es incompatible (tipo con "!" o pie BREAKING CHANGE),
 * 'minor' si hay algún feat, 'patch' si hay algún fix o perf, null si no hay release.
 */
@NonCPS
String releaseType(List<String> messages) {
    int level = 0
    for (String message : messages) {
        String text = (message ?: '').trim()
        if (!text) {
            continue
        }
        String header = text.readLines()[0].trim()
        def matcher = header =~ /^([a-zA-Z]+)(\([^)]*\))?(!)?:\s+\S/
        if (!matcher.find()) {
            continue
        }
        String type = matcher.group(1).toLowerCase()
        boolean breaking = matcher.group(3) != null || (text =~ /(?m)^BREAKING[ -]CHANGE:/).find()

        if (breaking) {
            level = Math.max(level, 3)
        } else if (type == 'feat') {
            level = Math.max(level, 2)
        } else if (type in ['fix', 'perf']) {
            level = Math.max(level, 1)
        }
    }
    return [null, 'patch', 'minor', 'major'][level]
}

/** Aplica un incremento a una versión MAJOR.MINOR.PATCH. */
@NonCPS
String bump(String version, String type) {
    List<Integer> parts = parse(version)
    switch (type) {
        case 'major': return "${parts[0] + 1}.0.0"
        case 'minor': return "${parts[0]}.${parts[1] + 1}.0"
        case 'patch': return "${parts[0]}.${parts[1]}.${parts[2] + 1}"
        default: throw new IllegalArgumentException("Incremento desconocido: ${type}")
    }
}

/** La mayor versión MAJOR.MINOR.PATCH de la lista (orden numérico), o null si no hay. */
@NonCPS
String highest(List<String> versions) {
    String best = null
    for (String version : versions) {
        if (!isVersion(version)) {
            continue
        }
        if (best == null || compare(version, best) > 0) {
            best = version
        }
    }
    return best
}

/** La mayor versión de la lista que es menor que "version", o null si no hay. */
@NonCPS
String highestBelow(List<String> versions, String version) {
    List<String> lower = []
    for (String candidate : versions) {
        if (isVersion(candidate) && compare(candidate, version) < 0) {
            lower.add(candidate)
        }
    }
    return highest(lower)
}

/** Las versiones de la lista ordenadas de mayor a menor. */
@NonCPS
List<String> sortDesc(List<String> versions) {
    List<String> sorted = []
    for (String version : versions) {
        if (isVersion(version)) {
            sorted.add(version)
        }
    }
    sorted.sort { String a, String b -> compare(b, a) }
    return sorted
}

/** Versiones a partir de una lista de tags de Git: 'v1.2.3' -> '1.2.3'; ignora el resto. */
@NonCPS
List<String> fromTags(String tagsOutput) {
    List<String> versions = []
    for (String tag : (tagsOutput ?: '').split('\\s+')) {
        if (tag.startsWith('v') && isVersion(tag.substring(1))) {
            versions.add(tag.substring(1))
        }
    }
    return versions
}

/** Separa la salida de git log --format='%B%x1e' en mensajes no vacíos. */
@NonCPS
List<String> splitMessages(String log) {
    List<String> messages = []
    for (String message : (log ?: '').split('\u001e')) {
        if (message.trim()) {
            messages.add(message.trim())
        }
    }
    return messages
}

@NonCPS
boolean isVersion(String value) {
    return value != null && value ==~ /\d+\.\d+\.\d+/
}

@NonCPS
int compare(String a, String b) {
    List<Integer> x = parse(a)
    List<Integer> y = parse(b)
    for (int i = 0; i < 3; i++) {
        if (x[i] != y[i]) {
            return x[i] <=> y[i]
        }
    }
    return 0
}

@NonCPS
List<Integer> parse(String version) {
    if (!isVersion(version)) {
        throw new IllegalArgumentException("'${version}' no es una versión MAJOR.MINOR.PATCH")
    }
    return version.tokenize('.').collect { it as int }
}
