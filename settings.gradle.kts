plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "notebook-platform"

include(
    "common-security",
    "api-gateway",
    "identity-service",
    "workspace-service",
    "content-service"
)
