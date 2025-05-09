import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.tasks.KoverReport

plugins {
    alias(libs.plugins.kotlinx.kover)
}

val koverSkip: Boolean = false
val koverCoverageLineRate: Int = 0
val koverCoverageBranchRate: Int = 0
val koverExclusions = listOf("")

dependencies {
    kover(project(":ktor-toolkit-expander"))
    kover(project(":ktor-toolkit-hateoas"))
    kover(project(":ktor-toolkit-mediator"))
    kover(project(":ktor-toolkit-paginator"))
    kover(project(":ktor-toolkit-validator"))
}

tasks.withType<KoverReport> {
    dependsOn(
        project(":ktor-toolkit-expander").tasks.test,
        project(":ktor-toolkit-hateoas").tasks.test,
        project(":ktor-toolkit-mediator").tasks.test,
        project(":ktor-toolkit-paginator").tasks.test,
        project(":ktor-toolkit-validator").tasks.test,
    )
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

        filters {
            excludes {
                classes(koverExclusions)
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
