plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // S3 client for the environment bucket (StorageConfig). The URL connection HTTP client
    // replaces the Apache and Netty ones the SDK brings by default: fewer dependencies in the
    // image for Trivy to flag, and enough for one request per upload or download.
    implementation(platform("software.amazon.awssdk:bom:2.56.0"))
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation("software.amazon.awssdk:url-connection-client")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
