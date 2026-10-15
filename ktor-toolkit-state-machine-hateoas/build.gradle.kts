plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(project(":ktor-toolkit-state-machine"))
    api(project(":ktor-toolkit-hateoas"))

    testImplementation(libs.bundles.testing)
}
