import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.kover) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
}

/** Single source of truth for the JVM target. Java 21 LTS is the floor for consumers. */
val javaVersion = 21

/**
 * Only `io.github.<user>` is verifiable on Maven Central via a GitHub account, so the group has to
 * live under that namespace regardless of what the Kotlin packages are called.
 */
val mavenGroup = "io.github.joaoseidel"

val projectUrl = "https://github.com/joaoseidel/ktor-toolkit"

allprojects {
    group = mavenGroup

    // `-Pversion=` lets the snapshot workflow stamp a build without touching gradle.properties.
    version = providers.gradleProperty("version").get()
}

// Neither the umbrella project nor the coverage aggregator ships a public API.
apiValidation {
    ignoredProjects += listOf(project.name, "report")
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    kotlin {
        jvmToolchain(javaVersion)

        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")

            // An interface method with a body compiles to a JVM default method alone. The default
            // is to emit a DefaultImpls trampoline beside it, for binary compatibility with code
            // built against a Kotlin that had no default methods — code that cannot exist for a
            // library whose first release this is. Nothing calls the copy, and dead bytecode is
            // better deleted than explained to the coverage gate.
            jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
        }
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion)
        }
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
}

/**
 * The library modules are the only publishable ones — `report` exists to aggregate coverage and has
 * nothing consumers could depend on. Configuring them from here keeps one POM definition instead of
 * a copy per module.
 */
configure(subprojects.filter { it.name.startsWith("ktor-toolkit-") }) {
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.vanniktech.maven.publish")

    val moduleName = name

    extensions.configure<MavenPublishBaseExtension> {
        // Sources and Dokka-rendered javadoc are both required for a Central release to validate.
        configure(
            KotlinJvm(
                javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
                sourcesJar = SourcesJar.Sources(),
            ),
        )

        // Whether the deployment is auto-released and whether it is signed come from
        // gradle.properties (`mavenCentralAutomaticPublishing`, `signAllPublications`), so CI can
        // turn signing off for an unsigned smoke test with `-PsignAllPublications=false`.
        publishToMavenCentral()

        coordinates(mavenGroup, moduleName, version.toString())

        pom {
            name = "Ktor Toolkit :: $moduleName"
            description = "A set of tools to help the development of Ktor applications."
            url = projectUrl
            inceptionYear = "2026"

            licenses {
                license {
                    name = "MIT License"
                    url = "$projectUrl/blob/main/LICENSE"
                    distribution = "repo"
                }
            }

            developers {
                developer {
                    id = "joaoseidel"
                    name = "João Seidel"
                    email = "joaovseidel@gmail.com"
                    url = "https://github.com/joaoseidel"
                }
            }

            scm {
                url = projectUrl
                connection = "scm:git:$projectUrl.git"
                developerConnection = "scm:git:ssh://git@github.com/joaoseidel/ktor-toolkit.git"
            }

            issueManagement {
                system = "GitHub Issues"
                url = "$projectUrl/issues"
            }
        }
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
