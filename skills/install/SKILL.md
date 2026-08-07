---
name: install
description: >-
    Adds Ktor Toolkit to a project — checks the JVM target, picks the modules, wires the version
    catalog and dependencies, installs the required Ktor plugins, installs these skills, and verifies
    it compiles. Use to start using the toolkit, to add another of its modules, when stuck on
    "Could not find io.github.joaoseidel:...", or when a task routed to a feature skill whose module
    is not on the classpath yet.
---

# Installing Ktor Toolkit

## Two different things are called "install"

**The libraries** — Gradle dependencies on `io.github.joaoseidel:*`, so the project can call `call.pagination`, `problemDetails { }` and
`rulesFor<T> { }`.

**The skills** — this collection, so an agent working in the project knows how these APIs are meant to be used.

They are independent and most projects want both. Ask which is in scope when the request is ambiguous; do not silently do one. Libraries without
skills gives endpoints that compile and drift. Skills without libraries gives advice nobody can follow.

## Step 1 — Check the JVM target

**The modules require Java 21 or newer.** On an older toolchain the dependency resolves and the *compile* fails with `class file has wrong version`,
which reads like a corrupt artifact and is not one.

```bash
grep -rn "jvmToolchain\|sourceCompatibility\|languageVersion" --include="*.gradle.kts" .
```

Below 21, stop here: raise the toolchain, or tell the user the toolkit is not usable on this project. Do not spend time on the error itself.

## Step 2 — Choose the modules

Every module stands alone. Install what the project needs now; another is two lines later.

Ask in terms of what they are building, not module names — the names mean nothing before you have used them:

| If the API…                                                        | Install                        |
|--------------------------------------------------------------------|--------------------------------|
| returns lists of anything                                          | `ktor-toolkit-paginator`       |
| should advertise next/prev pages or related actions in the payload | `ktor-toolkit-hateoas`         |
| accepts request bodies with rules about them                       | `ktor-toolkit-validator`       |
| returns errors — so, every API                                     | `ktor-toolkit-problem-details` |
| lets clients pull in related resources with `?expand=`             | `ktor-toolkit-expander`        |
| has read endpoints worth caching                                   | `ktor-toolkit-cache`           |

Two things to say while asking, because they change the answer:

- **`hateoas` brings `paginator` with it** as an `api` dependency, because `PagedResponse` is in `toResource`'s signature. Selecting hateoas alone is
  complete — do not also list paginator.
- **Default to `problem-details`.** Every service returns errors, and this is the module that decides what a client sees when one happens. Skip it and
  validation failures answer with Ktor's default HTML error page.

"All of them" is a legitimate answer for a greenfield service. Take it.

## Step 3 — Declare the versions

Published to Maven Central, so a build that already resolves anything else needs no repository change. Where repositories are declared explicitly,
`mavenCentral()` is all this needs.

Versions belong in `gradle/libs.versions.toml`, never inline — load the `ktor-toolkit:gradle` skill for why. One version key, one alias per selected
module:

```toml
[versions]
ktor-toolkit = "1.0.0"

[libraries]
ktor-toolkit-paginator = { module = "io.github.joaoseidel:ktor-toolkit-paginator", version.ref = "ktor-toolkit" }
ktor-toolkit-hateoas = { module = "io.github.joaoseidel:ktor-toolkit-hateoas", version.ref = "ktor-toolkit" }
ktor-toolkit-validator = { module = "io.github.joaoseidel:ktor-toolkit-validator", version.ref = "ktor-toolkit" }
ktor-toolkit-problem-details = { module = "io.github.joaoseidel:ktor-toolkit-problem-details", version.ref = "ktor-toolkit" }
ktor-toolkit-expander = { module = "io.github.joaoseidel:ktor-toolkit-expander", version.ref = "ktor-toolkit" }
ktor-toolkit-cache = { module = "io.github.joaoseidel:ktor-toolkit-cache", version.ref = "ktor-toolkit" }
```

**One shared version key, not one per module.** They are released together and mixing versions across them is unsupported. Delete the aliases for
modules nobody selected — an unused alias is an invitation to use it without thinking.

## Step 4 — Depend on them

In a single-module service, `implementation`:

```kotlin
dependencies {
    implementation(libs.ktor.toolkit.paginator)
    implementation(libs.ktor.toolkit.problem.details)
}
```

In a multi-module service it depends on whether toolkit types cross the module's own boundary — a `web` module whose public functions return
`PagedResponse` needs `api`. Load the `ktor-toolkit:gradle` skill for that decision, and the `ktor-toolkit:architecture` skill for which module each
type belongs in.

**Content negotiation does not come along.** Ktor and kotlinx-serialization are `api` dependencies and arrive for free; the thing that actually turns
responses into JSON does not, and every module needs it:

```kotlin
implementation(libs.ktor.content.negotiation)
implementation(libs.ktor.serialization.kotlinx.json)
```

Without it a route returning `PagedResponse` fails at runtime with "Response pipeline couldn't transform", which reads like a toolkit bug and is not.

**Optional integrations.** Three features compile `compileOnly` against libraries their module deliberately does not carry, so projects that skip the
feature do not pay for it. Add the dependency only alongside the feature:

| Using                              | Add                                                                          |
|------------------------------------|------------------------------------------------------------------------------|
| `Sort.toExposedQueryExpression(…)` | `org.jetbrains.exposed:exposed-core`                                         |
| `Sort.toMongoSortExpression(…)`    | `org.mongodb:mongodb-driver-core` — already transitive to any MongoDB driver |
| `LettuceCache`                     | `io.lettuce:lettuce-core`                                                    |

These fail with `NoClassDefFoundError` at runtime, not at compile time: the module compiled fine against a class the consumer never supplied.

## Step 5 — Import from the right package

**The package root is not the group id.** Coordinates are `io.github.joaoseidel`, because only `io.github.<user>` is verifiable on Maven Central
through a GitHub account. The Kotlin packages are `com.github.joaoseidel.ktor.toolkit.*`, so an import guessed from the dependency line does not
resolve:

```kotlin
import com.github.joaoseidel.ktor.toolkit.paginator.pagination          // ApplicationCall.pagination
import com.github.joaoseidel.ktor.toolkit.paginator.data.Pagination
import com.github.joaoseidel.ktor.toolkit.paginator.web.PagedResponse
import com.github.joaoseidel.ktor.toolkit.hateoas.data.resource
import com.github.joaoseidel.ktor.toolkit.problemdetails.problemDetails
import com.github.joaoseidel.ktor.toolkit.validator.rulesFor
import com.github.joaoseidel.ktor.toolkit.expander.data.ExpandSpec
import com.github.joaoseidel.ktor.toolkit.cache.withCache
```

Note **`problemdetails`** — one word, no hyphen and no dot, unlike the artifact name.

Within each module: domain types under `data`, wire types under `web`, call extensions and plugin installers at the module root. One level deeper
matters for the validator — every rule lives in `…validator.validators`, one import per rule the file uses:

```kotlin
import com.github.joaoseidel.ktor.toolkit.validator.validators.blank    // should notBe blank()
```

Two receivers are easy to get wrong: `withCache` extends **`ApplicationRequest`**, so it is `call.request.withCache(…)`, and `toResource` takes the
routing call, so it is `.toResource(call)`.

## Step 6 — Wire the Ktor plugins

A dependency on its own changes nothing. Install only the plugins the selected modules need:

```kotlin
fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    // ktor-toolkit-problem-details
    install(StatusPages) {
        problemDetails {
            namingStrategy = JsonNamingStrategy.SnakeCase
        }
    }

    // ktor-toolkit-validator
    install(RequestValidation) {
        rulesFor<CreateBookRequest> {
            property(CreateBookRequest::title) { should notBe blank() }
        }
    }
}
```

**`paginator`, `hateoas`, `expander` and `cache` need no plugin** — they are call extensions and plain objects. Do not invent an `install()` for them.

**Leave `problemDetails { }` in place before any rules exist.** Covering the *unmapped* cases is the module's whole point, and a service that adds it
later has already shipped a different error shape to its clients.

**Two opt-ins the compiler will ask for:**

- `JsonNamingStrategy` is `@ExperimentalSerializationApi`. Setting `namingStrategy` needs `-opt-in=kotlinx.serialization.ExperimentalSerializationApi`
  in the module's `compilerOptions`.
- The temporal validation rules sit on `kotlin.time`. Add `kotlin.time.ExperimentalTime` the same way if the compiler asks — usually when a test
  passes an explicit `now`.

Use the compiler flag rather than scattering `@OptIn`: depending on the module already made this decision project-wide, and repeating it per call site
adds noise without information.

## Step 7 — Install the skills

**Project scope, unless the user explicitly asks for global.** These encode one project's conventions; installed globally they follow the user into
unrelated repositories and give Ktor Toolkit advice about codebases that have never heard of it.

For Claude Code, the plugin install keeps the `/ktor-toolkit:` namespace. Both commands are interactive — hand them to the user rather than running
them:

```
/plugin marketplace add joaoseidel/ktor-toolkit
```

```
/plugin install ktor-toolkit@ktor-toolkit
```

For any other agent, or to vendor the files into the repository, use the skills CLI. The verb is `add` — there is no `install` subcommand, and
`experimental_install` only restores from an existing `skills-lock.json`:

```bash
npx skills add joaoseidel/ktor-toolkit --skill '*'
```

**Install the whole collection, not a subset.** The skills route to each other — `endpoint` sends you to `pagination`, which sends you to
`architecture` — and a partial install turns those into dead references at the moment they were needed.

Then make the entry point fire. `ktor-toolkit:start` triggers off its description, which is reliable but not certain; a line in the project's
`CLAUDE.md` makes it explicit:

```markdown
This service is built with Ktor Toolkit. Run `/ktor-toolkit:start` before implementing anything.
```

## Step 8 — Verify, then report

Verification is compiling, not reading the diff back:

```bash
./gradlew compileKotlin
```

Where a route already exists, one request through it is the honest end-to-end check: a `PagedResponse` that serialises proves the dependency, the
content negotiation and the plugin wiring at once, and those are the three things that break.

Then report, in this order:

1. **Modules installed**, one line each on why.
2. **Files changed** — catalog, build script, `Application.kt`, `CLAUDE.md`.
3. **Skills installed**, and that `/ktor-toolkit:start` now runs first.
4. **What to do next** — normally load `ktor-toolkit:endpoint` for the first route.

Keep it short. The user asked for a working project, and the evidence is the compile, not the prose.

## When it goes wrong

| Symptom                                                           | Cause                                                             |
|-------------------------------------------------------------------|-------------------------------------------------------------------|
| `class file has wrong version 65.0`                               | The project targets below Java 21 (Step 1)                        |
| `Could not find io.github.joaoseidel:...`                         | `mavenCentral()` missing from the build's repositories (Step 3)   |
| Unresolved reference on `import io.github.joaoseidel...`          | The packages are `com.github.joaoseidel...` (Step 5)              |
| Unresolved reference on `blank()`, `email()`, `size()`            | Each rule is its own import from `…validator.validators` (Step 5) |
| `Response pipeline couldn't transform`                            | ContentNegotiation missing (Step 4)                               |
| `NoClassDefFoundError` on Exposed / Lettuce / MongoDB classes     | Optional integration used without its dependency (Step 4)         |
| Validation failures return HTML, not `problem+json`               | `problemDetails { }` missing from StatusPages (Step 6)            |
| `JsonNamingStrategy` is experimental and its usage must be marked | Opt-in missing (Step 6)                                           |
| Skills resolve as `/start` instead of `/ktor-toolkit:start`       | Installed via the skills CLI rather than as a Claude Code plugin  |

## Consuming an unreleased change

Every merge to `main` publishes a snapshot:

```kotlin
repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}
```

For a change that is not merged, `make publish_local` in a checkout of the toolkit puts it in the local Maven repository. Add `mavenLocal()` only for
that session and remove it in the same one — the `ktor-toolkit:gradle` skill covers why it must not stay.
