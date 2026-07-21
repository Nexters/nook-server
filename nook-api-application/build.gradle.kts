plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":nook-api-domain"))

    testImplementation(kotlin("test"))
}
