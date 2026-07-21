pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "nook-api"

include(
    "nook-api-application",
    "nook-api-batch",
    "nook-api-domain",
    "nook-api-infrastructure",
    "nook-api-presentation",
)
