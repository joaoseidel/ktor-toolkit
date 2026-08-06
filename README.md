# Ktor Toolkit

A set of small, independent libraries for building JSON APIs with [Ktor](https://ktor.io) — the
pieces most services end up writing themselves: pagination, HATEOAS links, request validation,
RFC 9457 error responses, sparse field expansion and response caching.

Every module stands alone. Take the one you need; nothing pulls in the others except `hateoas`,
which builds on `paginator`.

Requires **Java 21+**, Kotlin 2.3 and Ktor 3.4.

## Modules

| Module | What it does |
|---|---|
| [`ktor-toolkit-paginator`](#paginator) | Parses `?page`/`?pageSize`/`?sortBy`, and shapes the paged response |
| [`ktor-toolkit-hateoas`](#hateoas) | Wraps a response in a `_links` envelope, with pagination links built for you |
| [`ktor-toolkit-validator`](#validator) | A type-safe `should be` / `should notBe` DSL over Ktor's RequestValidation |
| [`ktor-toolkit-problem-details`](#problem-details) | Turns exceptions into RFC 9457 `application/problem+json` responses |
| [`ktor-toolkit-expander`](#expander) | Resolves `?expand=author.books` references, batching one query per field |
| [`ktor-toolkit-cache`](#cache) | Caches a response by request path and query, over any key–value store |

## Installation

No public repository is wired yet. Build and install into your local Maven repository:

```bash
make publish
```

Then depend on the modules you need:

```kotlin
dependencies {
    implementation("com.github.joaoseidel.ktor-toolkit:ktor-toolkit-paginator:1.0.0")
    implementation("com.github.joaoseidel.ktor-toolkit:ktor-toolkit-hateoas:1.0.0")
    implementation("com.github.joaoseidel.ktor-toolkit:ktor-toolkit-validator:1.0.0")
    implementation("com.github.joaoseidel.ktor-toolkit:ktor-toolkit-problem-details:1.0.0")
    implementation("com.github.joaoseidel.ktor-toolkit:ktor-toolkit-expander:1.0.0")
    implementation("com.github.joaoseidel.ktor-toolkit:ktor-toolkit-cache:1.0.0")
}
```

Ktor and kotlinx-serialization come along transitively — they appear in these modules' public
signatures, so they are declared as `api` dependencies.

### Optional dependencies

A few features are compiled against libraries their module does **not** bring with it. If you use
them, add the dependency yourself:

| Feature | You must add |
|---|---|
| `Sort.toExposedQueryExpression(...)` | `org.jetbrains.exposed:exposed-core` |
| `Sort.toMongoSortExpression(...)` | `org.mongodb:mongodb-driver-core` (comes with any MongoDB driver) |
| `LettuceCache` | `io.lettuce:lettuce-core` |

Everything else in the toolkit works without them.

## Paginator

Read the pagination off the call, hand it to your repository, wrap the result:

```kotlin
get("/books") {
    val pagination = call.pagination
    val (page, pageSize) = pagination.page

    val books = repository.findAll(pagination)
    val paged = Paged(pagination.page, pagination.sortBy, books, repository.count())

    call.respond(PagedResponse.from(paged) { it.toResponse() })
}
```

`call.pagination` never fails the request: an unparseable `?page=abc` falls back to the default, a
negative page becomes 0, and the page size is clamped to `1..100`. Use
`call.paginationRequest(defaultPageSize = 20, maxPageSize = 200)` for different bounds.

`?sortBy=name,-createdAt` parses into `[Sort("name", ASC), Sort("createdAt", DESC)]`. Build a
default ordering from property references instead of strings, so a rename cannot leave a stale sort
key behind:

```kotlin
val ordering = sortBy {
    desc(Book::publishedAt)
    asc(Book::title)
}
```

Turn either into a query with an explicit allow-list of sortable columns — anything outside it
raises `IllegalArgumentException` rather than reaching the database:

```kotlin
Books.selectAll().orderBy(*pagination.sortBy.toExposedQueryExpression(Books.title, Books.createdAt).toTypedArray())
```

MongoDB works the same way, except the criteria collapse into the single sort document `find` takes.
The allow-list is field names, or property references of the document class:

```kotlin
collection.find().sort(pagination.sortBy.toMongoSortExpression(BookDocument::title, BookDocument::createdAt))
```

The response carries a `metadata` block: `page`, `pageSize`, `totalPages` (a count — the last page
index is `totalPages - 1`), `totalElements`, `hasNext`, `hasPrevious`, `isSorted` and `sortCriteria`.

## HATEOAS

`toResource(call)` wraps a paged response and attaches `self`, `next`, `prev`, `first` and `last` —
only the ones that point at a page that exists. Every other query parameter of the current request
is carried over, correctly percent-encoded.

```kotlin
get("/books") {
    val response = PagedResponse.from(paged) { it.toResponse() }
    call.respond(response.toResource(call))
}
```

```json
{
  "metadata": { "page": 0, "pageSize": 10, "totalPages": 3, "hasNext": true },
  "content": [ … ],
  "_links": [
    { "rel": "self", "href": "/books?page=0&pageSize=10", "method": "GET" },
    { "rel": "next", "href": "/books?page=1&pageSize=10", "method": "GET" },
    { "rel": "last", "href": "/books?page=2&pageSize=10", "method": "GET" }
  ]
}
```

Pass your own links as a second argument to publish them after the pagination ones.

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

Validation rules read as sentences and attach to properties by reference, so a rename is a compile
error rather than a silently dead rule:

```kotlin
install(RequestValidation) {
    rulesFor<CreateBookRequest> {
        property(CreateBookRequest::title) {
            should notBe blank()
            should be size(min = 3, max = 200)
        }
        property(CreateBookRequest::authorEmail) {
            should be email()
        }
        property(CreateBookRequest::publishedAt) {
            should be past()
        }
        nested(CreateBookRequest::publisher) {
            property(Publisher::name) { should notBe blank() }
        }
    }
}
```

For anything beyond a couple of fields, implement `RequestValidator<T>` and pass the instance to
`rulesFrom(validator)` instead.

Available rules, and the property types each applies to:

| Rule | Applies to |
| --- | --- |
| `blank`, `email`, `pattern` | `String` |
| `uuid` | `String`, `UUID`, `Uuid` |
| `size` | `String`, `Collection`, `Map`, `Array` |
| `min`, `max`, `inRange`, `positive`, `negative` | any `Number` |
| `past`, `future`, `before`, `after`, `within` | `LocalDate`, `LocalDateTime`, `Instant` |
| `nil`, `satisfying` | any |

**A rule that does not fit does not compile.** `should be email()` on an `Int` property is an
unresolved reference, not a runtime error, and completion inside a `property { }` block only offers
the rules that apply.

**Composition.** Rules are values: combine them with `and`, `or` and `!`, and write a one-off with
`satisfying`. Note the parentheses — infix calls associate to the left.

```kotlin
property(CreateBookRequest::isbn) {
    should be (uuid() or satisfying("should be an ISBN") { it.isValidIsbn() })
}
```

**Messages.** Every rule carries a default message. Override it with `describedAs`, which needs no
parentheses when it follows an assertion:

```kotlin
should be email() describedAs "should be a work email address"
```

**Absent values.** A rule stays silent on a `null` property — `should be email()` does not complain
about an optional field that was not sent. `nil` is the one rule with an opinion about absence, so
requiring a field and constraining it are two separate assertions:

```kotlin
property(CreateBookRequest::authorEmail) {
    should notBe nil()
    should be email()
}
```

**Collections, conditions and invariants.** `each` validates elements as values and `eachNested` as
objects, reporting at `tags[0]` and `authors[0].email`. `whenever` makes a group of rules
conditional, and `invariant` states a rule that no single property owns:

```kotlin
rulesFor<CreateBookRequest> {
    each(CreateBookRequest::tags) { should notBe blank() }

    eachNested(CreateBookRequest::authors) {
        property(Author::email) { should be email() }
    }

    whenever(!target.draft) {
        property(CreateBookRequest::publishedAt) { should notBe nil() }
    }

    invariant("should not be dated before its author was born") { it.publishedAt > it.authorBornAt }
}
```

`target` is the object under validation, so a rule can also depend on a sibling field:
`should be after(target.startsAt)`.

**Time zones.** The temporal rules take an explicit `timeZone`, defaulting to the system zone. Pass
`timeZone = TimeZone.UTC` when the comparison should not depend on where the server runs, and `now`
to make a test deterministic.

## Problem Details

One installer turns validation failures, malformed bodies, deliberate status exceptions and
anything otherwise unhandled into `application/problem+json`:

```kotlin
install(StatusPages) {
    problemDetails {
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
}
```

Throw `HttpStatusException` where you want a specific status:

```kotlin
throw HttpStatusException(HttpStatusCode.NotFound, "Book not found", mapOf("id" to id))
```

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Book not found",
  "instance": "/books/42",
  "properties": { "id": "42" }
}
```

Map your own exceptions in the same block with `on<E>`. StatusPages resolves by nearest ancestor
class, so a mapping wins over the catch-all whatever order it was declared in, and the request path
fills in as the problem's `instance` unless you name one:

```kotlin
install(StatusPages) {
    problemDetails {
        on<BookNotFoundException> { ProblemDetail.fromStatus(HttpStatusCode.NotFound, "No book ${it.id}") }
    }
}
```

An unhandled exception logs its stack trace and answers with a fixed message — the exception text
routinely names the database. Set `includeExceptionMessage = true` to echo it while developing.

Register your own `exception<T>` handlers after `problemDetails()` to override any of them.

## Expander

Declare once which fields a response can expand, then let the client ask:

```kotlin
val bookSpec = ExpandSpec.build<BookResponse> {
    field("author", get = { it.author }, set = { copy(author = it) }) {
        batch { ids, _ -> authorRepository.findAllById(ids).associateBy { it.id } }
    }
}

get("/books") {
    val books = repository.findAll()
    call.respond(bookSpec.apply(books, call.expand))
}
```

`?expand=author` resolves the whole page in **one** call to `batch`, not one per row.
`?expand=author.name` projects: the `batch` lambda receives `setOf("name")` so it can issue a
narrower query, and only that field is serialized.

`?expand=author.books` nests, via a `nested` block on the field — inline, or an existing spec:

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

An `Expandable<T>` field serializes as a bare `"author-id"` string while unresolved and as a full
object once expanded, so the wire format tells the client which it got. `field` covers nullable
fields too; register list fields with `listField`, and fields whose type varies per row with
`polymorphicField` and one `case` per discriminator.

## Cache

```kotlin
val cache = InMemoryCache(maxSize = 1_000, ttl = 5.minutes)

get("/books") {
    val books = call.request.withCache("books", cache, excludeQueryKeys = setOf("traceId")) {
        repository.findAll()
    }
    call.respond(books)
}
```

The key is the request path plus its query parameters, sorted so parameter order does not matter,
then hashed. A cache failure is logged and the request is served from the origin — but a coroutine
cancellation still propagates.

Invalidate on write:

```kotlin
cache.invalidateNamespace("books")   // everything under the namespace
cache.invalidateContaining(bookId)   // entries whose payload mentions this id
```

`InMemoryCache` is LRU-bounded with an optional TTL, which suits a single node. Reach for a shared
store once more than one instance serves the same traffic — otherwise each holds, and invalidates,
its own copy.

### Redis

`LettuceCache` is that shared store, over [Lettuce](https://lettuce.io). Add
`io.lettuce:lettuce-core` yourself — the toolkit only compiles against it.

```kotlin
val client = RedisClient.create("redis://localhost:6379")
val connection = client.connect(LettuceCache.Codec)   // keys are text, values raw bytes
val cache = LettuceCache(connection.async(), ttl = 5.minutes)
```

Nothing else changes: `withCache` and both invalidation helpers work the same, and Redis applies
the TTL itself. The connection belongs to you — share it (it is thread-safe and multiplexes) and
close it on shutdown. `connection.async()` from a cluster client fits too.

Every key is stored under `keyPrefix`, `"ktor-toolkit:"` by default, which keeps the cache in its
own corner of a shared instance and bounds what `invalidateNamespace` and `invalidateContaining`
have to scan — they walk the keyspace with `SCAN`, so keep them to writes, not requests.

## Development

```bash
make build      # compile, test and lint every module
make test       # tests only
make coverage   # report/build/reports/kover/html/index.html
make format     # apply ktlint
make api        # refresh the public API dumps after an intentional change
```

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE)
