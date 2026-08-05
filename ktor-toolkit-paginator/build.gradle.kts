plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    // Exposed in the public API: Parameters, @Serializable models.
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)

    // Optional integrations — consumers opt in by adding the dependency themselves.
    // See SortExposedExtensions.kt / SortGelExtensions.kt.
    compileOnly(libs.exposed.core)
    compileOnly(libs.gel.query.dsl.core)

    testImplementation(libs.exposed.core)
    testImplementation(libs.gel.query.dsl.core)
    testImplementation(libs.bundles.testing)
}
