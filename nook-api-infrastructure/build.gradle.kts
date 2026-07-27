plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":nook-api-application"))
    implementation(project(":nook-api-domain"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework:spring-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.linecorp.kotlin-jdsl:spring-data-jpa-boot4-support:3.9.0")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation(platform("software.amazon.awssdk:bom:2.49.3"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:url-connection-client")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}
