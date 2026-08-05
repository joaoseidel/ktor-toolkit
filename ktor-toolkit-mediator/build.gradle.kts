plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    // Exposed in the public API: ApplicationCall, StatusPagesConfig, RequestValidationException.
    api(libs.ktor.server.core)
    api(libs.ktor.status.pages)
    api(libs.ktor.request.validation)
    api(libs.kotlinx.serialization)

    testImplementation(libs.ktor.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.bundles.testing)
}
