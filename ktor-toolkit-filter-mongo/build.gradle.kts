plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(project(":ktor-toolkit-filter"))
    compileOnly(libs.mongodb.driver.core)

    testImplementation(libs.mongodb.driver.core)
    testImplementation(libs.bundles.testing)
}
