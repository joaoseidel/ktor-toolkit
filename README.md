# Ktor Toolkit

[![CI](https://img.shields.io/github/actions/workflow/status/joaoseidel/ktor-toolkit/ci.yml?branch=main&label=CI)](https://github.com/joaoseidel/ktor-toolkit/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.joaoseidel/ktor-toolkit-paginator?label=Maven%20Central)](https://central.sonatype.com/search?q=g:io.github.joaoseidel)
[![Coverage](https://img.shields.io/badge/coverage-100%25-brightgreen)](#development)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/Ktor-3.4.1-087CFA)](https://ktor.io)
[![Java](https://img.shields.io/badge/Java-21+-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net)

Six small, independent libraries for building JSON APIs with [Ktor](https://ktor.io): pagination, HATEOAS links, request validation, RFC 9457 errors,
field expansion and response caching.

Take one or take all seven. Nothing pulls in the others except `hateoas`, which builds on `paginator`.

| Module                                             | What it does                                                                 |
|----------------------------------------------------|------------------------------------------------------------------------------|
| [`ktor-toolkit-paginator`](#paginator)             | Parses `?page`/`?pageSize`/`?sortBy`, and shapes the paged response          |
| [`ktor-toolkit-hateoas`](#hateoas)                 | Wraps a response in a `_links` envelope, with pagination links built for you |
| [`ktor-toolkit-validator`](#validator)             | A type-safe `should be` / `should notBe` DSL over Ktor's RequestValidation   |
| [`ktor-toolkit-problem-details`](#problem-details) | Turns exceptions into RFC 9457 `application/problem+json` responses          |
| [`ktor-toolkit-expander`](#expander)               | Resolves `?expand=author.books`, batching one query per field                |
| [`ktor-toolkit-cache`](#cache)                     | Caches a response by request path and query, over any key-value store        |
| [`ktor-toolkit-state-machine`](#state-machine)     | Declares an aggregate's legal moves, and publishes the open ones as links    |

The repository also ships [25 agent skills](#skills) that record how these APIs are meant to be used, so an agent working in your service follows the
same conventions you would.

## Install

**Java 21 or newer.** On an older toolchain the dependency resolves and the compile fails with `class file has wrong version`, which reads like a
corrupt artifact and is not one.

Published to Maven Central, so a build that already resolves anything else needs no repository change:

```kotlin
dependencies {
    implementation("io.github.joaoseidel:ktor-toolkit-paginator:1.0.0")
    implementation("io.github.joaoseidel:ktor-toolkit-problem-details:1.0.0")
}
```

The modules are released together. Give them one shared version key in `gradle/libs.versions.toml` rather than one per module — mixing versions across
them is unsupported.

```toml
[versions]
ktor-toolkit = "1.0.0"

[libraries]
ktor-toolkit-paginator = { module = "io.github.joaoseidel:ktor-toolkit-paginator", version.ref = "ktor-toolkit" }
ktor-toolkit-problem-details = { module = "io.github.joaoseidel:ktor-toolkit-problem-details", version.ref = "ktor-toolkit" }
```

### The package root is not the group id

Coordinates are `io.github.joaoseidel`, because that is the only namespace Maven Central verifies from a GitHub account. **The Kotlin packages are
`com.github.joaoseidel`**, so an import guessed from the dependency line does not resolve:

```kotlin
import com.github.joaoseidel.ktor.toolkit.paginator.pagination          // ApplicationCall.pagination
import com.github.joaoseidel.ktor.toolkit.paginator.web.PagedResponse
import com.github.joaoseidel.ktor.toolkit.problemdetails.problemDetails // one word, unlike the artifact
import com.github.joaoseidel.ktor.toolkit.validator.rulesFor
import com.github.joaoseidel.ktor.toolkit.validator.validators.blank    // one import per rule you use
```

Domain types sit under `data`, wire types under `web`, and the call extensions and plugin installers at the module root.

### What you also need

**Ktor and kotlinx-serialization arrive transitively** — they appear in these modules' public signatures, so they are `api` dependencies.
**ContentNegotiation does not**, and every module needs it to turn responses into JSON:

```kotlin
implementation("io.ktor:ktor-server-content-negotiation:3.4.1")
implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.1")
```

Without it a route returning `PagedResponse` fails at runtime with "Response pipeline couldn't transform".

**A dependency on its own changes nothing.** `problem-details` needs `StatusPages` with `problemDetails { }` inside it, and `validator` needs
`RequestValidation`. `paginator`, `hateoas`, `expander` and `cache` need no plugin — they are call extensions and plain objects.

**Three features compile against libraries their module deliberately does not carry**, so a project that skips the feature does not pay for it. Add
the dependency only alongside the feature, or it fails at runtime with `NoClassDefFoundError`:

| Using                              | Add                                                                          |
|------------------------------------|------------------------------------------------------------------------------|
| `Sort.toExposedQueryExpression(…)` | `org.jetbrains.exposed:exposed-core`                                         |
| `Sort.toMongoSortExpression(…)`    | `org.mongodb:mongodb-driver-core` — already transitive to any MongoDB driver |
| `LettuceCache`                     | `io.lettuce:lettuce-core`                                                    |

### Snapshots

Every merge to `main` publishes `1.0.0-SNAPSHOT`, so an unreleased change can be consumed without building it:

```kotlin
repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}
```

```kotlin
dependencies {
    implementation("io.github.joaoseidel:ktor-toolkit-paginator:1.0.0-SNAPSHOT")
}
```

Gradle caches a snapshot for 24 hours. To pick up a fresh one, either run with `--refresh-dependencies` or narrow the window for these modules alone:

```kotlin
configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
}
```

For a change that is not merged yet, `make publish_local` puts the current tree in your local Maven repository; add `mavenLocal()` for that session
and remove it in the same one.

### Let the agent do it

The [skills](#skills) ship as a Claude Code plugin, from a marketplace this repository hosts itself. Both commands are interactive and run inside a
session:

```bash
/plugin marketplace add joaoseidel/ktor-toolkit
```

```bash
/plugin install ktor-toolkit@ktor-toolkit
```

The marketplace step only points Claude Code at this repository; nothing is installed until the second command. Installing this way is what keeps the
`/ktor-toolkit:` prefix on every skill. For any other agent, or to vendor the files into your repository, the skills CLI reads the same collection:

```bash
npx skills add joaoseidel/ktor-toolkit --skill '*'
```

Take the whole collection rather than a subset — the skills route to each other, and a partial install turns those references into dead ends. Prefer
project scope over global: these are one project's conventions, and installed globally they follow you into projects that do not share them.

With the collection in place, `/ktor-toolkit:install` does the whole setup — picks the modules from what your API actually does, adds the catalog
entries and dependencies, installs the Ktor plugins each selected module requires, adds the compiler opt-ins the DSLs ask for, and verifies the result
compiles before reporting back.

```bash
/ktor-toolkit:install
```

Use it to add another module to a project already on the toolkit, and when `Could not find io.github.joaoseidel:...` or an error response arrives as
HTML instead of `problem+json`.

---

## Paginator

Reads the pagination off the call, carries it through to your repository, and shapes what comes back. **`call.pagination` never fails the request:**
an unparseable `?page=abc` falls back to the default, a negative page becomes `0`, and the page size is clamped to `1..100`. Use
`call.paginationRequest(…)` for other bounds.

```kotlin
get("/books") {
    val pagination = call.pagination
    val books = repository.findAll(pagination)
    val paged = Paged(pagination.page, pagination.sortBy, books, repository.count())

    call.respond(PagedResponse.from(paged) { it.toResponse() })
}
```

`?sortBy=name,-createdAt` parses into `[Sort("name", ASC), Sort("createdAt", DESC)]`. Build a default ordering from property references, so a rename
cannot leave a stale sort key behind:

```kotlin
val ordering = sortBy {
    desc(Book::publishedAt)
    asc(Book::title)
}
```

Turn a sort into a query with an explicit allow-list. **Anything outside it raises `IllegalArgumentException` rather than reaching the database:**

```kotlin
Books.selectAll().orderBy(*pagination.sortBy.toExposedQueryExpression(Books.title, Books.createdAt).toTypedArray())

collection.find().sort(pagination.sortBy.toMongoSortExpression(BookDocument::title, BookDocument::createdAt))
```

The response carries a `metadata` block: `page`, `pageSize`, `totalPages` (a count, so the last page index is `totalPages - 1`), `totalElements`,
`hasNext`, `hasPrevious`, `isSorted` and `sortCriteria`.

## HATEOAS

Makes a response advertise where a client can go next, instead of the client hardcoding URLs. `toResource(call)` attaches `self`, `next`, `prev`,
`first` and `last`, skipping any that would point at a page that does not exist. Every other query parameter of the current request is carried over,
correctly percent-encoded.

```kotlin
get("/books") {
    call.respond(PagedResponse.from(paged) { it.toResponse() }.toResource(call))
}
```

```json
{
    "metadata": {
        "page": 0,
        "pageSize": 10,
        "totalPages": 3,
        "hasNext": true
    },
    "content": [
        …
    ],
    "_links": [
        { "rel": "self", "href": "/books?page=0&pageSize=10", "method": "GET" },
        { "rel": "next", "href": "/books?page=1&pageSize=10", "method": "GET" },
        { "rel": "last", "href": "/books?page=2&pageSize=10", "method": "GET" }
    ]
}
```

For anything that is not a page, `resource` wraps content and declares its links in a block:

```kotlin
call.respond(
    resource(book.toResponse()) {
        link("self", "/books/${book.id}")
        link("delete", "/books/${book.id}", HttpMethod.Delete)
    },
)
```

## Validator

Rules read as sentences and attach to properties by reference, so a rename is a compile error rather than a silently dead rule. **A rule that does not
fit does not compile:** `should be email()` on an `Int` is an unresolved reference, and completion inside `property { }` offers only the rules that
apply.

```kotlin
install(RequestValidation) {
    rulesFor<CreateBookRequest> {
        property(CreateBookRequest::title) {
            should notBe blank()
            should be size(min = 3, max = 200)
        }
        property(CreateBookRequest::authorEmail) { should be email() }
        nested(CreateBookRequest::publisher) {
            property(Publisher::name) { should notBe blank() }
        }
    }
}
```

| Rule                                            | Applies to                              |
|-------------------------------------------------|-----------------------------------------|
| `blank`, `email`, `pattern`                     | `String`                                |
| `uuid`                                          | `String`, `UUID`, `Uuid`                |
| `size`                                          | `String`, `Collection`, `Map`, `Array`  |
| `min`, `max`, `inRange`, `positive`, `negative` | any `Number`                            |
| `past`, `future`, `before`, `after`, `within`   | `LocalDate`, `LocalDateTime`, `Instant` |
| `nil`, `satisfying`                             | any                                     |

Rules are values: combine them with `and`, `or` and `!`, write a one-off with `satisfying`, and override the default message with `describedAs`. Note
the parentheses around a composed rule — infix calls associate to the left.

```kotlin
property(CreateBookRequest::isbn) {
    should be (uuid() or satisfying("should be an ISBN") { it.isValidIsbn() })
}

property(CreateBookRequest::authorEmail) {
    should be email() describedAs "should be a work email address"
}
```

**A rule stays silent on a `null` property**, so requiring a field and constraining it are two separate assertions — `should notBe nil()`, then
`should be email()`. `each` validates collection elements as values and `eachNested` as objects, `whenever` makes a group conditional, and `invariant`
states a rule no single property owns:

```kotlin
rulesFor<CreateBookRequest> {
    each(CreateBookRequest::tags) { should notBe blank() }
    eachNested(CreateBookRequest::authors) { property(Author::email) { should be email() } }

    whenever(!target.draft) {
        property(CreateBookRequest::publishedAt) { should notBe nil() }
    }

    invariant("should not be dated before its author was born") { it.publishedAt > it.authorBornAt }
}
```

`target` is the object under validation, so a rule can depend on a sibling field: `should be after(target.startsAt)`. Temporal rules take an explicit
`timeZone` (the system zone by default) and a `now`, so a comparison need not depend on where the server runs and a test can pin the clock.

## Problem Details

One installer turns validation failures, malformed bodies, deliberate status exceptions and anything otherwise unhandled into
`application/problem+json`.

```kotlin
install(StatusPages) {
    problemDetails {
        namingStrategy = JsonNamingStrategy.SnakeCase
        on<BookNotFoundException> { ProblemDetail.fromStatus(HttpStatusCode.NotFound, "No book ${it.id}") }
    }
}

throw HttpStatusException(HttpStatusCode.NotFound, "Book not found", mapOf("id" to id))
```

```json
{
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "Book not found",
    "instance": "/books/42",
    "properties": {
        "id": "42"
    }
}
```

StatusPages resolves by nearest ancestor class, so an `on<E>` mapping wins over the catch-all whatever order it was declared in, and the request path
fills in as `instance` unless you name one.

**An unhandled exception logs its stack trace and answers with a fixed message**, because exception text routinely names the database. Set
`includeExceptionMessage = true` to echo it while developing, and register `exception<T>` handlers after `problemDetails()` to override any of them.

Install it before you have any mappings. Covering the *unmapped* cases is the module's whole point, and a service that adds it later has already
shipped a different error shape to its clients.

## Expander

Lets a client pull in related resources without a second round trip, and without an N+1. Declare once what a response can expand:

```kotlin
val bookSpec = ExpandSpec.build<BookResponse> {
    field("author", get = { it.author }, set = { copy(author = it) }) {
        batch { ids, _ -> authorRepository.findAllById(ids).associateBy { it.id } }
    }
}

get("/books") {
    call.respond(bookSpec.apply(repository.findAll(), call.expand))
}
```

`?expand=author` resolves a whole page in **one** call to `batch`. `?expand=author.name` projects: `batch` receives `setOf("name")` so it can issue a
narrower query, and only that field is serialized. `?expand=author.books` nests, via a `nested` block:

```kotlin
field("author", get = { it.author }, set = { copy(author = it) }) {
    batch { ids, fields -> authorRepository.findAllById(ids, fields).associateBy { it.id } }
    nested {
        listField("books", get = { it.books }, set = { copy(books = it) }) {
            batch { ids, _ -> bookRepository.findAllById(ids).associateBy { it.id } }
        }
    }
}
```

An `Expandable<T>` serializes as a bare `"author-id"` string while unresolved and as a full object once expanded, so the wire format tells the client
which it got. `field` covers nullable fields, `listField` covers lists, and `polymorphicField` with one `case` per discriminator covers fields whose
type varies per row.

## Cache

Caches a route's result under the request path plus its query parameters, sorted so parameter order does not matter, then hashed. **A cache failure is
logged and the request is served from the origin**, though a coroutine cancellation still propagates.

```kotlin
val cache = InMemoryCache(maxSize = 1_000, ttl = 5.minutes)

get("/books") {
    val books = call.request.withCache("books", cache, excludeQueryKeys = setOf("traceId")) {
        repository.findAll()
    }
    call.respond(books)
}
```

Invalidate on write:

```kotlin
cache.invalidateNamespace("books")   // everything under the namespace
cache.invalidateContaining(bookId)   // entries whose payload mentions this id
```

`InMemoryCache` is LRU-bounded with an optional TTL, which suits a single node. **Once more than one instance serves the same traffic, use
`LettuceCache`** over [Lettuce](https://lettuce.io) — otherwise each node holds, and invalidates, its own copy:

```kotlin
val connection = RedisClient.create("redis://localhost:6379").connect(LettuceCache.Codec)
val cache = LettuceCache(connection.async(), ttl = 5.minutes)
```

Nothing else changes: `withCache` and both invalidation helpers behave the same, and Redis applies the TTL itself. You own the connection, so share
it — it is thread-safe and multiplexes — and close it on shutdown. Every key sits under `keyPrefix` (`"ktor-toolkit:"` by default), which bounds what
the invalidation helpers scan. They walk the keyspace with `SCAN`, so keep them to writes rather than requests.

---

## State Machine

States an aggregate's legal moves once, in the domain, instead of leaving them spread across whichever use cases happen to touch the status field. The
module has **no dependencies at all** — not even Ktor — so a framework-free `-core` can depend on it.

```kotlin
val orderFlow = stateMachine<OrderState, OrderEvent, Order> {
    initial(DRAFT)
    final(SHIPPED, CANCELLED)

    state(DRAFT) {
        on<Place>(PLACED) {
            guard("must have at least one line") { it.lines.isNotEmpty() }
            effect { order, event -> audit.record(order, event) }
        }
        on<Cancel>(CANCELLED)
    }

    state(PLACED) {
        on<Pay>(PAID)
        on<Cancel>(CANCELLED)
    }

    state(PAID) {
        onEnter { order, _ -> receipts.issue(order) }
        on<Ship>(SHIPPED) { rel = "dispatch" }
    }

    onTransition { audit.log(it) }
}
```

**The definition is checked when it is built**, so an unreachable state, a dead end, a move to a state nobody declared or two moves one event could
both trigger is a `StateMachineDefinitionException` at startup rather than a bug found in production.

The machine never touches the subject — it reports where to go, and the caller applies it in the same unit of work that persists it:

```kotlin
when (val result = orderFlow.fire(order, order.state, Pay)) {
    is Accepted -> repository.save(order.copy(state = result.to))
    is Rejected -> log.info { result.reason.message }
}
```

Where a rejection is always an error, `fireOrThrow` returns the state and raises `IllegalTransitionException` — map it to a `409` in `problemDetails
{ }` and the handler stays one line. Guards and effects may both suspend.

**A guard asks about the subject, never the event.** That is what lets the machine answer "what can this order do right now?" without being handed an
event to test with — so the affordances and the rules that decide them cannot drift apart:

```kotlin
val moves = orderFlow.transitionLinks(order, order.state) { "/orders/${order.id}/${it.rel}" }

call.respond(
    resource(order.toResponse()) {
        link("self", "/orders/${order.id}")
        links(moves)
    },
)
```

The client stops reimplementing the lifecycle to decide whether to draw a button: it looks for a `pay` link, and the absence of one is the answer.
`transitionLinks` is the only part that needs `ktor-toolkit-hateoas`, and it is compiled against it optionally.

---

## Skills

The libraries leave the surrounding decisions open, and everyone resolves them differently: one parses `?page` by hand, another invents its own error
envelope, another puts the repository call in the route body. The skills record the intended answer to each, so the *n*-th endpoint in your service
looks like the first one whoever wrote it.

They are written for the repository that installs them, not for this one. Install with the commands under [Let the agent do it](#let-the-agent-do-it).

**`/ktor-toolkit:start` runs first, before any Kotlin is written or changed.** It establishes which toolkit modules are on your classpath and what
your project's build gate is, then routes the task to the skills that apply — usually two or three. Running it after the code is written is too late
to be useful.

| Skill             | What it covers                                                                          |
|-------------------|-----------------------------------------------------------------------------------------|
| `start`           | **Entrypoint.** Orients in the project, routes the task to the skills below             |
| `install`         | Adds the toolkit to a project: modules, Gradle wiring, Ktor plugins, these skills       |
| `endpoint`        | A new or changed HTTP route, end to end across `-core`, `-adapters` and `-app`          |
| `architecture`    | Ports & Adapters layout, the three-module split, where a file goes                      |
| `pagination`      | `?page`, `?pageSize`, `?sortBy`, carrying `Pagination` into the persistence adapter     |
| `hateoas`         | `_links`, page links, the `resource { }` builder                                        |
| `validation`      | `rulesFor<T>`, syntactic vs business rules, the nullable-DTO convention                 |
| `problem-details` | `problemDetails { }`, status codes, mapping domain exceptions                           |
| `expand`          | `ExpandSpec`, batched resolution, the `Expandable` wire contract                        |
| `cache`           | `withCache`, choosing a store, TTLs, who invalidates and when                           |
| `state-machine`   | `stateMachine { }`, guards as affordances, transitions as `_links`                      |
| `migrations`      | Versioned SQL under Flyway, where it lives, baselining, expand/contract schema changes  |
| `di`              | Ktor's native DI: `provide<Port> { Impl(resolve()) }`, lifetimes, test overrides        |
| `tests`           | Kotest ShouldSpec naming, MockK, `testApplication`, Testcontainers, acceptance tests    |
| `kover`           | The `report` aggregation module, ratcheting a threshold, the rule for exclusions        |
| `openapi`         | Ktor's comment-based OpenAPI generation, served through Scalar                          |
| `logging`         | KotlinLogging idioms, correlation IDs via CallId/CallLogging, what must never be logged |
| `healthcheck`     | Liveness and readiness probes with Cohort, and what a probe may depend on               |
| `gradle`          | `libs.versions.toml` as the single source of versions, choosing a dependency scope      |
| `makefile`        | The canonical target set, and a self-documenting `help`                                 |
| `container`       | Multi-stage jlink Dockerfile, container-aware JVM ergonomics, graceful shutdown         |
| `codestyle`       | What a formatter cannot check: naming, immutability, invariants, coroutine failure      |
| `comments`        | The best comment is none; KDoc states the contract, and stale comments get deleted      |
| `changelog`       | What earns an entry, written from the caller's side, in the commit that caused it       |
| `commit`          | Conventional Commits with a body that says what was wrong, and how to split work        |

Each skill also says what to do when it describes something your repository does not have yet — a Makefile, a Dockerfile, a `CHANGELOG.md`, a coverage
module. It proposes the file and waits for you, rather than scaffolding it unasked or quietly working around its absence.

## Development

```bash
make build      # compile, test and lint every module
make test       # tests only
make coverage   # report/build/reports/kover/html/index.html
make format     # apply ktlint
make api        # refresh the public API dumps after an intentional change
make verify     # everything a release must pass
make docs       # Dokka HTML per module
```

`make verify` is lint, `api_check`, build and coverage, in the order a failure is cheapest to read. Coverage is gated at 100% line and 100% branch,
and a changed public signature carries its refreshed `api/` dump in the same commit.

See [CONTRIBUTING.md](CONTRIBUTING.md), [RELEASING.md](RELEASING.md) and the [CHANGELOG](CHANGELOG.md).

## License

[MIT](LICENSE)
