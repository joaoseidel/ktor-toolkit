---
name: kover
description: >-
  Coverage with Kotlinx Kover — a dedicated `report` aggregation module, line and branch bounds,
  ratcheting a threshold rather than declaring one, and the rule that exclusions are only for code
  unreachable by construction. Use when setting up coverage, adding a module that must be counted,
  wiring coverage into CI, or when a gate fails and someone reaches for an exclusion.
---

# Coverage with Kover

## When the project has no coverage set up

If there is no Kover plugin and no `report` module, coverage is a thing to propose rather than assume. **Say what you would add** — the plugin, a
`report` module aggregating the production modules, and a bound set at whatever the codebase measures today — **and wait for a yes.**

It is worth proposing when a gate would have caught the thing you are working on, and worth skipping when the user asked for one endpoint. Adding a
build gate to someone's project is a standing constraint on everyone who commits to it after you; that is their call, not a detail of your task.

## One module aggregates, the others are measured

Coverage is a property of the whole codebase, not of each module in isolation — a class in `-core` is often exercised by a test in
`acceptance-tests`, and per-module reports under-report every shared type.

Put the whole configuration in one `report` module. The three imports are required; without them none of this compiles.

```kotlin
// report/build.gradle.kts
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.tasks.KoverReport

plugins {
    alias(libs.plugins.kotlinx.kover)
}

val koverSkip: Boolean = providers.gradleProperty("koverSkip").map(String::toBoolean).getOrElse(false)
val koverCoverageLineRate: Int = 65
val koverCoverageBranchRate: Int = 50

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

kover {
    currentProject {
        sources {
            excludeJava = true
        }
    }

    reports {
        filters {
            excludes {
                // One comment per exclusion, saying why a test cannot reach this code.
            }
        }

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

Four things in there are load-bearing:

**`filters` nests inside `reports`, not inside `kover`.** At the top level it silently applies to nothing.

**Add every new production module to `coveredProjects`.** Nothing warns you: the build stays green, the report generates, and the new module's code is
invisible. The `ktor-toolkit:architecture` skill names this too, because it is the failure that hides itself.

**Keep the `dependsOn`.** Without it the report is built from whatever execution data is on disk — stale or absent — and the number is fiction.

**Name each rule.** `rule("Branch Coverage")` is what prints when the gate fails; an unnamed rule reports a bound and leaves you guessing which.

Two more, less obvious:

**Gate line and branch separately.** Line coverage alone is easy to satisfy and says little — a `when` with four arms and one test scores full line
coverage on the arm it took. Branch coverage finds the case nobody thought about.

**`onCheck = true` on both reports.** They then exist whenever the gate ran, rather than only when someone remembered a separate task. HTML lands at
`report/build/reports/kover/html/index.html` for humans, XML beside it at `report.xml` for a coverage service or a PR annotation.

`-PkoverSkip=true` unblocks a spike or a bisect without a commit someone forgets to revert.

## Choosing a threshold

**Set the bound at the number you have today, then ratchet it.** Measure first — run the report, read the total, and put that number in the file,
rounded down. A threshold that only ever moves up turns coverage into a one-way constraint: nothing new can arrive uncovered, and the existing gap
closes as code is touched.

Starting at 40 and reaching 75 over a quarter is worth more than declaring 90 and disabling it in week three. That second outcome is the common one,
and it leaves the project with no gate at all.

**A service cannot reach the numbers a library can, and should not try.** A pure library is small, has no I/O, and every public declaration is
something a caller can invoke, so 100% is honest there. An application has startup code, framework glue, config parsing and infrastructure adapters
whose last few percent cost more than they return. If you have seen 100% quoted as the standard, that is where it came from — do not import the number
without the context.

**Never lower a threshold to make a build pass.** Lowering it is a decision to accept less, and it should look like one: its own commit, with a body
explaining what changed (load the `ktor-toolkit:commit` skill). The reflex to reach for when the gate fails is a test.

## Exclusions

**The rule: exclude code that is unreachable by construction, never code that is merely untested.**

Untested code is a gap you have decided not to close today. Excluding it hides the decision and the number stops meaning anything. Unreachable code is
different — a test genuinely cannot execute it, so counting it makes the gate unachievable for a reason that has nothing to do with test quality.

Every exclusion carries the reason, in a comment, next to it:

```kotlin
filters {
    excludes {
        // The compiler emits a non-inlined copy of a public inline function for the
        // declaration itself; callers inline the body instead, so that copy never runs.
        annotatedBy("com.example.catalog.UnreachableBytecode")

        // Every property of this DTO is optional, so kotlinx.serialization guards its
        // generated `throwMissingFieldException` with `(0 and seen) != 0` — always false.
        // Named precisely: the Companion, which holds the parsing this class is really
        // about, stays measured.
        classes("com.example.catalog.adapters.web.book.BookSearchRequest")
    }
}
```

**Use a marker annotation rather than a class name.** It puts the justification next to the code instead of in a build file nobody opens, and it
survives a rename:

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

**Exclude the narrowest thing that works.** Naming one class leaves its `$Companion` — often where the parsing actually lives — still measured. A
package-level wildcard would silently take that with it, which is the code most worth covering.

The usual legitimate candidates: compiler-generated bytecode that cannot run, generated sources, and `Application.kt`-style entry points whose only
content is framework wiring already exercised by an acceptance test. Everything else is a test you have not written.

## CI

Coverage is its own step, calling the same target a developer runs locally:

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

`if: always()` is the line that matters. The run you most want a report from is the one that failed, and a plain `upload-artifact` step is skipped
when an earlier step fails — leaving you a number and no way to see which lines produced it.

`make coverage` runs `koverHtmlReport` and `koverVerify`, so the report is produced and the bound enforced in one step (load the
`ktor-toolkit:makefile` skill).

## Read the report, not the number

**Coverage measures execution, not verification.** A test that calls a function and asserts nothing raises the number exactly as much as a good one.
The gate is a floor; the HTML report is the tool.

Use it to find what you did not think of — an uncovered branch is a case with no test, and reading them beats staring at the code for test ideas. Do
not close the last few percent with tests that assert nothing: that converts a real signal into decoration, and nobody can then tell which tests mean
anything. Load the `ktor-toolkit:tests` skill for what makes a test worth having.

## Failures that stay green

Every one of these leaves the build passing and the number looking fine. Check them by reading, because nothing else will.

| Mistake                                   | What it silently does                                    |
|-------------------------------------------|----------------------------------------------------------|
| New module missing from `coveredProjects` | Its code is uncounted; the total goes *up*               |
| No `dependsOn` on the test tasks          | The report is built from stale or absent data            |
| `filters` outside `reports`               | Every exclusion applies to nothing                       |
| Excluding a class because it is untested  | Hides the gap; the number stops meaning anything         |
| A package wildcard exclusion              | Takes real code with it, usually the part worth covering |
| Tests written to raise the number         | Coverage stops being a signal, and nobody can tell which |
| `upload-artifact` without `if: always()`  | No report from the one run you needed it from            |
