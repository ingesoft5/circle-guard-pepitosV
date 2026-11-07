// Publishes the JaCoCo XML report of each service as its own coverage
// result, so Jenkins shows line coverage per service.
def call(Map config = [:]) {
    def services = config.services ?: []
    for (service in services) {
        def report = "services/${service}/build/reports/jacoco/test/jacocoTestReport.xml"
        if (!fileExists(report)) {
            echo "No hay reporte de cobertura para ${service}."
            continue
        }
        try {
            recordCoverage(
                tools: [[parser: 'JACOCO', pattern: report]],
                id: "coverage-${service}",
                name: "Cobertura ${service}",
                sourceCodeRetention: 'NEVER',
                skipPublishingChecks: true
            )
        } catch (NoSuchMethodError e) {
            echo 'El plugin coverage no está instalado; se omite la publicación de cobertura.'
        }
    }
}