plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
}

// Docker 29 rejects the Testcontainers API that older Boot releases manage.
extra["testcontainers.version"] = "1.21.4"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.2.0"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.2.0"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-core")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    "integrationTestImplementation"("org.testcontainers:junit-jupiter:1.21.4")
}
