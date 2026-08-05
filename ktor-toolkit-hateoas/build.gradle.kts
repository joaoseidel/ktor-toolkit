plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    // PagedResponse appears in the signature of createPaginationLinks / toResource.
    api(project(":ktor-toolkit-paginator"))
    api(libs.ktor.server.core)
    api(libs.kotlinx.serialization)

    testImplementation(libs.bundles.testing)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}
