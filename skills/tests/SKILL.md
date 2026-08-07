---
name: tests
description: >-
    Testing a Ktor Toolkit service — Kotest ShouldSpec with behaviour-named cases, MockK for
    collaborators, testApplication for routes, Testcontainers for real persistence, and acceptance
    tests over the assembled app. Use when writing or reviewing any test, naming a context or a
    should, deciding what to mock and what to run for real, or when a test is flaky.
---

# Testing

## Prefer the test that would have caught it

**Acceptance first, then integration, then unit** — the inverse of the usual advice. Bugs that reach production are rarely wrong arithmetic inside a
function; they are a route wired to the wrong use case, a missing plugin, a serializer that renamed a field, an adapter whose query does not match the
port. A unit test cannot see any of those, which is how a service ends up with 400 green tests and a 500 on the first request.

Not a ban. Logic with a table of inputs — parsing, clamping, date arithmetic — is slow and unreadable driven through HTTP. Write those as units *and*
one acceptance test proving the feature is reachable.

## Where each kind lives

**A test lives in the module of the code it tests.** Acceptance tests belong to no single module, so they get their own.

| Location                      | Tests                                        | Runs against                                                                          |
|-------------------------------|----------------------------------------------|---------------------------------------------------------------------------------------|
| `<service>-core/src/test`     | Entities, value objects, use cases           | Real domain objects, mocked ports. No framework, no I/O.                              |
| `<service>-adapters/src/test` | Route functions, repository implementations  | `testApplication` with just the route under test; Testcontainers for a real database. |
| `<service>-app/src/test`      | Plugin configuration and wiring, if anything | Rarely needed — `-app` mostly has no logic of its own.                                |
| `acceptance-tests/src/test`   | Whole features                               | The assembled application, over HTTP, as a client sees it.                            |

A `-core` test that starts a server, or an `-adapters` test of a domain rule, means the code is in the wrong module — load the
`ktor-toolkit:architecture` skill.

**The two Ktor tests differ only in how much they assemble.** An `-adapters` route test installs what that route needs and mocks the use case behind
it, so a failure names the adapter. An acceptance test boots `module()` against the real graph, so a failure means the feature is broken for a client.
`acceptance-tests` is its own module precisely so it cannot reach internals.

**Where there is no `acceptance-tests` module**, offer one — a settings entry plus a build script — the first time a task needs a whole-app test, and
wait. Until it exists, boot `module()` from an app-level test and say that is what you did.

## The shape of a test

Kotest `ShouldSpec`, one style across every module. Where the project already standardised on `FunSpec`, `BehaviorSpec` or JUnit, match it and apply
the naming rules below to that.

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

**Three blocks, blank-line separated: arrange, act, assert.** The shape carries it; no comments needed. A one-line case can skip the separation, as
the second `should` above does. One behaviour per `should`, so a failure names the thing that broke.

**File naming:** `XTest.kt`, in the package of the thing under test.

### Naming a `context`

A `context` names **what is being exercised**. Two idioms — pick whichever makes the `should` names underneath read naturally.

**The API surface**, written as it appears in code, including the receiver and, where overloads differ meaningfully, the parameters:

```
context("GET /books")
context("ExposedBookRepository.findAll")
context("Isbn.fromString")
context("CreateBookRequest.toDomain")
```

**The situation** — the state the cases share. Use this when the interesting variable is the input rather than the entry point:

```
context("a paged route")
context("an absent value")
context("a rule with no opinion about absence")
context("unhandled exceptions")
```

Nest a second `context` to split variants, so each leaf stays about one thing:

```
context("size bound") {
    context("on a string") { … }
    context("on a collection") { … }
}
```

Nest by type, by direction (`serialization` / `deserialization`), by phrasing (`be blank` / `notBe blank`), or by lifecycle.

### Naming a `should`

**Name the behaviour, not the method.** Read it with "it should" in front — if that is not a sentence about what the software does for someone, rename
it. Never `should("test coerceIn")`.

Start with a verb: *accept*, *reject*, *fall back to*, *default to*, *carry … through*, *skip*, *stay quiet*, *emit*, *drop*, *answer with*.

```
should("apply the standard maximum page size when the caller names none")
should("produce an empty sort when none was requested")
should("stay quiet when the condition holds")
should("emit only self when everything fits on one page")
should("answer with the exception's status and detail as problem+json")
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

`coEvery` for `suspend` functions, `every` for the rest — ports here are suspending, so `coEvery` is the one you reach for. Use
`mockk(relaxed = true)` only when a test cares about one interaction and the rest are noise; naming each stub states what the test depends on.

MockK also covers a constructor argument the test never touches — `LettuceCache(mockk(), ttl = Duration.ZERO)`, where the point is that the
constructor rejects the ttl.

**What not to mock:**

| Do not mock                           | Why                                                       |
|---------------------------------------|-----------------------------------------------------------|
| The class under test                  | The test then asserts on itself                           |
| An entity, value object or data class | No behaviour to stub; construct a real one with a builder |
| A `@Serializable` DTO                 | Stubbing getters proves nothing about serialization       |
| A pure function you own               | Call it                                                   |

**Verify an interaction only when the interaction *is* the behaviour.** `coVerify { cache.invalidateNamespace("books") }` is a real assertion —
invalidation is observable no other way. `coVerify { repository.save(any()) }` beside an assertion on the result asserts *how* the code works, so it
breaks on every refactor that keeps the behaviour intact.

When a test stubs three levels deep to reach one assertion, the use case has too many dependencies. Fix the design, not the test.

## Fixtures and builders

Give every fixture a default for every field, so a test names only what it is about:

```kotlin
fun book(
    id: String = "book-1",
    title: String = "The Hobbit",
    isbn: Isbn = Isbn("978-0261102217"),
    publishedAt: LocalDate? = LocalDate(1937, 9, 21),
) = Book(id, title, isbn, publishedAt)

val draft = book(title = "Untitled", publishedAt = null)
```

That test is visibly about the title and the date, and nothing else. Spelling out eight constructor arguments hides the point, and every new field on
`Book` then breaks every test that ever built one. It is also why entities are built rather than mocked: `book(title = "Untitled")` is shorter than
stubbing four properties and cannot drift from the real constructor.

Keep helpers private and local. Promote one to a shared fixture when a third file needs it, with a KDoc line saying why it exists:

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

Register only the mock the route resolves and install only the plugins it uses, so a failure here is the adapter's — load the `ktor-toolkit:di` skill
for overriding a registration when the real module is booted.

**Assert on the JSON, not on a deserialized object.** Deserializing with your own `@Serializable` class only proves it round-trips with itself; it
cannot see a renamed field, a naming-strategy change, or a null where the client expects a value. Parsing as `JsonElement` tests the contract the
client receives.

## Testing a feature — `acceptance-tests/src/test`

Same tool, everything assembled. Boot the real `module()`, so routing, DI, `problemDetails`,
`RequestValidation` and content negotiation are exercised together:

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

**Send the body as a raw string, not a serialized DTO.** A DTO the service also owns cannot disagree with itself; the point is to prove the service
handles what a client actually sends.

This layer proves the cross-cutting wiring — that errors are `problem+json`, that validation reached the client with a usable field path, that the
route exists at the documented path. A unit test sees none of it, and an `-adapters` test sees only the half it assembled.

## Testing persistence — `<service>-adapters/src/test`

For a repository, the database *is* the code under test — mocking it tests the mock. Run the real thing with Testcontainers:

```kotlin
class ExposedBookRepositoryTest :
    ShouldSpec({
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        beforeSpec {
            postgres.start()

            // The same migrations production runs — never SchemaUtils.create.
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .load()
                .migrate()

            Database.connect(postgres.jdbcUrl, user = postgres.username, password = postgres.password)
        }

        afterSpec { postgres.stop() }

        context("findAll") {
            should("return the requested page in the requested order") { … }
        }
    })
```

**`beforeSpec`, not `beforeTest`** — one container per spec; per test turns a fast suite slow. Declaring the container without starting it is the same
mistake's other half: nothing starts it for you, and the failure is a connection refused that reads like a config problem.

**Build the schema with the real migrations.** A query tested against a table the migrations never produced proves nothing about production, and the
two drift silently — load the `ktor-toolkit:migrations` skill.

**Let each test own its data.** Insert, assert, then roll back or truncate. Shared fixtures are the usual cause of a suite that passes alone and fails
in parallel.

Testcontainers goes in the version catalog, not inline, with the dependency on `-adapters` and `acceptance-tests` — load the `ktor-toolkit:gradle`
skill. Say so before adding it: it needs a Docker daemon on every machine that runs the suite, CI included, and a team without one gets a red build
they did not ask for.

## Determinism

A flaky test is worse than no test: it trains people to re-run the build.

- **Control the clock.** Never let code under test call `Clock.System.now()` — take a `Clock` parameter and stub it:
  `every { clock.now() } returns Instant.parse("2024-01-01T00:00:00Z")`, or `returnsMany` when time must move. The toolkit's temporal rules take an
  explicit `now` for exactly this reason. A hand-written advancing clock is clearer when a test steps through many instants.
- **Never sleep.** A `delay` is a slow test that is still flaky on a loaded CI machine.
- **Do not assume ordering the code does not guarantee.** `shouldContainExactlyInAnyOrder` says what you mean; `shouldContainExactly` on a `Map`'s
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

`result shouldBe expected` on a data class reports the differing field. Asserting field by field gives that up and stops at the first mismatch.

## Before a bug is fixed

**Write the failing test first and watch it fail for the stated reason.** A test that passes before the fix is testing something else, and you find
out when the bug returns. Put it at the level the bug lived at: an off-by-one in page arithmetic is a unit test, a route that returned 500 is an
acceptance test.

## Mistakes that make a green suite worthless

| Mistake                                          | What it costs                                                           |
|--------------------------------------------------|-------------------------------------------------------------------------|
| Only unit tests                                  | Nothing proves the endpoint is wired at all                             |
| Deserializing the response with your own DTO     | Cannot see a renamed field or a naming-strategy change                  |
| Mocking the database in a repository test        | The database is the thing under test                                    |
| `coVerify` on every interaction                  | Asserts the implementation, so every refactor is a test rewrite         |
| Shared mutable state between tests               | Passes alone, fails in parallel, gets blamed on Kotest                  |
| `Thread.sleep` / `delay` to wait for expiry      | Slow, and flaky on a loaded CI machine — inject a `Clock`               |
| Fixtures with every field spelled out            | Hides the point of the test; every new field breaks every test          |
| `should("test findAll")`                         | Names the method, says nothing about the behaviour                      |
| Booting the whole `module()` for an adapter test | A failure could be anywhere in the graph; the test names nothing        |
| `every` where `coEvery` is needed                | The stub never matches, and the mock throws on a call it never heard of |
