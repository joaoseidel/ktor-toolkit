plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    // Exposed in the public API: ApplicationCall, Parameters, KSerializer.
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)
    api(libs.kotlinx.coroutines)

    testImplementation(libs.bundles.testing)
}
