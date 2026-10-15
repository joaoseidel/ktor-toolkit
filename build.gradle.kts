import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import java.io.DataInputStream
import java.util.zip.ZipFile
import kotlin.KotlinVersion as MetadataVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.kover) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
}

val javaVersion = 17

val kotlinFloor = KotlinVersion.KOTLIN_2_3

/**
 * Only `io.github.<user>` is verifiable on Maven Central via a GitHub account, so the group has to
 * live under that namespace regardless of what the Kotlin packages are called.
 */
val mavenGroup = "io.github.joaoseidel"

val projectUrl = "https://github.com/joaoseidel/ktor-toolkit"

allprojects {
    group = mavenGroup

    version = providers.gradleProperty("version").get()
}

apiValidation {
    ignoredProjects += listOf(project.name, "report")
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    kotlin {
        jvmToolchain(javaVersion)

        // Without this the POM pins kotlin-stdlib to the compiler version and drags consumers up.
        coreLibrariesVersion = "${kotlinFloor.version}.0"

        compilerOptions {
            languageVersion = kotlinFloor
            apiVersion = kotlinFloor
            freeCompilerArgs.add("-Xjsr305=strict")

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

configure(subprojects.filter { it.name.startsWith("ktor-toolkit-") }) {
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.vanniktech.maven.publish")

    val moduleName = name

    val checkDependencyFloor =
        tasks.register("checkDependencyFloor") {
            val jars =
                configurations.named("runtimeClasspath").map { classpath ->
                    classpath.incoming
                        .artifactView { componentFilter { it is ModuleComponentIdentifier } }
                        .files
                }
            val maxJava = javaVersion
            val (floorMajor, floorMinor) = kotlinFloor.version.split('.').map(String::toInt)
            val newestReadableKotlin = MetadataVersion(floorMajor, floorMinor + 1)

            inputs.files(jars)
            doLast {
                val offenders =
                    jars.get().files.mapNotNull { jar ->
                        ZipFile(jar).use { zip ->
                            val entries = zip.entries().toList()

                            val jvm =
                                entries
                                    .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/versions/") }
                                    .filterNot { it.name.endsWith("module-info.class") }
                                    .maxOfOrNull { entry ->
                                        DataInputStream(zip.getInputStream(entry)).use {
                                            it.skipBytes(6)
                                            it.readUnsignedShort() - 44
                                        }
                                    } ?: 0

                            // A .kotlin_module starts with the length of its version array, then the version.
                            val kotlin =
                                entries.firstOrNull { it.name.endsWith(".kotlin_module") }?.let { entry ->
                                    DataInputStream(zip.getInputStream(entry)).use {
                                        it.readInt()
                                        MetadataVersion(it.readInt(), it.readInt())
                                    }
                                }

                            if (jvm > maxJava || (kotlin != null && kotlin > newestReadableKotlin)) {
                                "${jar.name}: Java $jvm, Kotlin metadata ${kotlin?.let { "${it.major}.${it.minor}" } ?: "none"}"
                            } else {
                                null
                            }
                        }
                    }
                check(offenders.isEmpty()) {
                    "$moduleName pulls in dependencies above its Java $maxJava / Kotlin $floorMajor.$floorMinor floor:\n" +
                        offenders.joinToString("\n") { "  $it" }
                }
            }
        }
    tasks.named("check") { dependsOn(checkDependencyFloor) }

    extensions.configure<MavenPublishBaseExtension> {
        // Sources and Dokka-rendered javadoc are both required for a Central release to validate.
        configure(
            KotlinJvm(
                javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
                sourcesJar = SourcesJar.Sources(),
            ),
        )

        // Whether the deployment is auto-released and whether it is signed come from
        // gradle.properties (`mavenCentralAutomaticPublishing`, `signAllPublications`).
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
