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
    jvmToolchain(25)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
