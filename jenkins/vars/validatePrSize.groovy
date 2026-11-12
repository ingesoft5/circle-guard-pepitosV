/**
 * Valida el tamaño de un Pull Request usando el diff que Jenkins construye.
 *
 * Por defecto: máximo 700 líneas agregadas, 300 eliminadas y 1000 en total.
 * Los límites se centralizan como variables de entorno en el Jenkinsfile.
 *
 * En builds PR-Merge se compara HEAD^1 (base) contra HEAD (merge result).
 * En builds PR-Head se usa el merge-base con la rama destino.
 */
def call(Map config = [:]) {
    if (!env.CHANGE_ID) {
        echo 'validatePrSize: no es un build de Pull Request; se omite.'
        return [skipped: true, additions: 0, deletions: 0, files: 0]
    }
    if (env.CHANGE_TARGET == 'main' && (env.CHANGE_BRANCH ?: '') ==~ /release\/.+/) {
        echo 'validatePrSize: promoción de release a main; se omite el límite de tamaño. Este mismo PR debe completar build, análisis, publicación RC, despliegue a stage y E2E antes de poder fusionarse.'
        return [skipped: true, reason: 'release-promotion', additions: 0, deletions: 0, files: 0]
    }

    int maxAdditions = (config.maxAdditions ?: env.PR_MAX_ADDITIONS ?: '700') as int
    int maxDeletions = (config.maxDeletions ?: env.PR_MAX_DELETIONS ?: '300') as int
    int maxChanged = (config.maxChangedLines ?: env.PR_MAX_CHANGED_LINES ?: '1000') as int

    if ([maxAdditions, maxDeletions, maxChanged].any { it < 0 }) {
        error('validatePrSize: los límites no pueden ser negativos.')
    }

    String base = sh(script: 'git rev-parse --verify HEAD^1 2>/dev/null || true',
            returnStdout: true).trim()
    if (!base) {
        String target = (env.CHANGE_TARGET ?: '').trim()
        if (!target) {
            error('validatePrSize: Jenkins no expuso CHANGE_TARGET y HEAD no tiene primer padre.')
        }
        withEnv(["PR_TARGET=${target}"]) {
            sh 'git fetch --no-tags origin "$PR_TARGET"'
            base = sh(script: 'git merge-base FETCH_HEAD HEAD', returnStdout: true).trim()
        }
    }

    String numstat = sh(
        script: "git diff --no-renames --numstat '${base}' HEAD -- . ':(exclude,glob)**/*.md' ':(exclude,glob)*.md' ':(exclude)docs/**' ':(exclude,glob)**/*.lock' ':(exclude,glob)*.lock' ':(exclude,glob)**/*.lockfile' ':(exclude,glob)*.lockfile' ':(exclude,glob)**/package-lock.json' ':(exclude,glob)**/yarn.lock' ':(exclude,glob)**/pnpm-lock.yaml' ':(exclude,glob)**/gradle.lockfile' ':(exclude)**/build/**' ':(exclude)**/generated/**' ':(exclude,glob)**/*.png' ':(exclude,glob)*.png' ':(exclude,glob)**/*.jpg' ':(exclude,glob)*.jpg' ':(exclude,glob)**/*.jpeg' ':(exclude,glob)*.jpeg' ':(exclude,glob)**/*.pdf' ':(exclude,glob)*.pdf'",
        returnStdout: true
    )
    int additions = 0
    int deletions = 0
    int files = 0
    numstat.readLines().each { line ->
        if (line?.trim()) {
            def columns = line.split('\t', 3)
            if (columns.length == 3) {
                files++
                // Git reports '-' for binary files: they do not count as source lines.
                if (columns[0] ==~ /\d+/) additions += columns[0] as int
                if (columns[1] ==~ /\d+/) deletions += columns[1] as int
            }
        }
    }
    int total = additions + deletions

    echo """PR size: ${additions} additions + ${deletions} deletions = ${total} changed lines across ${files} files.
Limits: additions <= ${maxAdditions}, deletions <= ${maxDeletions}, total <= ${maxChanged}."""

    List<String> violations = []
    if (additions > maxAdditions) violations.add("${additions} adiciones (máximo ${maxAdditions})")
    if (deletions > maxDeletions) violations.add("${deletions} eliminaciones (máximo ${maxDeletions})")
    if (total > maxChanged) violations.add("${total} líneas modificadas en total (máximo ${maxChanged})")

    if (violations) {
        error("PR #${env.CHANGE_ID} excede la política de tamaño: ${violations.join('; ')}. " +
                'Divide el cambio en Pull Requests más pequeños o solicita una excepción documentada.')
    }

    return [skipped: false, additions: additions, deletions: deletions, total: total, files: files]
}
