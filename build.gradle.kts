import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.springframework.boot.gradle.tasks.bundling.BootJar
import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    id("org.springframework.boot") version "4.0.6" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    id("com.diffplug.spotless") version "7.2.1" apply false
}

group = "com.notebook.lumen"
version = "0.0.1-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

plugins.apply("com.diffplug.spotless")
extensions.configure<SpotlessExtension> {
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events = setOf(
                TestLogEvent.FAILED,
                TestLogEvent.SKIPPED
            )
        }
    }

    // Stable artifact name for Docker ENTRYPOINT usage.
    tasks.withType<BootJar>().configureEach {
        archiveFileName.set("${project.name}.jar")
        enabled = project.name != "common-security"
    }

    extensions.configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat("1.28.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}

// Root convenience task: start all services.
// NOTE: Executes subproject `bootRun` tasks; parallelism is controlled via Gradle parallel execution.
tasks.register("bootRun") {
    group = "application"
    description = "Start all services"
    dependsOn(
        ":api-gateway:bootRun",
        ":identity-service:bootRun",
        ":workspace-service:bootRun",
        ":content-service:bootRun"
    )
}

tasks.register("rlsIntegrationTest") {
    group = "verification"
    description = "Run staging-like Runtime RLS integration tests for workspace and content services"
    dependsOn(
        ":workspace-service:rlsIntegrationTest",
        ":content-service:rlsIntegrationTest"
    )
}
