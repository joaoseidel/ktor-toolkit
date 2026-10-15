plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)
    api(libs.kotlinx.coroutines)

    testImplementation(libs.ktor.server.testhost)
    testImplementation(libs.mockk)
    testImplementation(libs.bundles.testing)
}
