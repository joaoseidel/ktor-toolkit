plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)

    testImplementation(libs.ktor.server.testhost)
    testImplementation(libs.bundles.testing)
}
