plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(libs.ktor.server.core)
    api(libs.ktor.request.validation)
    api(libs.kotlinx.serialization)
    api(libs.kotlinx.datetime)

    testImplementation(libs.ktor.server.testhost)
    testImplementation(libs.bundles.testing)
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
    }
}
