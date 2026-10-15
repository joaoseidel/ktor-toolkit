# Ktor Toolkit

[![CI](https://img.shields.io/github/actions/workflow/status/joaoseidel/ktor-toolkit/ci.yml?branch=main&label=CI)](https://github.com/joaoseidel/ktor-toolkit/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.joaoseidel/ktor-toolkit-paginator?label=Maven%20Central)](https://central.sonatype.com/search?q=g:io.github.joaoseidel)
[![Coverage](https://img.shields.io/badge/coverage-100%25-brightgreen)](#development)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3+-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/Ktor-3.6.0-087CFA)](https://ktor.io)
[![Java](https://img.shields.io/badge/Java-17+-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net)

Eight small, independent libraries for building JSON APIs with [Ktor](https://ktor.io): pagination, filtering, HATEOAS links, request validation,
RFC 9457 errors, field expansion, response caching and aggregate state machines.

Take one or take all eight. Nothing pulls in the others except `hateoas`, which builds on `paginator`.

| Module                                             | What it does                                                                 |
|----------------------------------------------------|------------------------------------------------------------------------------|
| [`ktor-toolkit-paginator`](#paginator)             | Parses `?page`/`?pageSize`/`?sortBy`, and shapes the paged response          |
| [`ktor-toolkit-filter`](#filter)                   | Parses `?filterBy=price:gte:1000`, and applies it through an allow-list      |
| [`ktor-toolkit-hateoas`](#hateoas)                 | Wraps a response in a `_links` envelope, with pagination links built for you |
| [`ktor-toolkit-validator`](#validator)             | A type-safe `should be` / `should notBe` DSL over Ktor's RequestValidation   |
| [`ktor-toolkit-problem-details`](#problem-details) | Turns exceptions into RFC 9457 `application/problem+json` responses          |
| [`ktor-toolkit-expander`](#expander)               | Resolves `?expand=author.books`, batching one query per field                |
| [`ktor-toolkit-cache`](#cache)                     | Caches a response by request path and query, over any key-value store        |
| [`ktor-toolkit-state-machine`](#state-machine)     | Declares an aggregate's legal moves, and publishes the open ones as links    |

The repository also ships [26 agent skills](#skills) that record how these APIs are meant to be used, so an agent working in your service follows the
same conventions you would.

## Install

**Java 17 and Kotlin 2.3 or newer.** On an older JDK the dependency resolves and the compile fails with `class file has wrong version`, which reads like a
corrupt artifact and is not one.

Published to Maven Central, so a build that already resolves anything else needs no repository change:

```kotlin
dependencies {
    implementation("io.github.joaoseidel:ktor-toolkit-paginator:1.0.0")
    implementation("io.github.joaoseidel:ktor-toolkit-problem-details:1.0.0")
}
```

The modules are released together. Give them one shared version key in `gradle/libs.versions.toml` rather than one per module. Mixing versions across
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

**Ktor and kotlinx-serialization arrive transitively** because they appear in these modules' public signatures, so they are `api` dependencies.
**ContentNegotiation does not**, and every module needs it to turn responses into JSON. Pin it to the same Ktor version as the rest of your server
(3.6 or newer), under the catalog's existing `ktor` key:

```toml
[libraries]
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
```

Without it a route returning `PagedResponse` fails at runtime with "Response pipeline couldn't transform".

**A dependency on its own changes nothing.** `problem-details` needs `StatusPages` with `problemDetails { }` inside it, and `validator` needs
`RequestValidation`. `paginator`, `filter`, `hateoas`, `expander` and `cache` need no plugin. They are call extensions and plain objects.

**Five features compile against libraries their module deliberately does not carry**, so a project that skips the feature does not pay for it. Add
the dependency only alongside the feature, or it fails at runtime with `NoClassDefFoundError`:

| Using                              | Add                                                                         |
|------------------------------------|-----------------------------------------------------------------------------|
| `Sort.toExposedQueryExpression(…)` | `org.jetbrains.exposed:exposed-core`                                        |
| `Sort.toMongoSortExpression(…)`    | `org.mongodb:mongodb-driver-core`, already transitive to any MongoDB driver |
| `Filter.toExposedCondition(…)`     | `org.jetbrains.exposed:exposed-core`                                        |
| `Filter.toMongoFilter(…)`          | `org.mongodb:mongodb-driver-core`, already transitive to any MongoDB driver |
| `LettuceCache`                     | `io.lettuce:lettuce-core`                                                   |

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

Gradle caches a snapshot for 24 hours. To pick up a fresh one, run with `--refresh-dependencies`, or stop caching changing modules for as long as you
depend on one. That setting covers every snapshot in the build, not only these:

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

Take the whole collection rather than a subset. The skills route to each other, and a partial install turns those references into dead ends. Prefer
project scope over global: these are one project's conventions, and installed globally they follow you into projects that do not share them.

With the collection in place, `/ktor-toolkit:install` does the whole setup. It picks the modules from what your API actually does, adds the catalog
entries and dependencies, installs the Ktor plugins each selected module requires, adds the compiler opt-ins the DSLs ask for, and verifies the
result compiles before reporting back.

```bash
/ktor-toolkit:install
```

Use it to add another module to a project already on the toolkit, and when `Could not find io.github.joaoseidel:...` or an error response arrives as
HTML instead of `problem+json`.

---

## Paginator

Reads the pagination off the call, carries it through a use case to your repository, and shapes what comes back. **`call.pagination` never fails
the request:** an unparseable `?page=abc` falls back to the default, a negative page becomes `0`, and the page size is clamped to `1..100`. Use
`call.paginationRequest(…)` for other bounds.

The route reads the call and hands a domain type inward; the use case pairs the slice with its total:

```kotlin
get("/books") {
    val paged = findBooks(call.pagination)
    call.respond(PagedResponse.from(paged) { it.toResponse() })
}
```

```kotlin
class FindBooks(private val books: BookRepository) {
    suspend operator fun invoke(pagination: Pagination): Paged<Book> =
        Paged(pagination.page, pagination.sortBy, books.findAll(pagination), books.count())
}
```

`?sortBy=title,-published_at` parses into `[Sort("title", ASC), Sort("published_at", DESC)]`. A key is matched by the name the store uses: an Exposed
column's SQL name, or a MongoDB field as stored. With snake_case columns the client sends `published_at`, which is also the name it reads in a
snake_case response.

The repository resolves the sort against an explicit allow-list. **Anything outside it raises `InvalidSortException` rather than reaching the
database.** Give every listing a default order and a unique tie-breaker, or rows repeat and vanish between pages:

```kotlin
override suspend fun findAll(pagination: Pagination): List<Book> =
    suspendTransaction(database, readOnly = true) {
        val (page, pageSize) = pagination.page
        val order =
            pagination.sortBy.toExposedQueryExpression(Books.title, Books.publishedAt)
                .ifEmpty { listOf(Books.publishedAt to SortOrder.DESC) } + (Books.id to SortOrder.ASC)

        Books.selectAll()
            .orderBy(*order.toTypedArray())
            .limit(pageSize)
            .offset(page.toLong() * pageSize)
            .map { it.toBook() }
    }
```

On MongoDB the allow-list is the document class, whose property names are the stored field names:

```kotlin
collection.find().sort(pagination.sortBy.toMongoSortExpression(BookDocument::title, BookDocument::publishedAt))
```

The response carries a `metadata` block: `page`, `pageSize`, `totalPages` (a count, so the last page index is `totalPages - 1`), `totalElements`,
`hasNext`, `hasPrevious`, `isSorted` and `sortCriteria`.

## Filter

Reads `?filterBy=` off the call and applies it in your repository. Each condition is `property:operator:value`; conditions are separated by `,` (or
by repeating the parameter) and must all hold:

```
?filterBy=price:gte:1000,title:like:kotlin
?filterBy=status:in:draft|published
?filterBy=subtitle:exists:false
```

| Operator              | Means                                  |
|-----------------------|----------------------------------------|
| `eq` `notEq`          | Equal, not equal                       |
| `gt` `gte` `lt` `lte` | Ordering                               |
| `like` `notLike`      | Contains the text, ignoring case       |
| `in` `notIn`          | Any or none of a `\|`-separated list   |
| `exists`              | `true`: has a value; `false`: has none |

The value is everything after the second `:`, and `\` makes the next character literal, so `title:eq:Hello\, world` is one condition.
`notEq`, `notLike` and `notIn` also match a row with no value, as MongoDB does; on its own, SQL's `NULL <> x` would drop it.

**Unlike paging, a bad filter fails the request.** A filter is data: dropping one would answer with rows the client asked to exclude. Malformed
syntax, a property outside the allow-list and a value of the wrong type all raise `InvalidFilterException`, which carries the offending `property`,
so one `problemDetails { }` mapping turns them into a 400.

The filters travel the same way the page does, and **the count takes them too**, because a total over the unfiltered table makes every number in
`metadata` wrong:

```kotlin
get("/books") {
    val paged = findBooks(call.filters, call.pagination)
    call.respond(PagedResponse.from(paged) { it.toResponse() })
}
```

```kotlin
class FindBooks(private val books: BookRepository) {
    suspend operator fun invoke(filters: List<Filter>, pagination: Pagination): Paged<Book> =
        Paged(pagination.page, pagination.sortBy, books.findAll(filters, pagination), books.count(filters))
}
```

The repository applies them through an explicit allow-list. In SQL each value is typed by the column it targets; MongoDB has no schema, so the
allow-list declares the types:

```kotlin
private val filterable = listOf(Books.title, Books.price, Books.status)

Books.selectAll().where(filters.toExposedCondition(filterable))
```

```kotlin
private val filterable = filterableFields {
    field(BookDocument::title)
    field(BookDocument::price)
}

collection.find(filters.toMongoFilter(filterable))
```

HATEOAS page links carry `filterBy` over, so a filtered listing stays filtered as the client follows `next`.

## HATEOAS

Makes a response advertise where a client can go next, instead of the client hardcoding URLs. `toResource(call)` attaches `self`, `next`, `prev`,
`first` and `last`, skipping any that would point at a page that does not exist. Every other query parameter of the current request is carried over,
correctly percent-encoded.

```kotlin
get("/books") {
    val paged = findBooks(call.filters, call.pagination)
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

For anything that is not a page, `resource` wraps content and declares its links in a block. A link is worth its bytes when it states something
the client would otherwise compute. Here, that is whether a draft can still be deleted:

```kotlin
call.respond(
    resource(book.toResponse()) {
        link("self", "/books/${book.id}")
        if (book.isDraft) link("delete", "/books/${book.id}", HttpMethod.Delete)
    },
)
```

## Validator

Rules read as sentences and attach to properties by reference, so a rename is a compile error rather than a silently dead rule. **A rule that does
not fit does not compile:** `should be email()` on an `Int` is an unresolved reference, and completion inside `property { }` offers only the rules
that apply.

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
the parentheses around a composed rule, because infix calls associate to the left.

```kotlin
property(CreateBookRequest::isbn) {
    should be (uuid() or satisfying("should be an ISBN") { it.isValidIsbn() })
}

property(CreateBookRequest::authorEmail) {
    should be email() describedAs "should be a work email address"
}
```

**A rule stays silent on a `null` property**, so requiring a field and constraining it are two separate assertions: `should notBe nil()`, then
`should be email()`. That is what lets request DTOs declare every property nullable with a `= null` default, so a missing field and a malformed one
are reported together instead of the first failing deserialization alone. `each` validates collection elements as values and `eachNested` as objects,
`whenever` makes a group conditional, and `invariant` states a rule no single property owns:

```kotlin
rulesFor<CreateBookRequest> {
    each(CreateBookRequest::tags) { should notBe blank() }
    eachNested(CreateBookRequest::authors) { property(AuthorRequest::email) { should be email() } }

    whenever(target.draft != true) {
        property(CreateBookRequest::publishedAt) { should notBe nil() }
    }

    invariant("should name at least one author or an editor") { !it.authors.isNullOrEmpty() || it.editor != null }
}
```

`target` is the object under validation, so a rule can depend on a sibling field: `should be after(target.startsAt)`. Temporal rules take an explicit
`timeZone` (the system zone by default) and a `now`, so a comparison need not depend on where the server runs and a test can pin the clock.

## Problem Details

One installer turns validation failures, malformed bodies, deliberate status exceptions and anything otherwise unhandled into
`application/problem+json`.

The domain throws its own exceptions and never picks a status; the web adapter maps each one once:

```kotlin
install(StatusPages) {
    problemDetails {
        namingStrategy = JsonNamingStrategy.SnakeCase

        on<BookNotFoundException> {
            ProblemDetail.fromStatus(HttpStatusCode.NotFound, "No book matches that id", mapOf("id" to it.id.value))
        }
        on<InvalidSortException> {
            ProblemDetail.fromStatus(HttpStatusCode.BadRequest, it.message, mapOf("sortBy" to it.property))
        }
    }
}
```

```json
{
    "type": "about:blank",
    "title": "Not Found",
    "status": 404,
    "detail": "No book matches that id",
    "instance": "/books/42",
    "properties": {
        "id": "42"
    }
}
```

StatusPages resolves by nearest ancestor class, so an `on<E>` mapping wins over the catch-all whatever order it was declared in, and the request path
fills in as `instance` unless you name one. Map `InvalidSortException` and `InvalidFilterException` the first time an endpoint pages or filters, or a
typo in `?sortBy=` answers 500. Do not map every `IllegalArgumentException` instead: a `require` that fails inside the service is a bug, and it
should stay a 500.

Where a failure genuinely is about HTTP, throw the status directly from the adapter. The domain never does:
`throw HttpStatusException(HttpStatusCode.UnsupportedMediaType, "Covers must be image/png or image/jpeg")`.

**An unhandled exception logs its stack trace and answers with a fixed message**, because exception text routinely names the database. Set
`includeExceptionMessage = true` to echo it while developing, and register `exception<T>` handlers after `problemDetails()` to override any of them.

Install it before you have any mappings. Covering the *unmapped* cases is the module's whole point, and a service that adds it later has already
shipped a different error shape to its clients.

## Expander

Lets a client pull in related resources without a second round trip, and without an N+1. Declare once what a response can expand. The spec shapes
a response, so it lives in the web adapter and its batchers call use cases, mapping what they return to response types:

```kotlin
fun bookExpandSpec(findAuthors: FindAuthors): ExpandSpec<BookResponse> =
    ExpandSpec.build<BookResponse> {
        field("author", get = { it.author }, set = { copy(author = it) }) {
            batch { ids, _ -> findAuthors(ids).associate { it.id.value to it.toResponse() } }
        }
    }
```

Build it once, at the top of the route function rather than per request, and apply it to the mapped page:

```kotlin
val bookSpec = bookExpandSpec(findAuthors)

get("/books") {
    val paged = findBooks(call.filters, call.pagination)
    val content = bookSpec.apply(paged.content.map { it.toResponse() }, call.expand)

    call.respond(PagedResponse.from(Paged(paged.page, paged.sortBy, content, paged.totalElements)))
}
```

`?expand=author` resolves a whole page in **one** call to `batch`. `?expand=author.name` projects: `batch` receives `setOf("name")` so it can issue a
narrower query, and only that field is serialized. `?expand=author.books` nests, via a `nested` block:

```kotlin
field("author", get = { it.author }, set = { copy(author = it) }) {
    batch { ids, fields -> findAuthors(ids, fields).associate { it.id.value to it.toResponse() } }
    nested {
        listField("books", get = { it.books }, set = { copy(books = it) }) {
            batch { ids, _ -> findBooksById(ids).associate { it.id.value to it.toResponse() } }
        }
    }
}
```

A nested level is batched once per parent rather than once per page, so keep expansion one level deep until a client needs more.

An `Expandable<T>` serializes as a bare `"author-id"` string while unresolved and as a full object once expanded, so the wire format tells the client
which it got. `field` covers nullable fields, `listField` covers lists, and `polymorphicField` with one `case` per discriminator covers fields whose
type varies per row.

## Cache

Caches a route's result under the request path plus its query parameters, sorted so parameter order does not matter, then hashed. Headers stay out
of the key until `varyHeaders` names them, the way `Vary` does for an HTTP cache. **A cache failure is logged and the request is served from the
origin**, though a coroutine cancellation still propagates.

Cache the response type, which is already serializable, rather than domain entities, which should not be:

```kotlin
get("/books") {
    val response = call.request.withCache("books", cache, excludeQueryKeys = setOf("traceId"), varyHeaders = setOf("Accept-Language")) {
        PagedResponse.from(findBooks(call.filters, call.pagination)) { it.toResponse() }
    }
    call.respond(response)
}
```

The cache is built once where the application is wired, `InMemoryCache(maxSize = 1_000, ttl = 5.minutes)`, and injected. Invalidate after a write
succeeds, in the adapter that knows the namespace:

```kotlin
post("/books") {
    val book = createBook(call.receive<CreateBookRequest>().toDomain())
    cache.invalidateNamespace("books")
    call.respond(HttpStatusCode.Created, book.toResponse())
}
```

`invalidateContaining(id)` also exists, for an entity that appears in entries whose keys do not name it; it reads every entry, so prefer namespaces.

`InMemoryCache` is LRU-bounded with an optional TTL, which suits a single node. **Once more than one instance serves the same traffic, use
`LettuceCache`** over [Lettuce](https://lettuce.io). Otherwise each node holds, and invalidates, its own copy:

```kotlin
val connection = RedisClient.create("redis://localhost:6379").connect(LettuceCache.Codec)
val cache = LettuceCache(connection.async(), ttl = 5.minutes)
```

Nothing else changes: `withCache` and both invalidation helpers behave the same, and Redis applies the TTL itself. You own the connection. One is
thread-safe and multiplexes, so share it, and close it on shutdown. Every key sits under `keyPrefix` (`"ktor-toolkit:"` by default), which bounds
what the invalidation helpers scan. They walk the keyspace with `SCAN`, so keep them to writes rather than requests.

---

## State Machine

States an aggregate's legal moves once, in the domain, instead of leaving them spread across whichever use cases happen to touch the status field.
The module has **no dependencies at all**, not even Ktor, so a framework-free `-core` can depend on it.

```kotlin
val orderFlow = stateMachine<OrderState, OrderEvent, Order> {
    initial(DRAFT)
    final(SHIPPED, CANCELLED)

    state(DRAFT) {
        on<Place>(PLACED) {
            guard("must have at least one line") { it.lines.isNotEmpty() }
        }
        on<Cancel>(CANCELLED)
    }

    state(PLACED) {
        on<Pay>(PAID)
        on<Cancel>(CANCELLED)
    }

    state(PAID) {
        on<Ship>(SHIPPED) { rel = "dispatch" }
    }
}
```

**The definition is checked when it is built**, so an unreachable state, a dead end, a move to a state nobody declared or two moves one event could
both trigger is a `StateMachineDefinitionException` at startup rather than a bug found in production.

The machine never touches the subject. It reports where to go, and the use case applies it. Write the new state conditionally on the old one, so
two concurrent requests cannot both move the same order out of `PLACED`:

```kotlin
class PayOrder(private val orders: OrderRepository) {
    suspend operator fun invoke(id: OrderId): Order {
        val order = orders.findById(id) ?: throw OrderNotFoundException(id)
        val next = orderFlow.fireOrThrow(order, order.state, Pay)

        return orders.moveState(order, from = order.state, to = next) ?: throw OrderChangedConcurrentlyException(id)
    }
}
```

`fireOrThrow` raises `IllegalTransitionException` on a refused event. Map it to a `409` in `problemDetails { }`. Where a refusal is an ordinary
outcome rather than an error, `fire` returns `Accepted` or `Rejected` to branch on instead. Guards may suspend, and so may the `effect`, `onEnter`,
`onExit` and `onTransition` hooks, but they run before the caller persists anything, so anything that leaves the process belongs in the use case,
after the write.

**A guard asks about the subject, never the event.** That is what lets the machine answer "what can this order do right now?" without being handed an
event to test with, so the affordances and the rules that decide them cannot drift apart:

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

They are written for the repository that installs them, not for this one. Install with the commands under [Let the agent do
it](#let-the-agent-do-it).

**`/ktor-toolkit:start` runs first, before any Kotlin is written or changed.** It establishes which toolkit modules are on your classpath and what
your project's build gate is, then routes the task to the skills that apply, usually two or three. Running it after the code is written is too late
to be useful.

| Skill             | What it covers                                                                          |
|-------------------|-----------------------------------------------------------------------------------------|
| `start`           | **Entrypoint.** Orients in the project, routes the task to the skills below             |
| `install`         | Adds the toolkit to a project: modules, Gradle wiring, Ktor plugins, these skills       |
| `endpoint`        | A new or changed HTTP route, end to end across `-core`, `-adapters` and `-app`          |
| `architecture`    | Ports & Adapters layout, the three-module split, where a file goes                      |
| `pagination`      | `?page`, `?pageSize`, `?sortBy`, carrying `Pagination` into the persistence adapter     |
| `filtering`       | `?filterBy=`, the 400 contract, allow-lists, counting with the same filters             |
| `hateoas`         | `_links`, page links, the `resource { }` builder                                        |
| `validation`      | `rulesFor<T>`, syntactic vs business rules, the nullable-DTO convention                 |
| `problem-details` | `problemDetails { }`, status codes, mapping domain exceptions                           |
| `expand`          | `ExpandSpec`, batched resolution, the `Expandable` wire contract                        |
| `cache`           | `withCache`, choosing a store, TTLs, who invalidates and when                           |
| `state-machine`   | `stateMachine { }`, guards as affordances, transitions as `_links`                      |
| `migrations`      | Versioned SQL under Flyway, where it lives, baselining, expand/contract schema changes  |
| `di`              | Ktor's native DI: `provide<Port> { Impl(resolve()) }`, lifetimes, test overrides        |
| `tests`           | Kotest ShouldSpec naming, MockK, `testApplication`, Testcontainers, acceptance tests    |
| `kover`           | The `report` aggregation module, a threshold that only rises, the rule for exclusions   |
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

Each skill also says what to do when it describes something your repository does not have yet, such as a Makefile, a Dockerfile, a `CHANGELOG.md` or
a coverage module. It proposes the file and waits for you, rather than scaffolding it unasked or quietly working around its absence.

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
