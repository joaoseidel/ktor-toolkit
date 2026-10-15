plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(project(":ktor-toolkit-filter"))
    compileOnly(libs.exposed.core)

    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.jdbc)
    testImplementation(libs.h2)
    testImplementation(libs.bundles.testing)
}
