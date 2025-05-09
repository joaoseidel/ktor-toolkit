import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.kover) apply false

    `maven-publish`
}

allprojects {
    group = "com.luizalabs.ktor-toolkit"
    version = "1.0.0"

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "maven-publish")

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()

        testLogging {
            displayGranularity = 2

            events(
                TestLogEvent.PASSED,
                TestLogEvent.FAILED,
                TestLogEvent.SKIPPED,
            )
        }

        defaultCharacterEncoding = "UTF-8"
    }

    // Maven publishing
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                pom {
                    name.set("Ktor Web Toolkit")
                    description.set("A set of tools to help the development of Ktor applications.")
                    url.set("https://github.com/joaovseidel/ktor-toolkit")
                }
            }
        }

        repositories {
            maven {
            }
        }
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }

    jvmToolchain(25)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
