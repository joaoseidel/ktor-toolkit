plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    compileOnly(libs.bundles.kotlinx)
    compileOnly(libs.bundles.ktor)

    testImplementation(libs.bundles.kotlinx)
    testImplementation(libs.bundles.ktor)
    testImplementation(libs.bundles.testing)
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
    }
}
