plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(libs.ktor.server.core)
    api(libs.ktor.status.pages)
    api(libs.ktor.request.validation)
    api(libs.kotlinx.serialization)

    testImplementation(libs.ktor.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(project(":ktor-toolkit-validator"))
    testImplementation(libs.ktor.server.testhost)
    testImplementation(libs.bundles.testing)
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
    }
}
