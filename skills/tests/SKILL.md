---
name: tests
description: >-
    How this project tests — Kotest ShouldSpec with behaviour-named cases, MockK for collaborators,
    testApplication for routes, Testcontainers for real persistence, and acceptance tests over the
    assembled app. Use whenever writing or reviewing a test, when naming a context or a should, when
    deciding what to mock and what to run for real, when a bug fix needs a failing test first, when a
    test is flaky or time-dependent, and when choosing between a unit, integration or acceptance
    test. Covers spec style, naming conventions, fixtures and builders, determinism, Kotest matchers
    and where each kind of test lives.
---

# Testing

## Prefer the test that would have caught it

The house preference is **acceptance tests first**, then integration, then unit — the inverse of the usual advice, and deliberately so. Most bugs that
reach production are not wrong arithmetic inside a function; they are a route wired to the wrong use case, a missing plugin, a serializer that renames
a field, an adapter whose query does not match the port's contract. Unit tests are structurally incapable of seeing any of those.

That is a preference, not a ban. A unit test earns its place when the logic has enough cases that driving them through HTTP would be slow and
unreadable — parsing, clamping, date arithmetic, anything with a table of inputs. Write those as units *and* one acceptance test that proves the
feature is reachable.

The failure mode this guards against is a service with 400 green unit tests where the endpoint 500s on the first request because nothing was ever
wired together.

## Where each kind lives

**A test lives in the module of the code it tests.** Acceptance tests are the one exception: they belong to no single module, so they get their own.

| Location                      | Tests                                        | Runs against                                                                          |
|-------------------------------|----------------------------------------------|---------------------------------------------------------------------------------------|
| `<service>-core/src/test`     | Entities, value objects, use cases           | Real domain objects, mocked ports. No framework, no I/O.                              |
| `<service>-adapters/src/test` | Route functions, repository implementations  | `testApplication` with just the route under test; Testcontainers for a real database. |
| `<service>-app/src/test`      | Plugin configuration and wiring, if anything | Rarely needed — `-app` mostly has no logic of its own.                                |
| `acceptance-tests/src/test`   | Whole features                               | The assembled application, over HTTP, as a client sees it.                            |

There is no `-core` test that starts a server and no `-adapters` test of a domain rule. If you find yourself writing one, the code is in the wrong
module — load the `ktor-toolkit:architecture` skill.

`acceptance-tests` is a separate Gradle module precisely so it *cannot* reach into internals. It depends on the app the way a client does, which is
what makes it the test that would have caught the wiring bug.

The distinction that matters in practice is between the two Ktor tests. An `-adapters` route test installs only what that route needs and mocks the
use case behind it, so a failure names the adapter. An acceptance test boots `module()` and touches the real graph, so a failure means the feature is
broken for a client. Both use `testApplication`; they differ in how much they assemble.

## The shape of a test

Kotest `ShouldSpec`, always. The project uses it exclusively across every module; a second spec style is a style argument nobody needs to have.

```kotlin
class PaginationExtensionsTest :
    ShouldSpec({
        context("PaginationRequest.toPagination") {
            should("carry the page and the sort criteria over") {
                val sortBy = listOf(Sort("name", Sort.Direction.ASC))
                val request = PaginationRequest(Page(2, 25), sortBy)

                val pagination = request.toPagination()

                pagination.page shouldBe Page(2, 25)
                pagination.sortBy shouldBe sortBy
            }

            should("produce an empty sort when none was requested") {
                PaginationRequest().toPagination().sortBy shouldBe emptyList()
            }
        }
    })
```

**Three blocks, blank-line separated: arrange, act, assert.** No comments needed; the shape carries it. A one-line case that reads clearly as a single
expression can skip the separation, as the second
`should` above does. One behaviour per `should`, so a failure names the thing that broke.

**File naming:** `XTest.kt`, in the package of the thing under test.

### Naming a `context`

A `context` names **what is being exercised**, and there are two idioms in use. Pick whichever makes the `should` names underneath read naturally.

**The API surface** — the function, property or entry point under test. Use the name as written, including the receiver and, when overloads differ
meaningfully, the parameters:

```
context("PaginationRequest.from(Parameters)")
context("PaginationRequest.from(page, pageSize, sortBy)")
context("Sort.fromString")
context("List<Sort>.toExposedQueryExpression")
context("ApplicationCall.expand")
context("buildCacheKey")
context("withCache")
context("toResource")
context("rulesFor")
context("invariant")
```

**The situation** — the state of the world the cases share. Use this when the interesting variable is the input rather than the entry point:

```
context("a paged route")
context("an absent value")
context("a malformed spec")
context("a rule with no opinion about absence")
context("an application's own exception")
context("unhandled exceptions")
context("which links are emitted")
context("the window is symmetric")
context("without a zone")
```

Nest a second `context` to split variants, so each leaf stays about one thing:

```
context("size bound") {
    context("on a string") { … }
    context("on a collection") { … }
    context("on a map") { … }
    context("on an array") { … }
}
```

Common nesting axes here: by type (`on a LocalDate`, `on an Instant`), by direction (`serialization`, `deserialization`), by DSL phrasing (`be blank`,
`notBe blank`), and by lifecycle (`construction`, `comparing two links`).

### Naming a `should`

**Name the behaviour, not the method.** Read it aloud with "it should" in front — if that is not a sentence about what the software does for someone,
rename it. Never `should("test coerceIn")`.

Start with a verb. The vocabulary that recurs here: *accept*, *reject*, *return*, *fall back to*, *default to*, *carry … through*, *skip*, *stay
quiet*, *record*, *emit*, *apply*, *drop*, *strip*, *hold for*, *answer with*, *consider … equal*.

```
should("apply the standard maximum page size when the caller names none")
should("fall back to the defaults when nothing is supplied")
should("produce an empty sort when none was requested")
should("index the errors it records")
should("skip an absent collection")
should("do nothing for an empty collection")
should("stay quiet when the condition holds")
should("emit only self when everything fits on one page")
should("delete only the keys under the namespace")
should("ask for every ref id in one call")
should("answer with the exception's status and detail as problem+json")
should("accept the smallest expiry Redis records")
```

**The best names carry a *because*.** A name that says why the behaviour matters survives a refactor; one that restates the assertion does not:

```
should("strip the prefix, so namespace invalidation still matches")
should("count a read as a use, so a hot key survives")
should("let a Redis failure propagate, so the caller can fall back to the origin")
should("reject a ttl Redis would round down to no expiry at all")
should("answer false for a value it cannot compare against a point in time, rather than throw")
should("drop the quoting when the error belongs to the object itself")
```

Each of those tells the next reader what breaks if they change the behaviour — which is the whole job of a test name.

## Mocking

**MockK is the default for collaborators.** A use case's ports, a repository, an external client:
mock them, and state exactly what the test needs them to do.

```kotlin
class FindBooksTest :
    ShouldSpec({
        context("FindBooks") {
            should("carry the requested page and sort into the result") {
                val books = mockk<BookRepository>()
                val pagination = Pagination(Page(1, 10), listOf(Sort("title", ASC)))
                coEvery { books.findAll(pagination) } returns listOf(book())
                coEvery { books.count() } returns 25

                val paged = FindBooks(books)(pagination)

                paged.page shouldBe Page(1, 10)
                paged.totalElements shouldBe 25
            }
        }
    })
```

`coEvery` for `suspend` functions, `every` for the rest — the ports in this architecture are suspending, so `coEvery` is what you will reach for most.
`mockk(relaxed = true)` when a test only cares about one interaction and the others are noise, though naming each stub is usually clearer about what
the test depends on.

MockK also serves where a real object is impossible or beside the point: a third-party client that would open a network connection, and a constructor
argument the test never touches —
`LettuceCache(mockk(), ttl = Duration.ZERO)`, where the point is that the constructor rejects the ttl.

**What not to mock:**

| Do not mock                           | Why                                                       |
|---------------------------------------|-----------------------------------------------------------|
| The class under test                  | The test then asserts on itself                           |
| An entity, value object or data class | No behaviour to stub; construct a real one with a builder |
| A `@Serializable` DTO                 | Stubbing getters proves nothing about serialization       |
| A pure function you own               | Call it                                                   |

**Verifying interactions is for when the interaction *is* the behaviour.** `coVerify {
cache.invalidateNamespace("books") }` is a real assertion — invalidation is not observable any other way. But `coVerify { repository.save(any()) }`
next to an assertion on the result is asserting *how*
the code works, so it fails on every refactor that keeps the behaviour intact. Prefer asserting on the outcome; add `coVerify` when the call is the
outcome.

Mocking is for collaborators the code under test *talks to*. When a test starts stubbing three levels deep to reach one assertion, that is the design
telling you the use case has too many dependencies — fix the design rather than the test.

## Fixtures and builders

Give every fixture a default for every field, so a test names only what it is about:

```kotlin
fun book(
    id: String = "book-1",
    title: String = "The Hobbit",
    isbn: Isbn = Isbn("978-0261102217"),
    publishedAt: LocalDate? = LocalDate(1937, 9, 21),
) = Book(id, title, isbn, publishedAt)
```

```kotlin
val draft = book(title = "Untitled", publishedAt = null)
```

The reader sees immediately that this test is about the title and the date, and that nothing else matters. A test that spells out eight constructor
arguments hides its own point, and every new field on `Book` breaks every test that ever built one.

This is why entities are built rather than mocked: `book(title = "Untitled")` is shorter than stubbing four properties and cannot drift from the real
constructor.

Keep small helpers private and local to the file — a `bytes()`, a function that pulls `_links` out of a JSON body. Promote one to a shared fixture
only when a third file needs it, and give shared helpers a KDoc line saying *why* they exist:

```kotlin
/** A clock the test moves by hand, so expiry can be exercised without sleeping. */
```

## Testing a route — `<service>-adapters/src/test`

`testApplication` runs the real Ktor pipeline in-process — real routing, real plugins, real serialization — with no port to bind. In `-adapters`,
assemble only the route under test and mock the use case behind it, so a failure points at the adapter and nothing else:

```kotlin
class BookRoutesTest :
    ShouldSpec({
        context("GET /books") {
            should("carry the page metadata into the response") {
                val findBooks = mockk<FindBooks>()
                coEvery { findBooks(any()) } returns Paged(Page(0, 10), emptyList(), books(25), 25)

                testApplication {
                    application {
                        install(ContentNegotiation) { json() }
                        dependencies { provide<FindBooks> { findBooks } }
                        routing { bookRoutes() }
                    }

                    val response = client.get("/books?page=0&pageSize=10")

                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["metadata"]!!.jsonObject["totalPages"]!!.jsonPrimitive.int shouldBe 3
                }
            }
        }
    })
```

Register only the mock the route resolves, and install only the plugins it uses. Nothing else in the graph is stood up, so a failure here is the
adapter's — load the `ktor-toolkit:di` skill for overriding a registration when the real module is booted.

**Assert on the JSON, not on a deserialized object.** Deserializing with your own `@Serializable`
class asserts that your class round-trips with itself and would not notice a renamed field, a naming-strategy change, or a null where the client
expects a value. Parsing the body as
`JsonElement` tests the contract the client actually receives.

## Testing a feature — `acceptance-tests/src/test`

Same tool, everything assembled. Boot the real `module()`, so routing, DI, `problemDetails`,
`RequestValidation` and content negotiation are all exercised together:

```kotlin
class CreateBookAcceptanceTest :
    ShouldSpec({
        context("POST /books") {
            should("reject a blank title with a problem detail naming the field") {
                testApplication {
                    application { module() }

                    val response = client.post("/books") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"title": "", "isbn": "978-0261102217"}""")
                    }

                    response.status shouldBe HttpStatusCode.BadRequest
                    response.contentType()?.withoutParameters() shouldBe
                        ContentType("application", "problem+json")
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["properties"]!!.jsonObject.keys shouldContain "$.title"
                }
            }
        }
    })
```

Send the body as a raw string rather than a serialized DTO. The point of an acceptance test is to prove the service handles what a client actually
sends, and a DTO the service also owns cannot disagree with itself.

This layer is where the cross-cutting wiring is proved: that the error shape is `problem+json`, that validation reached the client with a usable field
path, that a route exists at the path documented. None of that is visible from a unit test, and an `-adapters` test only sees the half it assembled.

## Testing persistence — `<service>-adapters/src/test`

A repository test that mocks the database tests the mock: mocking is for the collaborators of the code under test, and for a repository the database
*is* the code under test. Run the real thing with Testcontainers:

```kotlin
private val postgres = PostgreSQLContainer("postgres:17-alpine")
```

Testcontainers is **not** in the version catalog yet — add it there rather than inline, and give
`acceptance-tests` and `-adapters` the dependency they need (load the `ktor-toolkit:gradle` skill, which covers both). It needs a working Docker
daemon, so the CI runner must provide one.

Start the container once for the spec rather than per test, and let each test own its data — insert what it needs, assert, and rely on a transaction
rollback or a truncate between cases. Shared mutable fixtures across tests are the usual cause of a suite that passes alone and fails in parallel.

**Build the container's schema with the real migrations**, not `SchemaUtils.create`. A query tested against a table the migrations never produced
proves nothing about production, and the two drift silently — load the `ktor-toolkit:migrations` skill.

## Determinism

A flaky test is worse than no test: it trains people to re-run the build.

- **Control the clock.** Never let code a test drives call `Clock.System.now()` — take a `Clock`
  parameter and stub it: `every { clock.now() } returns Instant.parse("2024-01-01T00:00:00Z")`, or
  `returnsMany` when the test needs time to move. The toolkit's temporal validators take an explicit
  `now` for exactly this reason. A hand-written clock you advance is fine when a test steps through many instants and `returnsMany` would obscure the
  sequence.
- **Never sleep.** A `delay` in a test is a slow test that will still be flaky on a loaded CI machine.
- **Do not assume ordering** the code does not guarantee. `shouldContainExactlyInAnyOrder` states what you mean; `shouldContainExactly` on a `Map`'s
  values is a coin flip.
- **Pin generated values.** Random ids and `LocalDate.now()` in a fixture make a failure unreproducible. Pass them in.

## Assertions

Kotest matchers, chosen for what they say when they fail:

| Use                                | For                                                                  |
|------------------------------------|----------------------------------------------------------------------|
| `shouldBe`                         | Values                                                               |
| `shouldBeNull` / `shouldNotBeNull` | Absence                                                              |
| `shouldContainExactly`             | Order matters                                                        |
| `shouldContainExactlyInAnyOrder`   | Order does not                                                       |
| `shouldThrow<T> { }`               | The type of failure — assert the message too when it is the contract |

`result shouldBe expected` on a data class reports the differing field. Asserting field by field gives up that report and stops at the first mismatch.

## Before a bug is fixed

Write the failing test first, and watch it fail for the *stated* reason. A test that passes before the fix is testing something else, and you will not
find out until the bug returns.

Put it at the level the bug lived at: an off-by-one in page arithmetic is a unit test, a route that returned 500 is an acceptance test.

## Common mistakes

| Mistake                                            | Why it hurts                                                              |
|----------------------------------------------------|---------------------------------------------------------------------------|
| `coVerify` on every interaction                    | Asserts the implementation, so every refactor is a test rewrite           |
| Mocking an entity or data class                    | Nothing to stub, and the stub drifts from the real constructor            |
| Mocking the database in a repository test          | The database is what is under test                                        |
| `every` where `coEvery` is needed                  | The stub never matches; the mock throws on a call it was never told about |
| Deserializing the response with your own DTO       | Cannot see a renamed field or a naming-strategy change                    |
| `Thread.sleep` / `delay` to wait for expiry        | Slow, and flaky on a loaded machine                                       |
| Fixtures with every field spelled out              | Hides the point of the test; every new field breaks every test            |
| Shared mutable state between tests                 | Passes alone, fails in parallel, blamed on Kotest                         |
| Only unit tests                                    | Nothing proves the endpoint is wired at all                               |
| An acceptance test living in `-adapters` or `-app` | It can reach internals, so it stops testing what a client sees            |
| Booting the whole `module()` for an adapter test   | A failure could be anywhere in the graph; the test names nothing          |
| `should("test findAll")`                           | Names the method; says nothing about the behaviour                        |
| A `-core` test that starts a server                | The domain has a dependency it should not have                            |
