plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    compileOnly(project(":ktor-toolkit-hateoas"))
    compileOnly(libs.ktor.server.core)

    testImplementation(project(":ktor-toolkit-hateoas"))
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.bundles.testing)
}
