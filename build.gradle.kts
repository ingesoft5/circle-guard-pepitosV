plugins {
    id("org.springframework.boot") version "3.2.4" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
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
}
