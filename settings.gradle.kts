pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "circleguard"

// Shared build cache of the CI workers (gradle-cache on the CI host, infra/compose/ci). Jenkins
// sets GRADLE_BUILD_CACHE_URL on every node and only Jenkins writes; without the variable (a
// developer machine) Gradle keeps using its local cache only.
val remoteBuildCacheUrl: String? = System.getenv("GRADLE_BUILD_CACHE_URL")
if (!remoteBuildCacheUrl.isNullOrBlank()) {
    buildCache {
        remote<HttpBuildCache> {
            url = uri(remoteBuildCacheUrl)
            isAllowInsecureProtocol = true
            isPush = System.getenv("JENKINS_URL") != null
        }
    }
}

// Shared Libraries (Commented out as folders are missing on disk)
// include("libs:circleguard-common")
// include("libs:circleguard-security")
// include("libs:circleguard-events")

// Microservices
include("services:circleguard-auth-service")
include("services:circleguard-identity-service")
include("services:circleguard-promotion-service")
include("services:circleguard-notification-service")
include("services:circleguard-form-service")
include("services:circleguard-file-service")
include("services:circleguard-gateway-service")
include("services:circleguard-dashboard-service")
