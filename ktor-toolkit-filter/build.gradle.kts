plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)

    compileOnly(libs.exposed.core)
    compileOnly(libs.mongodb.driver.core)

    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.jdbc)
    testImplementation(libs.h2)
    testImplementation(libs.mongodb.driver.core)
    testImplementation(libs.ktor.server.testhost)
    testImplementation(libs.bundles.testing)
}
