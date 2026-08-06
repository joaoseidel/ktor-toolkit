import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.kover) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.binary.compatibility.validator)

    `maven-publish`
}

/** Single source of truth for the JVM target. Java 21 LTS is the floor for consumers. */
val javaVersion = 21

allprojects {
    group = "com.github.joaoseidel.ktor-toolkit"
    version = providers.gradleProperty("version").get()

    repositories {
        mavenCentral()
    }
}

// Neither the umbrella project nor the coverage aggregator ships a public API.
apiValidation {
    ignoredProjects += listOf(project.name, "report")
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "maven-publish")

    kotlin {
        jvmToolchain(javaVersion)

        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion)
        }

        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()

        testLogging {
            displayGranularity = 2
            exceptionFormat = TestExceptionFormat.FULL

            events(
                TestLogEvent.PASSED,
                TestLogEvent.FAILED,
                TestLogEvent.SKIPPED,
            )
        }

        defaultCharacterEncoding = "UTF-8"
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                pom {
                    name = "Ktor Toolkit :: ${project.name}"
                    description = "A set of tools to help the development of Ktor applications."
                    url = "https://github.com/joaovseidel/ktor-toolkit"

                    licenses {
                        license {
                            name = "MIT License"
                            url = "https://github.com/joaovseidel/ktor-toolkit/blob/main/LICENSE"
                            distribution = "repo"
                        }
                    }

                    developers {
                        developer {
                            id = "joaovseidel"
                            name = "João Seidel"
                            email = "joaovseidel@gmail.com"
                        }
                    }

                    scm {
                        url = "https://github.com/joaovseidel/ktor-toolkit"
                        connection = "scm:git:https://github.com/joaovseidel/ktor-toolkit.git"
                        developerConnection = "scm:git:ssh://git@github.com/joaovseidel/ktor-toolkit.git"
                    }
                }
            }
        }
        // No remote repository is wired yet — `publishToMavenLocal` is the supported flow.
    }
}

kotlin {
    jvmToolchain(javaVersion)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}
