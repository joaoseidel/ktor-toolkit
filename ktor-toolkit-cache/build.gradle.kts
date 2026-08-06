plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    // Exposed in the public API: ApplicationRequest, Json, suspending cache contract.
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)
    api(libs.kotlinx.coroutines)

    // Optional integration — consumers opt in by adding the dependency themselves.
    // See LettuceCache.kt.
    compileOnly(libs.lettuce.core)

    testImplementation(libs.lettuce.core)
    testImplementation(libs.bundles.testing)
}
