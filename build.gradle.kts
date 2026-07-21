import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.spring") version "2.4.0" apply false
    kotlin("plugin.jpa") version "2.4.0" apply false
    id("org.springframework.boot") version "4.0.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("dev.detekt") version "2.0.0-alpha.5" apply false
}

allprojects {
    group = "org.every.nook.api"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

val allowedModuleDependencies = mapOf(
    ":nook-api-domain" to emptySet(),
    ":nook-api-application" to setOf(":nook-api-domain"),
    ":nook-api-infrastructure" to setOf(":nook-api-application", ":nook-api-domain"),
    ":nook-api-presentation" to setOf(":nook-api-application", ":nook-api-infrastructure"),
    ":nook-api-batch" to setOf(":nook-api-application", ":nook-api-infrastructure"),
)

val verifyModuleDependencies = tasks.register("verifyModuleDependencies") {
    group = "verification"
    description = "Verifies that project dependencies follow the module dependency direction."

    val violations = subprojects.flatMap { module ->
        val allowed = allowedModuleDependencies.getValue(module.path)
        module.configurations
            .flatMap { configuration ->
                configuration.dependencies
                    .withType(ProjectDependency::class.java)
                    .map { dependency -> dependency.path }
            }
            .toSet()
            .minus(allowed)
            .map { forbidden -> "${module.path} -> $forbidden" }
    }
    inputs.property("violations", violations.joinToString())

    doLast {
        val configuredViolations = inputs.properties.getValue("violations") as String
        check(configuredViolations.isBlank()) {
            "Forbidden project dependencies: $configuredViolations"
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "dev.detekt")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(25)
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget("25"))
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
            )
        }
    }

    extensions.configure<DetektExtension> {
        toolVersion.set("2.0.0-alpha.5")
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig.set(true)
        parallel.set(true)
    }

    dependencies {
        add("detektPlugins", "dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.5")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.named("check") {
        dependsOn(rootProject.tasks.named("verifyModuleDependencies"))
    }
}
