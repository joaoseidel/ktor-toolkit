plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    api(libs.kotlinx.coroutines)
    compileOnly(libs.jnats)
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.extension.kotlin)
    implementation(libs.slf4j.api)

    testImplementation(libs.jnats)
    testImplementation(libs.opentelemetry.api)
    testImplementation(libs.opentelemetry.extension.kotlin)
    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.mockk)
    testImplementation(libs.bundles.testing)
}

tasks.withType<Test>().configureEach {
    systemProperty("kotest.framework.timeout", "60000")
}
