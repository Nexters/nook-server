plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":nook-api-domain"))
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    testImplementation(kotlin("test"))
}
