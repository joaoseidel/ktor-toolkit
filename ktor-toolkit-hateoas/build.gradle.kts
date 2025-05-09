import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinx.kover)
}

dependencies {
    compileOnly(project(":ktor-toolkit-paginator"))

    compileOnly(libs.bundles.kotlinx)
    compileOnly(libs.bundles.ktor)

    testImplementation(libs.bundles.kotlinx)
    testImplementation(libs.bundles.ktor)
    testImplementation(libs.bundles.testing)
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}
