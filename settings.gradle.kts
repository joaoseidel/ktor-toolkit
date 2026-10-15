import org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ktor-toolkit"

dependencyResolutionManagement {
    repositoriesMode = FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()
    }
}

include("ktor-toolkit-expander")
include("ktor-toolkit-filter")
include("ktor-toolkit-filter-exposed")
include("ktor-toolkit-filter-mongo")
include("ktor-toolkit-hateoas")
include("ktor-toolkit-problem-details")
include("ktor-toolkit-paginator")
include("ktor-toolkit-paginator-exposed")
include("ktor-toolkit-paginator-mongo")
include("ktor-toolkit-validator")
include("ktor-toolkit-cache")
include("ktor-toolkit-cache-lettuce")
include("ktor-toolkit-state-machine")
include("ktor-toolkit-state-machine-hateoas")

include("report")
