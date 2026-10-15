plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)
    api(libs.kotlinx.coroutines)

    compileOnly(libs.lettuce.core)

    testImplementation(libs.lettuce.core)
    testImplementation(libs.ktor.server.testhost)
    testImplementation(libs.mockk)
    testImplementation(libs.bundles.testing)
}
