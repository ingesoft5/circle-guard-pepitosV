plugins {
    id("org.springframework.boot") version "3.2.4" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    id("org.sonarqube") version "5.1.0.4882" apply false
    kotlin("jvm") version "1.9.24" apply false
    kotlin("plugin.spring") version "1.9.24" apply false
    kotlin("plugin.jpa") version "1.9.24" apply false
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
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("com.h2database:h2")
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
