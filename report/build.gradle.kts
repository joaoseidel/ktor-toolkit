import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.tasks.KoverReport

plugins {
    alias(libs.plugins.kotlinx.kover)
}

val koverSkip: Boolean = providers.gradleProperty("koverSkip").map(String::toBoolean).getOrElse(false)
val koverCoverageLineRate: Int = 0
val koverCoverageBranchRate: Int = 0

/** Every library module contributes to the aggregated coverage report. */
val coveredProjects =
    listOf(
        ":ktor-toolkit-cache",
        ":ktor-toolkit-expander",
        ":ktor-toolkit-hateoas",
        ":ktor-toolkit-mediator",
        ":ktor-toolkit-paginator",
        ":ktor-toolkit-validator",
    )

dependencies {
    coveredProjects.forEach { kover(project(it)) }
}

tasks.withType<KoverReport>().configureEach {
    dependsOn(coveredProjects.map { project(it).tasks.named("test") })
}

kover {
    currentProject {
        sources {
            excludeJava = true
        }
    }

    reports {
        total {
            xml {
                onCheck = true
            }

            html {
                onCheck = true
            }
        }

        verify {
            rule("Line Coverage") {
                disabled = koverSkip

                bound {
                    minValue = koverCoverageLineRate
                    coverageUnits = CoverageUnit.LINE
                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                }
            }

            rule("Branch Coverage") {
                disabled = koverSkip

                bound {
                    minValue = koverCoverageBranchRate
                    coverageUnits = CoverageUnit.BRANCH
                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                }
            }
        }
    }
}
