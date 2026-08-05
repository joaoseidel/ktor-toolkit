plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    // Exposed in the public API: ApplicationRequest, Json, suspending cache contract.
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)
    api(libs.kotlinx.coroutines)

    testImplementation(libs.bundles.testing)
}
