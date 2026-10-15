plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)

    compileOnly(libs.exposed.core)
    compileOnly(libs.mongodb.driver.core)

    testImplementation(libs.exposed.core)
    testImplementation(libs.mongodb.driver.core)
    testImplementation(libs.bundles.testing)
}
