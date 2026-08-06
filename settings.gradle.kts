plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ktor-toolkit"

// Production code
include("ktor-toolkit-expander")
include("ktor-toolkit-hateoas")
include("ktor-toolkit-problem-details")
include("ktor-toolkit-paginator")
include("ktor-toolkit-validator")
include("ktor-toolkit-cache")

// Report generator
include("report")
