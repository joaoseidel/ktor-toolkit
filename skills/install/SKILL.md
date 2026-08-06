---
name: install
description: >-
  Adds Ktor Toolkit to a project — picks the modules, wires the Gradle dependencies and version
  catalog entries, installs the required Ktor plugins (ContentNegotiation, StatusPages,
  RequestValidation), installs the ktor-toolkit skill collection itself, and verifies the whole
  thing compiles. Use whenever someone wants to start using the toolkit, add another of its modules
  to a project already using it, install or update these skills, or is stuck on
  "Could not find com.luizalabs.ktor-toolkit:...". Also use when a task routed to a feature skill
  (pagination, hateoas, validation, problem-details, expand, cache) but that module turns out not
  to be on the classpath yet.
---

# Installing Ktor Toolkit

## Two different things are called "install"

**The libraries** — Gradle dependencies on `com.luizalabs.ktor-toolkit:*`, so the project can call
`call.pagination`, `problemDetails { }`, `rulesFor<T> { }`.

**The skills** — this collection, so an agent working in the project knows how the maintainers
intend those APIs to be used.

They are independent and a project usually wants both. Ask which is in scope if the request is
ambiguous; do not silently do only one. A project with the libraries but no skills gets endpoints
that compile and drift; a project with the skills but no libraries gets advice it cannot follow.

## Step 1 — Choose the modules

Every module stands alone. Install what the project needs now — another one is two lines later.

Ask the user, framed by what they are building rather than by module name, since the names mean
nothing before you have used them:

| If the API… | Install |
|---|---|
| returns lists of anything | `ktor-toolkit-paginator` |
| should advertise next/prev pages or related actions in the payload | `ktor-toolkit-hateoas` |
| accepts request bodies with rules about them | `ktor-toolkit-validator` |
| returns errors — so, every API | `ktor-toolkit-problem-details` |
| lets clients pull in related resources with `?expand=` | `ktor-toolkit-expander` |
| has read endpoints worth caching | `ktor-toolkit-cache` |

Two things to say while asking, because they change the answer:

- **`hateoas` brings `paginator` with it** as an `api` dependency. Selecting hateoas alone is fine
  and complete — do not also list paginator.
- **`problem-details` is the one to default to.** Every service returns errors, and it is the
  module that decides what a client sees when something goes wrong. A service that skips it answers
  validation failures with Ktor's default HTML error page.

If the user says "all of them", that is a legitimate answer for a greenfield service — take it.

## Step 2 — Declare the versions in the catalog

The modules are published to Maven Central, so a project that already resolves anything else needs
no repository changes. If the build declares repositories explicitly, `mavenCentral()` is all this
requires.

Versions belong in `gradle/libs.versions.toml`, never inline in a build script — see
`ktor-toolkit:gradle` for why this is not negotiable. Add one version key and one library alias per
selected module:

```toml
[versions]
ktor-toolkit = "1.0.0"

[libraries]
ktor-toolkit-paginator       = { module = "com.luizalabs.ktor-toolkit:ktor-toolkit-paginator",       version.ref = "ktor-toolkit" }
ktor-toolkit-hateoas         = { module = "com.luizalabs.ktor-toolkit:ktor-toolkit-hateoas",         version.ref = "ktor-toolkit" }
ktor-toolkit-validator       = { module = "com.luizalabs.ktor-toolkit:ktor-toolkit-validator",       version.ref = "ktor-toolkit" }
ktor-toolkit-problem-details = { module = "com.luizalabs.ktor-toolkit:ktor-toolkit-problem-details", version.ref = "ktor-toolkit" }
ktor-toolkit-expander        = { module = "com.luizalabs.ktor-toolkit:ktor-toolkit-expander",        version.ref = "ktor-toolkit" }
ktor-toolkit-cache           = { module = "com.luizalabs.ktor-toolkit:ktor-toolkit-cache",           version.ref = "ktor-toolkit" }
```

One shared `ktor-toolkit` version key rather than one per module: the modules are released together
and mixing versions across them is a bug, not a feature. Delete the aliases for modules that were
not selected — an unused alias is an invitation to use it without thinking.

## Step 3 — Depend on them

In a single-module Ktor service, `implementation` is right:

```kotlin
dependencies {
    implementation(libs.ktor.toolkit.paginator)
    implementation(libs.ktor.toolkit.problem.details)
}
```

In a multi-module service it depends on whether toolkit types cross the module's own boundary —
a `web` module whose public functions return `PagedResponse` needs `api`, not `implementation`.
`ktor-toolkit:gradle` covers that decision; ask it rather than guessing.

**What does not come along transitively.** The modules declare Ktor and kotlinx-serialization as
`api` dependencies, so those arrive for free. Content negotiation does not — it is what actually
turns the responses into JSON, and every module needs it:

```kotlin
implementation(libs.ktor.content.negotiation)
implementation(libs.ktor.serialization.kotlinx.json)
```

Without it a route returning `PagedResponse` fails at runtime with "Response pipeline couldn't
transform", which reads like a toolkit bug and is not one.

**Optional integrations.** Three features are compiled `compileOnly` against libraries the module
deliberately does not carry, so projects that do not use them do not pay for them. Add the
dependency only alongside the feature:

| Using | Add |
|---|---|
| `Sort.toExposedQueryExpression(...)` | `org.jetbrains.exposed:exposed-core` |
| `Sort.toMongoSortExpression(...)` | `org.mongodb:mongodb-driver-core` — already transitive to any MongoDB driver |
| `LettuceCache` | `io.lettuce:lettuce-core` |

These fail at runtime with `NoClassDefFoundError`, not at compile time — the module compiled fine
against a class the consumer never supplied. If a user reports that, this table is the answer.

## Step 4 — Wire the Ktor plugins

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

`paginator`, `hateoas`, `expander` and `cache` need no plugin — they are call extensions and plain
objects. Do not invent an `install()` for them.

Leave `problemDetails { }` in place even before any rules exist. It is the module's whole point that
the *unhandled* cases are covered, and a service that adds it later has already shipped a different
error shape to its clients.

**Two opt-ins the compiler will ask for:**

- `JsonNamingStrategy` is `@ExperimentalSerializationApi`. Setting `namingStrategy` needs
  `-opt-in=kotlinx.serialization.ExperimentalSerializationApi` in the module's `compilerOptions`,
  or an `@OptIn` on the function.
- The temporal validation rules sit on `kotlin.time`. If the compiler asks for
  `kotlin.time.ExperimentalTime` — usually when a test passes an explicit `now` — add that opt-in
  the same way.

Prefer the compiler flag over scattering `@OptIn` annotations: this is a project-wide decision that
was already made by depending on the module, and repeating it at each call site adds noise without
adding information.

## Step 5 — Install the skills

**Project scope, always, unless the user explicitly asks for global.** These skills encode one
project's conventions; installed globally they follow the user into unrelated repositories and give
Ktor Toolkit advice about codebases that have never heard of it.

For Claude Code, the plugin install keeps the `/ktor-toolkit:` namespace intact. It is interactive,
so hand the two commands to the user rather than running them:

```
/plugin marketplace add joaoseidel/ktor-toolkit
```

```
/plugin install ktor-toolkit@ktor-toolkit
```

For any other agent, or to vendor the files into the repository, use the skills CLI. Note the verb
is `add` — there is no `install` subcommand, and `experimental_install` only restores from an
existing `skills-lock.json`:

```bash
npx skills add joaoseidel/ktor-toolkit --skill '*'
```

Install the whole collection rather than a subset. The skills route to each other — `endpoint`
loads `pagination`, `pagination` refers to `architecture` — and a partial install turns those into
dead references at the exact moment they were needed.

Finally, make the entry point fire. `ktor-toolkit:start` triggers off its description, which is
reliable but not certain; a line in the project's `CLAUDE.md` makes it explicit:

```markdown
This service is built with Ktor Toolkit. Run `/ktor-toolkit:start` before implementing anything.
```

## Step 6 — Verify, then say what happened

Verification is compiling, not reading the diff back:

```bash
./gradlew compileKotlin
```

If the project has a route already, the honest end-to-end check is one request through it — a
`PagedResponse` that serializes proves the dependency, the content negotiation and the plugin
wiring in one shot, and those are the three things that break.

Then report, in this order:

1. **Modules installed**, and the one-line reason each was chosen.
2. **Files changed** — catalog, build script, `Application.kt`, `CLAUDE.md`.
3. **Skills installed**, and that `/ktor-toolkit:start` now runs first.
4. **What to do next** — normally `/ktor-toolkit:endpoint` for the first route.

Keep it to a short list. The user asked for a working project, and the evidence that they have one
is the compile, not the prose.

## When it goes wrong

| Symptom | Cause |
|---|---|
| `Could not find com.luizalabs.ktor-toolkit:...` | `mavenCentral()` missing from the build's repositories |
| `Response pipeline couldn't transform` | ContentNegotiation missing (Step 3) |
| `NoClassDefFoundError` on Exposed / Lettuce / MongoDB classes | Optional integration used without its dependency (Step 3) |
| Validation failures return HTML, not `problem+json` | `problemDetails { }` missing from StatusPages (Step 4) |
| `JsonNamingStrategy` is experimental and its usage must be marked | Opt-in missing (Step 4) |
| Skills resolve as `/start` instead of `/ktor-toolkit:start` | Installed via the skills CLI rather than as a Claude Code plugin |
