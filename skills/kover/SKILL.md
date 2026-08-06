---
name: kover
description: >-
  Code coverage with Kotlinx Kover — the dedicated `report` aggregation module, HTML and XML
  reports, line and branch verification bounds, and the strict rule for exclusions (unreachable by
  construction, never merely untested). Use when setting up coverage for a new service, adding a
  module that must be counted, choosing or raising a threshold, wiring coverage into CI, or when a
  coverage gate fails and someone is about to add an exclusion.
---

# Coverage with Kover

## One module aggregates, the others are measured

Coverage is a property of the whole codebase, not of each module in isolation — a class in `-core`
is often exercised by a test in `acceptance-tests`. Per-module reports miss that entirely and
under-report every shared type.

So there is a dedicated `report` module whose only job is to aggregate:

```kotlin
// report/build.gradle.kts
plugins {
    alias(libs.plugins.kotlinx.kover)
}

/** Every production module contributes to the aggregated coverage report. */
val coveredProjects = listOf(
    ":catalog-core",
    ":catalog-adapters",
    ":catalog-app",
)

dependencies {
    coveredProjects.forEach { kover(project(it)) }
}

tasks.withType<KoverReport>().configureEach {
    dependsOn(coveredProjects.map { project(it).tasks.named("test") })
}
```

**A new production module must be added to that list.** Nothing warns you: the build stays green,
the report is generated, and the new module's code is simply invisible. That silence is why this is
worth checking whenever a module appears — and why `ktor-toolkit:architecture` names it too.

**The `dependsOn` matters more than it looks.** Without it a report can be produced from whatever
execution data happens to be on disk — stale from a previous run, or absent — and the number is
fiction. Wiring the report to the tests makes `make coverage` honest on a clean checkout.

## Configuration

```kotlin
kover {
    currentProject {
        sources {
            excludeJava = true
        }
    }

    reports {
        total {
            xml { onCheck = true }
            html { onCheck = true }
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
```

**Name the rules.** `rule("Branch Coverage")` is what appears when the gate fails; an unnamed rule
reports a bound and leaves you to work out which.

**Line and branch are separate rules on purpose.** Line coverage alone is easy to satisfy and says
little — a `when` with four arms and one test scores full line coverage on the arm it took. Branch
coverage is the one that finds the case nobody thought about.

**`xml` for machines, `html` for humans.** `onCheck = true` generates both as part of `check`, so
they exist whenever the gate ran rather than only when someone remembered a separate task. The HTML
lands at `report/build/reports/kover/html/index.html` and the XML beside it at `report.xml`, which
is what a coverage service or a PR annotation consumes.

Hold the thresholds in named values at the top of the file, and make the gate skippable by property
rather than by editing:

```kotlin
val koverSkip: Boolean = providers.gradleProperty("koverSkip").map(String::toBoolean).getOrElse(false)
val koverCoverageLineRate: Int = 100
val koverCoverageBranchRate: Int = 100
```

`-PkoverSkip=true` then unblocks a spike or a bisect without a commit that someone forgets to revert.

## Choosing a threshold

**This toolkit gates at 100% line and 100% branch.** That is achievable because it is a library:
small, pure, no I/O, and every public declaration is something a consumer can call — so anything
unreachable is a design smell rather than a fact of life.

**Do not copy 100% into a service.** An application has startup code, framework glue and
infrastructure adapters whose last 5% costs more than it returns, and a gate nobody can meet gets
switched off — at which point you have no gate at all.

For a service, **set the bound at the number you have today and ratchet it.** A threshold that only
ever moves up turns coverage into a one-way constraint: nothing new can arrive uncovered, and the
existing gap closes as code is touched. Starting at 40 and reaching 75 over a quarter is worth more
than declaring 90 and disabling it in week three.

**Never lower a threshold to make a build pass.** Lowering it is a decision to accept less, and it
should look like one: its own commit, with a body explaining what changed (`ktor-toolkit:commit`).
The reflex to reach for when the gate fails is a test.

## Exclusions

**The rule: exclude code that is unreachable by construction, never code that is merely untested.**

Untested code is a gap you have decided not to close today. Excluding it hides the decision and the
number stops meaning anything. Unreachable code is different — a test genuinely cannot execute it,
so counting it makes the gate unachievable for a reason that has nothing to do with test quality.

Both exclusions in this repository are of the second kind, and both carry the reason:

```kotlin
filters {
    excludes {
        // The compiler emits a non-inlined copy of a public inline function for the
        // declaration itself; callers inline the body instead, so that copy never runs.
        annotatedBy("com.luizalabs.ktor.toolkit.cache.UnreachableBytecode")

        // Every property of PaginationRequest is optional, so kotlinx.serialization guards
        // its generated `throwMissingFieldException` with `(0 and seen) != 0` — always
        // false. Named precisely: PaginationRequest$Companion, which holds the parsing this
        // class is really about, stays measured.
        classes("com.luizalabs.ktor.toolkit.paginator.web.PaginationRequest")
    }
}
```

**Prefer an annotation over a name.** A marker annotation puts the justification next to the code
rather than in a build file nobody opens, and it survives a rename:

```kotlin
/**
 * Marks a declaration whose bytecode a test cannot execute, so the coverage gate leaves it out.
 *
 * This is for code that is unreachable *by construction*, never for code that is merely untested.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
internal annotation class UnreachableBytecode
```

**Exclude the narrowest thing that works.** `classes("…PaginationRequest")` names one class and
leaves `PaginationRequest$Companion` — where the parsing actually lives — measured. A package-level
wildcard would have silently taken the parser with it, which is the code most worth covering.

The usual legitimate candidates: compiler-generated bytecode that cannot run, generated sources, and
`Application.kt`-style entry points whose only content is framework wiring already exercised by an
acceptance test. Everything else is a test you have not written.

## CI

Coverage is its own step, calling the same target a contributor runs:

```yaml
- name: Coverage
  run: make coverage

- name: Upload reports
  if: always()
  uses: actions/upload-artifact@v7
  with:
    name: reports
    path: |
      **/build/reports/tests/test
      report/build/reports/kover
    if-no-files-found: ignore
```

`if: always()` is the important line. The run you most want the report from is the one that failed,
and a plain `upload-artifact` step is skipped when a previous step fails — leaving you with a number
and no way to see which lines it came from.

`make coverage` runs `koverHtmlReport` and `koverVerify`, so the report is produced and the bound
enforced in one step (`ktor-toolkit:makefile`).

## What coverage does and does not tell you

**Coverage measures execution, not verification.** A test that calls a function and asserts nothing
raises the number exactly as much as a good one. 100% line coverage is a statement that no code is
unreached — not that any of it is correct.

So treat the gate as a floor and the report as a tool:

- **Use the HTML report to find what you did not think of.** An uncovered branch is a case that has
  no test, and reading them is a better source of test ideas than staring at the code.
- **A sudden drop is a signal.** New code arriving uncovered is easier to fix in the pull request
  that added it than in the quarter that follows.
- **Do not chase the last percent with tests that assert nothing.** That converts a real signal into
  a decoration, and the next person cannot tell which tests mean anything.

`ktor-toolkit:tests` covers what makes a test worth having in the first place.

## Common mistakes

| Mistake | Why it hurts |
|---|---|
| New module missing from the `report` list | Its code is silently uncounted and the total looks fine |
| No `dependsOn` on the test tasks | The report can be built from stale or absent execution data |
| Copying the toolkit's 100% into a service | Unmeetable, so it gets disabled — and then there is no gate |
| Lowering the threshold to go green | Accepts less, invisibly; the fix is a test |
| Excluding a class because it is untested | Hides the gap and makes the number meaningless |
| A package wildcard exclusion | Takes real code with it, usually the part worth covering |
| An exclusion with no comment | Nobody can tell later whether it is still justified |
| Line coverage only | A four-arm `when` passes on one arm |
| `upload-artifact` without `if: always()` | No report from the run that failed |
| Tests written to raise the number | Coverage stops being a signal at all |
