plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(project(":ktor-toolkit-cache"))
    compileOnly(libs.lettuce.core)

    testImplementation(libs.lettuce.core)
    testImplementation(libs.mockk)
    testImplementation(libs.bundles.testing)
}
