import groovy.json.JsonOutput

def call(Map config = [:]) {
    String status = (config.status ?: 'FAILED').toString().toUpperCase()
    String service = (config.service ?: 'N/A').toString()
    String stage = (config.stage ?: 'unknown').toString()
    String branch = (config.branch ?: env.CHANGE_BRANCH ?: env.BRANCH_NAME ?: env.GIT_BRANCH ?: 'unknown').toString()
    String credentialsId = (config.credentialsId ?: 'discord-webhook').toString()
    String buildUrl = (config.buildUrl ?: env.BUILD_URL ?: '').toString()
    String buildNumber = (config.buildNumber ?: env.BUILD_NUMBER ?: '').toString()
    String author = (config.author ?: env.CHANGE_AUTHOR ?: '').toString()

    if (!author) {
        try {
            author = sh(script: "git log -1 --format='%an'", returnStdout: true).trim()
        } catch (Exception ignored) {
            author = 'unknown'
        }
    }
    if (!author) {
        author = 'unknown'
    }

    int color = status == 'RECOVERED' ? 3066993 : (status == 'UNSTABLE' ? 15105570 : 15158332)
    String title = status == 'RECOVERED' ? 'Pipeline RECOVERED' : (status == 'UNSTABLE' ? 'Pipeline UNSTABLE' : 'Pipeline FAILED')

    def fields = [
        [name: 'Branch', value: branch, inline: true],
        [name: 'Service', value: service, inline: true],
        [name: 'Stage', value: stage, inline: true],
        [name: 'Author', value: author, inline: true]
    ]

    if (buildUrl) {
        String buildLink = buildNumber ? "[#${buildNumber}](${buildUrl})" : buildUrl
        fields.add([name: 'Build', value: buildLink, inline: false])
    }

    def payload = [
        embeds: [[
            title: title,
            color: color,
            fields: fields
        ]]
    ]

    String fileName = ".discord-notify-${buildNumber ?: 'build'}.json"

    try {
        writeFile file: fileName, text: JsonOutput.toJson(payload)
        withCredentials([string(credentialsId: credentialsId, variable: 'DISCORD_WEBHOOK_URL')]) {
            sh """
                curl -fsS -m 15 -X POST \
                  -H 'Content-Type: application/json' \
                  --data-binary @'${fileName}' \
                  "\$DISCORD_WEBHOOK_URL"
            """
        }
        echo "notifyDiscord: ${status} notification sent"
    } catch (Exception e) {
        echo "notifyDiscord: could not send ${status} notification: ${e.message}"
    } finally {
        try {
            sh "rm -f '${fileName}'"
        } catch (Exception ignored) {
        }
    }
}
