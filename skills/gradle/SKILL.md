---
name: gradle
description: >-
  The project's Gradle conventions — libs.versions.toml as the single source of versions,
  settings.gradle.kts, shared configuration through convention plugins, and above all choosing the
  right dependency configuration (api / implementation / compileOnly / runtimeOnly /
  testImplementation / ksp) rather than defaulting to implementation. Use when adding a dependency
  or a module, when a version is about to be written into a build script, when deciding what a
  module exposes to its consumers, when build logic is duplicated across modules, and when
  organising any build.gradle.kts or settings.gradle.kts.
---

# Gradle

## Four files, one job each

| File                        | Owns                                                   |
|-----------------------------|--------------------------------------------------------|
| `gradle/libs.versions.toml` | Every version, alias, bundle and plugin coordinate     |
| `settings.gradle.kts`       | What the build contains, and where things resolve from |
| `build.gradle.kts` (root)   | Almost nothing — plugins declared `apply false`        |
| `<module>/build.gradle.kts` | This module's own plugins, dependencies and tasks      |

A version in a module script, or a dependency block in the root, means one of these has taken on another's job.

## The version catalog

**Every version lives here. No exceptions.** A literal version in a build script — including a plugin's — is the thing this file exists to prevent:

```kotlin
// Wrong, even when it works
id("org.jlleitschuh.gradle.ktlint") version "14.2.0"

// Right
alias(libs.plugins.ktlint)
```

The catalog is grouped by origin, with comments, and alphabetical inside each group where that does not fight the grouping:

```toml
[versions]
kotlin = "2.3.10"
ktor = "3.4.1"
exposed = "1.1.1"
kotest = "6.1.7"

[libraries]
# Ktor
ktor-server-core = { module = "io.ktor:ktor-server-core-jvm", version.ref = "ktor" }
ktor-server-di = { module = "io.ktor:ktor-server-di", version.ref = "ktor" }
ktor-status-pages = { module = "io.ktor:ktor-server-status-pages", version.ref = "ktor" }

[bundles]
ktor-server = ["ktor-server-core", "ktor-server-cio", "ktor-server-di", "ktor-status-pages"]

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
ktor = { id = "io.ktor.plugin", version.ref = "ktor" }
```

**One `version.ref` per family.** Ktor's server, client and serialization artefacts are released together; three separate version keys is three
chances to drift into an unsupported combination.

**A bundle earns its place when the same set is declared in more than one module.** A bundle used once is indirection for its own sake, and a bundle
so broad that modules take dependencies they do not use is worse — it hides exactly the coupling this skill is about.

**Alias naming mirrors the coordinate**, so `libs.ktor.server.core` is guessable from
`io.ktor:ktor-server-core`. Do not invent short names; completion is what makes the catalog usable.

## settings.gradle.kts

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "catalog"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include("catalog-core", "catalog-adapters", "catalog-app")
include("report", "acceptance-tests")
```

**Repositories belong here, not in `allprojects`.** `FAIL_ON_PROJECT_REPOS` then makes it an error for a module to add its own, which is what stops a
stray repository appearing in one module and quietly changing what everything resolves.

**Do not put `mavenLocal()` in shared configuration.** It makes the build depend on whatever is in someone's `~/.m2`, so it passes locally and fails
in CI — or worse, passes in both while resolving different bytecode. Add it temporarily when testing an unpublished change, and remove it in the same
session.

**Type-safe project accessors** turn `project(":catalog-core")` into `projects.catalogCore`: a typo becomes a compile error and a renamed module fails
loudly.

When a module's directory does not match its name, map it explicitly:

```kotlin
include("backend-core")
project(":backend-core").projectDir = file("backend/core")
```

The foojay resolver lets Gradle download a matching JDK, so a contributor with the wrong Java installed still builds.

## Shared configuration

Every module needs the same Kotlin settings, toolchain, ktlint and test logging. There are two ways to avoid repeating it, and they are not
equivalent.

**`subprojects { }` in the root** is the familiar one and it works. Its costs are real: each module's build script no longer says what it applies, so
a reader has to know to look in the root; it fights the configuration cache and project isolation; and configuring an extension from outside forces
`extensions.configure<KtlintExtension>` gymnastics instead of the plugin's own DSL.

**A convention plugin is the target.** Put shared build logic in an included build and apply it by name:

```kotlin
// build-logic/src/main/kotlin/catalog.kotlin-conventions.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(25)
    compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    defaultCharacterEncoding = "UTF-8"
}
```

```kotlin
// catalog-core/build.gradle.kts
plugins {
    id("catalog.kotlin-conventions")
}
```

Now the module script states its own truth, the shared logic is ordinary Kotlin with completion and type checking, and a module that needs to opt out
simply does not apply the plugin.

Where a build already uses `subprojects { }` and is working, migrating is a task of its own, not something to fold into a feature branch. Start new
builds from convention plugins.

The root script keeps only the plugin declarations, all `apply false`, and the group and version:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    group = "com.example.catalog"
    version = providers.gradleProperty("version").get()
}
```

## Choosing a dependency configuration

**Do not default to `implementation`.** The configuration is a statement about what this module exposes, and getting it wrong either leaks
implementation detail into consumers or breaks their compile.

Work down this list; the first match is the answer:

| Question                                                                                                             | Configuration                                     |
|----------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| Does the type appear in this module's **public** signature — a parameter, return type, supertype or public property? | `api`                                             |
| Is it needed only at runtime — a JDBC driver, a Logback binding, an SLF4J implementation?                            | `runtimeOnly`                                     |
| Is it an **optional integration** a consumer opts into?                                                              | `compileOnly` **+** matching `testImplementation` |
| Does the module use it internally, and consumers need not know?                                                      | `implementation`                                  |
| Only tests need it?                                                                                                  | `testImplementation`                              |
| Only tests need it at runtime — a JUnit platform launcher, a driver?                                                 | `testRuntimeOnly`                                 |
| Is it an annotation processor?                                                                                       | `ksp`                                             |

**`api` is a promise.** It puts the dependency in the published POM and on every consumer's compile classpath, so removing it later is a breaking
change. In this toolkit the rule is exact: **`api` only when the type appears in a public signature.** `ktor-server-core` is `api` in every module
because
`ApplicationCall` is in their signatures; anything a module merely uses internally is
`implementation`.

**`compileOnly` is how an optional feature stays optional.** `Sort.toExposedQueryExpression` compiles against Exposed, but a consumer who never sorts
through Exposed should not inherit it:

```kotlin
compileOnly(libs.exposed.core)
testImplementation(libs.exposed.core)   // so the code is still compiled and tested here
```

The `testImplementation` is not optional — `compileOnly` is **not** on the test classpath, so without it the feature ships untested. Document the
requirement in the README, because the failure mode for a consumer who forgets is `NoClassDefFoundError` at runtime, not a compile error.

### In a service

The three production modules follow the pattern in the `ktor-toolkit:architecture` skill: `-core` and
`-adapters` compile against their libraries, and `-app` owns them at runtime.

```kotlin
// catalog-adapters/build.gradle.kts
dependencies {
    compileOnly(projects.catalogCore)
    compileOnly(libs.bundles.ktor.server)
    compileOnly(libs.bundles.exposed)

    testImplementation(projects.catalogCore)
    testImplementation(libs.bundles.ktor.server)
    testImplementation(libs.bundles.testing)
}

// catalog-app/build.gradle.kts
dependencies {
    implementation(projects.catalogCore)
    implementation(projects.catalogAdapters)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.exposed)
}
```

Only the deployable assembles a runtime classpath, so a library version is decided in exactly one place and an inner module cannot drag a transitive
dependency into the artefact.

**The trap is the test classpath.** Every `compileOnly` an inner module's tests touch needs a matching `testImplementation`, and the failure is a
`NoClassDefFoundError` in a test rather than a compile error — including for things pulled in indirectly, like a `KotlinLogging.logger {}` at file
level running as a class initialiser. When you add a `compileOnly`, add its `testImplementation` in the same edit.

## Module script order

Same order in every file, so a reader knows where to look:

```kotlin
plugins { }          // conventions first, then module-specific
application { }      // or ktor { }, kover { }, publishing { } — plugin extensions
dependencies { }
tasks { }            // custom tasks and task configuration last
```

Inside `dependencies`, group by configuration in the order above, and inside a group put project dependencies before external ones. Blank lines
between groups; no comment needed for the obvious ones.

## Multi-module direction

`-app → -adapters → -core`, never the other way, and never a cycle. Gradle detects a cycle and fails, which is the easy case; the damaging one is a
dependency that compiles fine and inverts the design —
`-core` depending on `-adapters` "just for one type" is how the domain acquires a database.

Two rules keep it honest:

- **A module exposes the minimum.** Prefer `implementation` over `api` between your own modules unless a type genuinely crosses the boundary — `api`
  makes every downstream module see the whole graph, and then nothing can be moved.
- **Register new production modules in `report`**, or their coverage is silently uncounted (load the `ktor-toolkit:kover` skill).

## Hygiene

Worth doing whenever you touch a build file:

- **Remove unused dependencies.** They slow the build, enlarge the image and inflate the CVE surface. A dependency nobody imports is a liability with
  no upside.
- **One declaration per plugin.** A plugin aliased in the root *and* applied in a module is a version conflict waiting for one of them to be bumped.
- **No duplicated repositories.** They belong in `settings.gradle.kts`, once.
- **Prefer modern APIs**: `tasks.withType<T>().configureEach { }` over `.all { }`,
  `tasks.register` over `tasks.create`, providers over eager `get()`. These keep configuration lazy, which is most of Gradle's performance.
- **Keep the configuration cache working.** When one task cannot support it, opt that task out —
  `notCompatibleWithConfigurationCache("…")` — rather than disabling it for the build.
- **A public API change carries its `api/` dump** in the same commit (load the `ktor-toolkit:commit` skill).

## Common mistakes

| Mistake                                             | Why it hurts                                                           |
|-----------------------------------------------------|------------------------------------------------------------------------|
| A literal version in a build script                 | The catalog stops being the single source; upgrades miss it            |
| Defaulting everything to `implementation`           | Either leaks internals or breaks a consumer's compile                  |
| `api` for something not in a public signature       | Puts it in the POM; removing it later is a breaking change             |
| `compileOnly` with no matching `testImplementation` | The feature is never compiled into a test — it ships untested          |
| `mavenLocal()` in shared configuration              | Resolves differently on each machine; green locally, red in CI         |
| Repositories in `allprojects`                       | One module can silently change what everything resolves                |
| A separate version key per artefact of one family   | Mismatched Ktor or Kotlin versions at runtime                          |
| A bundle used by exactly one module                 | Indirection with no payoff                                             |
| A bundle so broad modules take what they do not use | Hides the coupling the configurations exist to express                 |
| `-core` depending on `-adapters` "for one type"     | The domain acquires a framework, and the layout stops meaning anything |
| Shared logic in `subprojects { }` for a new build   | Module scripts no longer state what they apply                         |
| `tasks.create` / `.all { }`                         | Eager configuration; every task is configured on every invocation      |
| New module missing from `report`                    | Its coverage silently stops counting                                   |
