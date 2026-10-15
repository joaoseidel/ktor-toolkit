plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(project(":ktor-toolkit-paginator"))
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)

    testImplementation(libs.ktor.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.ktor.server.testhost)
    testImplementation(libs.bundles.testing)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}
