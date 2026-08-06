plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    // Exposed in the public API: Parameters, @Serializable models.
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)

    // Optional integrations — consumers opt in by adding the dependency themselves.
    // See SortExposedExtensions.kt / SortMongoExtensions.kt.
    compileOnly(libs.exposed.core)
    compileOnly(libs.mongodb.driver.core)

    testImplementation(libs.exposed.core)
    testImplementation(libs.mongodb.driver.core)
    testImplementation(libs.bundles.testing)
}
