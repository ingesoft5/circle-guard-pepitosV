plugins {
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.sonarqube") version "5.1.0.4882" apply false
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    kotlin("plugin.jpa") version "1.9.25" apply false
}

val coverageGatedServices = setOf(
    "circleguard-auth-service",
    "circleguard-identity-service",
    "circleguard-promotion-service",
    "circleguard-form-service",
    "circleguard-gateway-service"
)
val minimumLineCoverage = "0.60".toBigDecimal()

allprojects {
    group = "com.circleguard"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        "implementation"("net.logstash.logback:logstash-logback-encoder:7.4")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("com.h2database:h2")
    }

    // The dependency plugin registers its version rule while this project is
    // configured, so this rule has to be added afterwards or the BOM wins.
    // Boot 3.5.16 still ships Tomcat, Jackson, Netty, PostgreSQL and lz4
    // builds that the image gate rejects.
    afterEvaluate {
        configurations.configureEach {
            resolutionStrategy.eachDependency {
                val group = requested.group
                val name = requested.name
                when {
                    group == "org.apache.tomcat.embed" || group == "org.apache.tomcat" -> {
                        useVersion("10.1.61")
                        because("Tomcat 10.1.55 still has critical CVEs fixed in 10.1.59")
                    }
                    group.startsWith("com.fasterxml.jackson.") && name != "jackson-annotations" -> {
                        useVersion("2.21.8")
                        because("Jackson 2.21.4 still has high CVEs fixed in 2.21.7")
                    }
                    group == "org.postgresql" && name == "postgresql" -> {
                        useVersion("42.7.14")
                        because("PostgreSQL JDBC 42.7.11 has a high CVE fixed in 42.7.12")
                    }
                    group == "commons-io" && name == "commons-io" -> {
                        useVersion("2.22.0")
                        because("Commons IO 2.7 has a high CVE fixed in 2.14.0")
                    }
                    group == "org.xerial.snappy" && name == "snappy-java" -> {
                        useVersion("1.1.10.12")
                        because("snappy-java 1.1.10.5 has high CVEs fixed in 1.1.10.9")
                    }
                    group == "org.freemarker" && name == "freemarker" -> {
                        useVersion("2.3.35")
                        because("FreeMarker 2.3.34 has a critical path traversal fixed in 2.3.35")
                    }
                    group == "io.netty" && name.startsWith("netty-") && !name.startsWith("netty-tcnative") -> {
                        useVersion("4.1.141.Final")
                        because("Netty 4.1.135 has high CVEs fixed in 4.1.136")
                    }
                    group == "org.apache.logging.log4j" && (name == "log4j-api" || name == "log4j-to-slf4j") -> {
                        useVersion("2.25.5")
                        because("Log4j API 2.24.3 has a CVE fixed in 2.25.5")
                    }
                    group == "at.yawk.lz4" && name == "lz4-java" -> {
                        useVersion("1.14.1")
                        because("lz4-java 1.10.1 has a high CVE fixed in 1.11.4")
                    }
                }
            }
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "21"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Docker build context: the boot JAR compiled and tested by the pipeline, copied to
    // build/docker/app.jar. The service Dockerfile packages it instead of compiling again.
    plugins.withId("org.springframework.boot") {
        val dockerContext = tasks.register<Copy>("dockerContext") {
            description = "Copies the boot JAR to build/docker/app.jar for the Docker image."
            group = "build"
            from(tasks.named("bootJar"))
            into(layout.buildDirectory.dir("docker"))
            rename { "app.jar" }
        }
        tasks.named("assemble") {
            dependsOn(dockerContext)
        }
    }

    // HU-19: integration tests use Testcontainers and stay out of the unit-test task.
    val sourceSets = the<SourceSetContainer>()
    val integrationTestSourceSet = sourceSets.create("integrationTest")
    integrationTestSourceSet.compileClasspath += sourceSets["main"].output
    integrationTestSourceSet.runtimeClasspath += sourceSets["main"].output
    configurations.named("integrationTestImplementation") {
        extendsFrom(configurations.named("testImplementation").get())
    }
    configurations.named("integrationTestRuntimeOnly") {
        extendsFrom(configurations.named("testRuntimeOnly").get())
    }
    tasks.register<Test>("integrationTest") {
        description = "Runs Testcontainers integration tests."
        group = "verification"
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        shouldRunAfter(tasks.named("test"))
        timeout.set(java.time.Duration.ofMinutes(25))
    }

    // ---- Coverage
    apply(plugin = "jacoco")
    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }

    val coverageExclusions = listOf("**/*Application.class")
    val coveredClasses = the<SourceSetContainer>()["main"].output.classesDirs.asFileTree.matching {
        exclude(coverageExclusions)
    }

    val jacocoTestReport = tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        classDirectories.setFrom(coveredClasses)
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.named<Test>("test") {
        finalizedBy(jacocoTestReport)
    }

    val gated = name in coverageGatedServices
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("test"))
        classDirectories.setFrom(coveredClasses)
        violationRules {
            rule {
                isEnabled = gated
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = minimumLineCoverage
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn("jacocoTestCoverageVerification")
    }

    if (name.startsWith("circleguard-")) {
        apply(plugin = "org.sonarqube")

        extensions.configure<org.sonarqube.gradle.SonarExtension> {
            properties {
                val service = name.removePrefix("circleguard-").removeSuffix("-service")

                property("sonar.projectKey", "circleguard-$service")
                property("sonar.projectName", "CircleGuard $service")
                property("sonar.sourceEncoding", "UTF-8")
                property("sonar.sources", "src/main")
                property("sonar.tests", "src/test")
                property(
                    "sonar.junit.reportPaths",
                    layout.buildDirectory.dir("test-results/test").get().asFile.absolutePath
                )
                property(
                    "sonar.coverage.jacoco.xmlReportPaths",
                    layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")
                        .get().asFile.absolutePath
                )
                property("sonar.coverage.exclusions", "**/*Application*")
            }
        }

        tasks.named("sonar") {
            dependsOn("test", "jacocoTestReport")
        }
    }
}

tasks.register("integrationTest") {
    group = "verification"
    description = "Runs Testcontainers integration tests in every service."
    dependsOn(subprojects.map { it.tasks.named("integrationTest") })
}
