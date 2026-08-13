import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.tasks.KoverReport

plugins {
    alias(libs.plugins.kotlinx.kover)
}

val koverSkip: Boolean = providers.gradleProperty("koverSkip").map(String::toBoolean).getOrElse(false)
val koverCoverageLineRate: Int = 100
val koverCoverageBranchRate: Int = 100

/** Every library module contributes to the aggregated coverage report. */
val coveredProjects =
    listOf(
        ":ktor-toolkit-cache",
        ":ktor-toolkit-expander",
        ":ktor-toolkit-hateoas",
        ":ktor-toolkit-problem-details",
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
        filters {
            excludes {
                // The compiler emits a non-inlined copy of a public inline function for the
                // declaration itself; callers inline the body instead, so that copy never runs.
                annotatedBy("com.github.joaoseidel.ktor.toolkit.cache.UnreachableBytecode")

                // Every property of PaginationRequest is optional, so kotlinx.serialization guards
                // its generated `throwMissingFieldException` with `(0 and seen) != 0` — always
                // false. Named precisely: PaginationRequest$Companion, which holds the parsing this
                // class is really about, stays measured.
                classes("com.github.joaoseidel.ktor.toolkit.paginator.web.PaginationRequest")
            }
        }

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
