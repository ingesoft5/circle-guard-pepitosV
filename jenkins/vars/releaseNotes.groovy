/**
 * Release notes generadas desde los Conventional Commits (HU-22).
 *
 *   String notes = releaseNotes(version: '1.3.0')   // sección de una versión
 *   String changelog = releaseNotes.changelog()      // CHANGELOG.md completo
 *
 * Cada versión agrupa los commits desde el tag anterior en Breaking changes,
 * Features, Fixes y Performance. Cada entrada enlaza a su commit y, si llegó por un
 * pull request, también al PR: se detecta por el merge commit "Merge pull request #N"
 * o por el sufijo "(#N)" de un squash merge. Otros tipos (docs, chore, ci...) no se listan.
 */
def call(Map config = [:]) {
    String version = (config.version ?: '').toString().trim()
    if (!semver.isVersion(version)) {
        error("releaseNotes: '${version}' no es una versión MAJOR.MINOR.PATCH")
    }
    int exists = sh(script: "git rev-parse -q --verify 'refs/tags/v${version}' >/dev/null", returnStatus: true)
    if (exists != 0) {
        error("releaseNotes: no existe el tag v${version} (lo crea pushReleaseTag)")
    }
    Map ctx = notesContext("v${version}")
    return section(version, ctx)
}

/** CHANGELOG.md completo: todas las versiones del historial de HEAD, de la más nueva a la más antigua. */
def changelog() {
    Map ctx = notesContext('HEAD')
    String text = '''# Changelog

Todas las versiones de CircleGuard. Este archivo lo genera el pipeline de Jenkins a partir de
los Conventional Commits (HU-22): no se edita a mano.
Esquema de versiones: [docs/release-notes/etiquetado.md](docs/release-notes/etiquetado.md).
'''
    for (String version : semver.sortDesc(ctx.versions)) {
        text += '\n' + section(version, ctx)
    }
    return text
}

/** owner/repo del remoto origin en GitHub, p. ej. ingesoft5/circle-guard-pepitosV. */
def repository() {
    String url = githubUrl(sh(script: 'git remote get-url origin', returnStdout: true).trim())
    if (!url) {
        error('releaseNotes: el remoto origin no apunta a un repositorio de GitHub')
    }
    return url - 'https://github.com/'
}

/** Datos comunes a todas las secciones: URL del repo, versiones, fechas y commit -> PR. */
def notesContext(String ref) {
    String repoUrl = "https://github.com/${repository()}"
    String tags = sh(script: "git tag --merged '${ref}' --list 'v[0-9]*'", returnStdout: true)
    String dates = sh(
        script: "git for-each-ref --format='%(refname:short) %(creatordate:short)' 'refs/tags/v[0-9]*'",
        returnStdout: true
    )
    String prs = ''
    withEnv(["NOTES_REF=${ref}"]) {
        // Para cada merge de un PR, los commits que trajo: "<sha> <número de PR>".
        // Del merge más antiguo al más nuevo: un commit queda asociado a su primer PR
        // (el de la rama de trabajo hacia dev, no el de dev hacia main).
        prs = sh(script: '''
            git log --merges --reverse --format='%H%x09%s' "$NOTES_REF" |
            while IFS="$(printf '\\t')" read -r sha subject; do
              case "$subject" in
                "Merge pull request #"*)
                  number="${subject#Merge pull request #}"
                  number="${number%% *}"
                  git rev-list "$sha^1..$sha^2" | sed "s/\\$/ $number/"
                  ;;
              esac
            done
        ''', returnStdout: true)
    }
    return [repoUrl: repoUrl, versions: semver.fromTags(tags), dates: parseDates(dates), prs: parsePrs(prs)]
}

def section(String version, Map ctx) {
    String previous = semver.highestBelow(ctx.versions, version)
    String range = previous ? "v${previous}..v${version}" : "v${version}"
    String log = sh(script: "git log --no-merges --format='%H%x1f%B%x1e' '${range}'", returnStdout: true)
    return formatSection(version, previous, ctx.dates["v${version}"], parseCommits(log), ctx.prs, ctx.repoUrl)
}

@NonCPS
String githubUrl(String remote) {
    def matcher = remote =~ /github\.com[:\/]+([^\/]+)\/([^\/]+?)(\.git)?\/*$/
    return matcher.find() ? "https://github.com/${matcher.group(1)}/${matcher.group(2)}" : null
}

@NonCPS
Map<String, String> parseDates(String output) {
    Map<String, String> dates = [:]
    for (String line : output.readLines()) {
        List<String> parts = line.trim().tokenize(' ')
        if (parts.size() == 2) {
            dates[parts[0]] = parts[1]
        }
    }
    return dates
}

@NonCPS
Map<String, String> parsePrs(String output) {
    Map<String, String> prs = [:]
    for (String line : output.readLines()) {
        List<String> parts = line.trim().tokenize(' ')
        if (parts.size() == 2 && !prs.containsKey(parts[0])) {
            prs[parts[0]] = parts[1]
        }
    }
    return prs
}

@NonCPS
List<Map> parseCommits(String log) {
    List<Map> commits = []
    for (String record : log.split('\u001e')) {
        int separator = record.indexOf('\u001f')
        if (separator < 0) {
            continue
        }
        commits.add([sha: record.substring(0, separator).trim(), message: record.substring(separator + 1).trim()])
    }
    return commits
}

@NonCPS
String formatSection(String version, String previous, String date, List<Map> commits,
                     Map<String, String> prs, String repoUrl) {
    Map<String, List<String>> groups = [
        'Breaking changes': [], 'Features': [], 'Fixes': [], 'Performance': []
    ]

    for (Map commit : commits) {
        String message = commit.message
        String header = message.readLines() ? message.readLines()[0].trim() : ''
        def matcher = header =~ /^([a-zA-Z]+)(?:\(([^)]*)\))?(!)?:\s+(.+)$/
        if (!matcher.find()) {
            continue
        }
        String type = matcher.group(1).toLowerCase()
        String scope = matcher.group(2)
        String description = matcher.group(4).trim()

        String pr = prs[commit.sha]
        def squash = description =~ /\s*\(#(\d+)\)$/
        if (squash.find()) {
            pr = squash.group(1)
            description = description.substring(0, squash.start()).trim()
        }

        def footer = message =~ /(?ms)^BREAKING[ -]CHANGE:\s*(.+?)(?:\n\s*\n|\z)/
        boolean hasFooter = footer.find()
        boolean breaking = matcher.group(3) != null || hasFooter
        String text = hasFooter ? footer.group(1).replaceAll(/\s*\n\s*/, ' ').trim() : description

        String links = (pr ? "([#${pr}](${repoUrl}/pull/${pr})) " : '') +
                "([${commit.sha.take(7)}](${repoUrl}/commit/${commit.sha}))"
        String entry = "- ${scope ? "**${scope}:** " : ''}${text} ${links}"

        if (breaking) {
            groups['Breaking changes'].add(entry)
        } else if (type == 'feat') {
            groups['Features'].add(entry)
        } else if (type == 'fix') {
            groups['Fixes'].add(entry)
        } else if (type == 'perf') {
            groups['Performance'].add(entry)
        }
    }

    String title = previous ?
            "## [${version}](${repoUrl}/compare/v${previous}...v${version})" :
            "## [${version}](${repoUrl}/releases/tag/v${version})"
    StringBuilder out = new StringBuilder(title)
    if (date) {
        out.append(" (${date})")
    }
    out.append('\n')

    boolean empty = true
    groups.each { String name, List<String> entries ->
        if (entries) {
            empty = false
            out.append("\n### ${name}\n\n")
            entries.each { out.append(it).append('\n') }
        }
    }
    if (empty) {
        out.append('\nSin features, fixes ni cambios incompatibles en esta versión.\n')
    }
    return out.toString()
}
