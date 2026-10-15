plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(project(":ktor-toolkit-paginator"))
    compileOnly(libs.exposed.core)

    testImplementation(libs.exposed.core)
    testImplementation(libs.bundles.testing)
}
