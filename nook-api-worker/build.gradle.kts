plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":nook-api-application"))
    runtimeOnly(project(":nook-api-infrastructure"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework:spring-tx")
    implementation("net.javacrumbs.shedlock:shedlock-spring:7.7.0")
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation(kotlin("reflect"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
    testImplementation(kotlin("test"))
}

tasks.jar {
    enabled = false
}
