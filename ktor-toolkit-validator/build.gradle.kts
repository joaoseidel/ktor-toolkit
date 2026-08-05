plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    // Exposed in the public API: RequestValidationConfig, ValidationResult, KProperty1-based DSL.
    api(libs.ktor.server.core)
    api(libs.ktor.request.validation)
    api(libs.kotlinx.serialization)
    api(libs.kotlinx.datetime)
    api(kotlin("reflect"))

    testImplementation(libs.bundles.testing)
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
    }
}
