---
name: architecture
description: >-
    Where a file goes in a Ports & Adapters Ktor service — the -core / -adapters / -app module split,
    the dependency direction between them, and which module each toolkit type belongs in. Use before
    creating any file, when placing a route, use case, port, adapter, entity or DTO, and whenever a
    core class is about to import io.ktor or exposed.
---

# Ports & Adapters, the toolkit way

## The one rule

**`-core` does not know that HTTP or a database exist.** Everything else here is a consequence.

The payoff is not architectural purity, it is that business rules stay testable without a server or a container, and that replacing Exposed is one
package of work rather than a rewrite. The cost is a mapping step at each boundary. Take that trade rather than relitigating it per endpoint.

The unusual thing about this layout is that the boundary is a **Gradle module boundary**, not a package convention. A package boundary is a promise; a
module boundary is checked by the compiler on every build. Start there, because retrofitting it onto a service that grew inside one module is the
expensive direction.

## When the project is a single module

Plenty of services are one Gradle module, and most of this skill still applies — the placement rules work as package rules, with `core`, `adapters`
and `app` as packages instead of modules. Follow them that way. You lose the compiler check and keep everything else, which is most of the value.

**Do not split someone's build as part of another task.** Proposing the three-module layout is fair when the user is scaffolding a new service or has
asked about structure; it is not fair as a side effect of adding an endpoint. When it is worth raising, say what moves where and that the split is its
own commit — then wait.

Until then, the greppable check below still works; point it at `src/main/kotlin/**/core/` instead of a module directory.

## The modules

Every service has the same five modules. The service name appears in three places and must agree across them: `rootProject.name`, the module prefixes,
and the package root `<org>.<service>`.

```
catalog/
├── settings.gradle.kts              rootProject.name = "catalog"
├── gradle/libs.versions.toml
├── catalog-core/                    domain + use cases — no framework
├── catalog-adapters/                every adapter: web in, persistence out
├── catalog-app/                     the deployable: wiring, plugins, config
├── acceptance-tests/                black-box tests over the running app
└── report/                          Kover aggregation across the three
```

```kotlin
// settings.gradle.kts
include("catalog-core", "catalog-adapters", "catalog-app")
include("report", "acceptance-tests")
```

The split that matters is **core / adapters**. `-app` is small on purpose: it is the composition root, not a layer. If you find yourself reaching for
it while implementing a feature, something has been put in the wrong module.

## Dependency direction

```
catalog-app ──▶ catalog-adapters ──▶ catalog-core
     └──────────────────────────────────▶
```

Arrows never point left. `-core` depends on no other module in the build; that is the invariant the whole layout exists to protect.

The build expresses it through the dependency configurations — understand these rather than copying them:

```kotlin
// catalog-core/build.gradle.kts
dependencies {
    compileOnly(libs.bundles.kotlinx)
    testImplementation(libs.bundles.testing)
}

// catalog-adapters/build.gradle.kts
dependencies {
    compileOnly(project(":catalog-core"))
    compileOnly(libs.bundles.kotlinx)
    compileOnly(libs.bundles.exposed)
    compileOnly(libs.bundles.ktor)
    testImplementation(libs.bundles.testing)
}

// catalog-app/build.gradle.kts
dependencies {
    implementation(project(":catalog-core"))
    implementation(project(":catalog-adapters"))
    implementation(libs.bundles.kotlinx)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.ktor)
}
```

**`-core` and `-adapters` compile against their libraries; `-app` owns them at runtime.** Only the deployable assembles a runtime classpath, so a
library version is decided in exactly one place and the inner modules cannot quietly pull a transitive dependency into the artifact. The
`ktor-toolkit:gradle` skill covers when `compileOnly` is right and when it is not — do not extend the pattern to a new module without loading it.

Note what `-core` does *not* list: no Exposed, no Ktor. That absence is the architecture, expressed in the one place a reviewer always looks.

The direction is also checkable, so check it rather than trusting a reading:

```bash
grep -rn "^import io.ktor" catalog-core/src/main/kotlin/
```

Any hit is a violation. This grep is genuinely necessary: `ktor-toolkit-paginator` declares
`ktor-server-core` as an `api` dependency, so adding the paginator to `-core` puts Ktor on its compile classpath whether you want it there or not. The
compiler will not stop you importing
`ApplicationCall` into a domain class. Nothing but this check will.

The fix for a hit is almost never to move the import. It is that a concept belonging to an adapter has been modelled in the domain.

## What lives in each module

### `-core` — entities, value objects, ports, use cases

Package by feature; the layer is already the module.

```
com.example.catalog.core/
└── book/
    ├── Book.kt                    entity
    ├── Isbn.kt                    value object
    ├── BookRepository.kt          driven port — an interface
    ├── BookNotFoundException.kt   domain exception
    ├── FindBooks.kt               use case
    ├── CreateBook.kt              use case
    └── BookFlow.kt                state machine, when the entity has a lifecycle
```

An entity has identity and a lifecycle. A value object is defined entirely by its contents and validates itself in its constructor — prefer one over a
`String` for anything with a rule attached, because it moves the rule to one place and makes the wrong type a compile error.

When that lifecycle has rules about what may follow what, state them once as a `stateMachine { }` beside the entity rather than as a status check in
each use case that touches it — load the `ktor-toolkit:state-machine` skill. It is a domain object: the module it comes from has no dependencies at
all, so it is the one toolkit type `-core` can take without the Ktor grep below finding anything.

Ports are interfaces named for what the domain wants, not for what implements them: `BookRepository`, never `BookDao` or `ExposedBookRepository`. The
domain declares the interface and the adapter satisfies it; that inversion is the entire point of the split.

A use case is a class with one public method and a constructor parameter per port:

```kotlin
class FindBooks(
    private val books: BookRepository,
) {
    suspend operator fun invoke(pagination: Pagination): Paged<Book> {
        val content = books.findAll(pagination)
        return Paged(pagination.page, pagination.sortBy, content, books.count())
    }
}
```

`operator fun invoke` so the call site reads as the action — `findBooks(pagination)`. Name the class for the action, not the pattern: `FindBooks`, not
`FindBooksUseCase` or `BookService`. Use cases orchestrate and do not hold business rules; a rule that fits in a use case usually belongs on the
entity that owns the data it reads.

Once a feature package passes roughly ten files, split `port/` and `usecase/` beneath it. Sooner than that the sub-packages cost more navigation than
they save.

### `-adapters` — both sides of the hexagon

This is where the service meets the outside world, in both directions. Split by direction first, then by feature:

```
com.example.catalog.adapters/
├── web/                              driving — the world calls us
│   └── book/
│       ├── BookRoutes.kt             the endpoints
│       ├── CreateBookRequest.kt      request DTO
│       ├── BookResponse.kt           response DTO + toResponse()
│       ├── BookValidation.kt         rulesFor<CreateBookRequest> { … }
│       ├── BookProblems.kt           on<BookNotFoundException> { … }
│       └── BookLinks.kt              HATEOAS links, if any
└── persistence/                      driven — we call the world
    └── book/
        ├── Books.kt                  Exposed table
        └── ExposedBookRepository.kt  implements BookRepository
```

```
catalog-adapters/src/main/resources/db/migration/
└── V1__create_books.sql              the schema those tables map to
```

Both directions are adapters, so both live here. An HTTP route is a *driving* adapter: it translates an inbound protocol into a use case call, exactly
as a repository translates a port call into SQL. Putting routes anywhere else splits the translation layer in half for no benefit.

**`web/`** holds routes, request and response DTOs, and the mappers between DTO and domain. The route body is thin by design: read the call, invoke
one use case, map the result, respond. An `if`
in a route body that carries business meaning belongs inward.

Mappers live next to the DTO — `Book.toResponse()` beside `BookResponse` — because the response knows about the entity and the entity must not know
about the response. Same inbound:
`CreateBookRequest.toDomain()`.

Validation rules and problem mappings belong here too, next to the type they talk about, exposed as extensions the app installs:

```kotlin
// BookValidation.kt
fun RequestValidationConfig.bookRules() {
    rulesFor<CreateBookRequest> {
        property(CreateBookRequest::title) { should notBe blank() }
    }
}
```

Keeping the rule beside the DTO is what stops the two drifting apart — load the `ktor-toolkit:validation`
and `ktor-toolkit:problem-details` skills.

**`persistence/`** holds table definitions and port implementations, named for their technology because the name is the one place the choice is
visible: `ExposedBookRepository`, `RedisBookCache`,
`HttpPricingClient`. This package is the only place Exposed appears. Outbound HTTP clients, message publishers and cache implementations sit alongside
it under their own directional package.

The migration SQL lives in this module's resources rather than in `-app`, so the table definition, the repository and the schema they both describe
change in one diff. `-app` depends on `-adapters` at runtime, so it runs those migrations without owning them — load the `ktor-toolkit:migrations`
skill.

### `-app` — the composition root

```
com.example.catalog.app/
├── Application.kt                 EngineMain entry point + module()
└── plugin/
    ├── Dependencies.kt            dependencies { provide<…> { … } }
    ├── Routing.kt                 routing { bookRoutes() }
    ├── Serialization.kt           install(ContentNegotiation)
    ├── StatusPages.kt             install(StatusPages) { problemDetails { bookProblems() } }
    └── Validation.kt              install(RequestValidation) { bookRules() }
```

Nothing here implements a feature. `-app` decides which adapters exist, installs the plugins, reads the configuration and starts the server. That is
why it is the only module allowed to see everything: it is also the only module nothing else depends on.

Configuration is `application.yaml` with environment interpolation, read through
`ktor-server-config-yaml` and nothing else:

```yaml
ktor:
    deployment:
        port: "$APPLICATION_PORT:8080"
    application:
        modules:
            - com.example.catalog.app.ApplicationKt.module
```

Wiring uses **Ktor's own DI plugin** — no third-party container:

```kotlin
fun Application.configureDependencies() {
    dependencies {
        provide<BookRepository> { ExposedBookRepository() }
        provide<FindBooks> { FindBooks(resolve()) }
    }
}
```

Ports resolve to their adapter here and nowhere else, and a route pulls what it needs with
`val findBooks: FindBooks by dependencies`. The `ktor-toolkit:di` skill owns scopes, lifetimes and testing — load it before adding a registration.

### `acceptance-tests` and `report`

`acceptance-tests` is its own module so black-box tests cannot reach into internals: it exercises the app the way a client does. It is the preferred
kind of test here — load the `ktor-toolkit:tests` skill for when a unit test earns its place instead.

`report` aggregates Kover across the three production modules and holds the thresholds. A new production module that is not registered there is
silently uncovered — load the `ktor-toolkit:kover` skill.

## Where the toolkit's own types belong

The line runs between `-core` and `-adapters`, and it is the line people get wrong, because some toolkit types are framework-free and still belong in
`-adapters`. **Role decides, not imports.** A type describing the syntax of what a client asked for, or the shape of what it will see, is an adapter
type even when it compiles without Ktor.

| Type                                   | Module                    | Why                                                             |
|----------------------------------------|---------------------------|-----------------------------------------------------------------|
| `Pagination`, `Page`, `Sort`           | `-core`                   | A request to read a slice in an order. Ports take these.        |
| `Paged<T>`                             | `-core`                   | A slice plus its total. Use cases return this.                  |
| `PaginationRequest`                    | `-adapters` / web         | Parses `?page` and `?pageSize`.                                 |
| `PagedResponse<T>`                     | `-adapters` / web         | The wire shape, with `metadata`. Framework-free, still a DTO.   |
| `Resource`, `Link`                     | `-adapters` / web         | Links are URLs — a transport concern the domain has no view on. |
| `ExpandRequest`                        | `-adapters` / web         | Parses `?expand=`.                                              |
| `ExpandSpec`, `Expandable`             | `-adapters` / web         | Shapes a response for a client that asked to expand.            |
| `ProblemDetail`, `HttpStatusException` | `-adapters` / web         | An HTTP status is a transport decision.                         |
| `StateMachine`, `Transition`           | `-core`                   | An aggregate's legal moves are the domain's own rules.          |
| `IllegalTransitionException`           | `-core`                   | A domain exception. The adapter picks the status, once.         |
| `transitionLinks`, `withTransitions`   | `-adapters` / web         | Renders those moves as URLs, which the domain has no view on.   |
| `Sort.toExposedQueryExpression(…)`     | `-adapters` / persistence | Turns a domain sort into a query.                               |
| `KeyValueCache` implementations        | `-adapters`               | Infrastructure. Constructed in `-app`, never in a route.        |

That first row is why `-core` carries `compileOnly(libs.ktor.toolkit.paginator)` while `-app` carries it as `implementation`: the domain genuinely
speaks in pages and sorts, and only the deployable needs it on the runtime classpath.

The `data` / `web` split in the table is also the package split, and the import root is **not** the group id: artifacts are `io.github.joaoseidel`,
packages are `com.github.joaoseidel.ktor.toolkit.*`. So `-core` imports
`com.github.joaoseidel.ktor.toolkit.paginator.data.Pagination` and `-adapters/web` imports
`...paginator.web.PagedResponse` — an import from `web` in a `-core` file is the same violation as the Ktor grep above, visible in the import list.
The `ktor-toolkit:install` skill has the full map — load it.

Two consequences people get wrong:

**Ports take `Pagination` and return `List<T>` or `Paged<T>`.** Paging is a domain-level concern — the domain really does want "the first 20 by
title" — while `?page=` syntax is not. So
`findAll(pagination: Pagination): List<Book>` is right, and `findAll(page: Int, size: Int)` throws away the sort model for nothing.

**Core never throws `HttpStatusException`.** It throws `BookNotFoundException`, which it owns and can be tested against. Mapping that to a 404 happens
once, in `problemDetails { }` — load the
`ktor-toolkit:problem-details` skill. A domain that picks status codes has quietly become a web layer.

## How a request travels

Each arrow is a mapping, and each one is skippable in a way that costs you the boundary:

```
HTTP  ─▶ adapters/web   route: call.pagination      (PaginationRequest ─▶ Pagination)
      ─▶ core           findBooks(pagination)
      ─▶ core           BookRepository.findAll(pagination)     (the port)
      ─▶ adapters/pers. Exposed query + Sort.toExposedQueryExpression(allow-list)
      ◀─ core           List<Book> ─▶ Paged<Book>
      ◀─ adapters/web   PagedResponse.from(paged) { it.toResponse() }
      ◀─ adapters/web   optionally .toResource(call) for links
HTTP
```

`-app` appears nowhere in that path. It built the objects before the first request arrived and then got out of the way — that is the test of whether
it is doing its job.

The `ktor-toolkit:pagination` skill walks this end to end with full code — load it.

## Common mistakes

| Mistake                                                                | Why it hurts                                                                | Instead                                              |
|------------------------------------------------------------------------|-----------------------------------------------------------------------------|------------------------------------------------------|
| Routes or DTOs in `-app`                                               | `-app` becomes a second web layer and the translation layer is split in two | Routes and DTOs are `adapters/web`                   |
| `@Serializable` entity returned straight from a route                  | The wire format is now the storage format; a rename breaks clients          | Response DTO plus `toResponse()`                     |
| Port interface and Exposed impl in the same module                     | There is no port left to substitute                                         | Interface in `-core`, impl in `adapters/persistence` |
| Use case returning `PagedResponse`                                     | `-core` now knows the wire shape and needs an adapter type                  | Return `Paged<T>`, map in the route                  |
| `adapters/web` importing `adapters/persistence`                        | The two sides of the hexagon are coupled through the middle                 | Both talk to `-core` only                            |
| Business rule in the route body                                        | Untestable without a server, invisible to the next reader                   | Push it to the entity or use case                    |
| `BookService` holding every book operation                             | Grows without bound; nothing readable in isolation                          | One class per use case                               |
| `implementation` in `-core` or `-adapters` where `compileOnly` belongs | The inner module starts shipping its own runtime classpath                  | Keep runtime ownership in `-app`                     |
| New production module missing from `report`                            | Its coverage silently stops counting                                        | Register it in `report/build.gradle.kts`             |

## When to bend it

Three production modules is the floor, not a target — a service does not earn `-core-domain` and
`-core-application` by growing. Add a module when a genuinely separate deployable or a genuinely reusable library appears, not to express a layer that
a package already expresses.

What does not bend: `-core` stays free of Ktor and Exposed imports, ports stay interfaces owned by
`-core`, and DTOs stay separate from entities. Those three are what keep everything else recoverable. Anything else is negotiable with a stated
reason.
