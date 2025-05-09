plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    compileOnly(libs.bundles.kotlinx)
    compileOnly(libs.bundles.ktor)
    compileOnly(libs.bundles.exposed)
    compileOnly(libs.gel.query.dsl.core)

    testImplementation(libs.bundles.kotlinx)
    testImplementation(libs.bundles.ktor)
    testImplementation(libs.bundles.testing)
}
