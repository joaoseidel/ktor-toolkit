plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    // Exposed in the public API: Parameters, ApplicationCall, @Serializable models.
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)

    // Optional integrations — consumers opt in by adding the dependency themselves.
    // See FilterExposedExtensions.kt / FilterMongoExtensions.kt.
    compileOnly(libs.exposed.core)
    compileOnly(libs.mongodb.driver.core)

    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.jdbc)
    testImplementation(libs.h2)
    testImplementation(libs.mongodb.driver.core)
    testImplementation(libs.bundles.testing)
}
