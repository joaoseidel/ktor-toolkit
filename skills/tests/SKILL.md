---
name: tests
description: >-
    Testing a Ktor Toolkit service — Kotest ShouldSpec with behaviour-named cases, MockK for
    collaborators, testApplication for routes, Testcontainers for real persistence, and acceptance
    tests that drive the built image over HTTP. Use when writing or reviewing any test, naming a
    context or a should, deciding what to mock and what to run for real, choosing what a response
    assertion should compare against, or when a test is flaky, hangs, or passes for the wrong
    reason.
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
it, so a failure names the adapter. An acceptance test drives the assembled service, so a failure means the feature is broken for a client.
`acceptance-tests` is its own module precisely so it cannot reach internals: no project dependency on `-core` or `-app`, so there is nothing to assert
on but the HTTP surface.

**Where there is no `acceptance-tests` module**, offer one the first time a task needs a whole-app test, and wait. It is more than a settings entry and
a build script: it needs a task that builds the service's image, and the specs need to disable themselves where no Docker daemon can run it. Say so
when offering, because it puts a daemon on the critical path of anyone who runs that module.

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

Everything assembled, over HTTP, as a client sees it. **Run the built image, not `testApplication`.**

`testApplication { application { module() } }` is the right shape only while `module()` can boot on the classpath alone. The moment it verifies a
database on startup, or launches background workers, that stops being true: the module fails before the first request, and the test that was supposed
to prove the feature works proves nothing at all. A service that migrates its schema on boot cannot be acceptance-tested any other way, because the
migration is part of what is being tested.

So the subject is the artifact that ships:

```kotlin
object AcceptanceStack {
    private val network = Network.newNetwork()

    private val database = GenericContainer(DockerImageName.parse("postgres:17-alpine"))
        .withNetwork(network).withNetworkAliases("db")

    private val service = GenericContainer(DockerImageName.parse(System.getenv("ACCEPTANCE_IMAGE")))
        .withNetwork(network)
        .withEnv("DATABASE_URL", "postgres://db:5432/app")
        .withExposedPorts(8080)
        // Not forListeningPort: the entrypoint migrates before it serves, so an open port
        // is not yet a service that can answer.
        .waitingFor(Wait.forHttp("/health").forPort(8080))

    val baseUrl: String by lazy {
        database.start()
        service.start()
        "http://${service.host}:${service.getMappedPort(8080)}"
    }
}
```

**The image is built by the build, from the working tree.** A task that runs `docker build` and that the test task depends on, never a tag someone
built by hand: otherwise the suite silently tests whatever was last built, which is the one thing an acceptance test must not do.

**Start the stack once for the whole run.** A boot costs a migration and every background worker the service has; one stack per spec spends the run in
startup and leaves several copies of every sweep racing over the same rows.

**Gate the module, do not fail without Docker.** A red build on every machine that cannot run it teaches people to ignore red. Disable the specs when
there is no daemon or no image, and keep the module out of `check` so a plain build stays fast.

### What to assert against

Three shapes, and they are not interchangeable.

**A JSON Schema, for the response contract.** Not a handful of field reads: `body["accessToken"]` says nothing about a token that arrived empty, about
a field renamed beside it, or about one the service started publishing that nobody meant to. A schema states the whole contract in one document a
client author can read without opening the test, and `additionalProperties: false` turns an unannounced field into a failure.

```kotlin
should("answer a created session a client can use") {
    val response = client.post("/api/v1/auth/sign-up") { jsonBody { put("email", "owner@example.com") } }

    response.status shouldBe HttpStatusCode.Created
    JsonSchema.assertMatches("auth/auth-response.json", response.bodyAsText())
}
```

This is the assertion that catches a naming strategy. A DTO declaring `accessToken` is published as `access_token` by an application-wide
`JsonNamingStrategy.SnakeCase`, and *every* test that deserializes with that DTO passes while every client breaks.

**A golden file, for a document read field by field.** A validation problem+json is highlighted input by input in the panel, so the contract is the
whole set of field paths, not a sample of it. Compare the entire body against a file, with volatile fields (ids, timestamps) replaced by a token naming
the shape they must still have. Keep the first write loud — it exists to make a human read the diff, not to make red go away.

**A status code, always explicitly.** `201` and `200` are different answers and clients branch on them.

### Bodies and external dependencies

**Build the request body, never hand-quote it, and never serialize the service's own DTO.** `buildJsonObject { put("email", "…") }` cannot disagree
with itself the way the DTO can, and cannot be broken by an escaping typo the way a string literal can. The one exception is a deliberately malformed
document — no builder produces `{ not json`, and mangled input is exactly what that case tests.

**Fake external services with a real HTTP mock, not an unreachable address.** Pointing a licence server at `127.0.0.1:1` exercises exactly one branch.
A WireMock container on the same network lets a spec put the install into a state and watch the behaviour, and lets it ask afterwards what the service
actually sent — an outbound contract nobody asserts is one that drifts. **Keep the stubs in files**, in WireMock's own format: a mapping embedded in a
Kotlin string loses its highlighting, its schema, and the ability to be read beside the response it fakes.

This layer proves the cross-cutting wiring — that errors are `problem+json`, that validation reached the client with a usable field path, that the
route exists at the documented path, that the image boots and migrates at all. A unit test sees none of it, and an `-adapters` test sees only the half
it assembled.

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

**When Testcontainers reports no Docker environment, read the strategy log before believing it.** The bundled docker-java negotiates an old API
version, and a current daemon refuses it outright — `client version 1.32 is too old. Minimum supported API version is 1.40`. The daemon is fine and the
socket is fine; the client is the problem. Fix it with `systemProperty("api.version", "1.44")` on the `Test` task, because docker-java reads that from
the JVM's system properties: a `testcontainers.properties` configures Testcontainers, not the client underneath it. Put it on the module that needs it
so a fresh checkout works without anyone being told to edit a file in their home directory.

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
- **Bound every test, so a stuck one fails instead of hanging.** `systemProperty("kotest.framework.timeout", "30000")` on the `Test` tasks. Without it
  a flow test waiting on an event the code never emits stops the build rather than failing it, and CI reports a timeout with no test name in it.
- **Never wait on an event that may not come.** Where a test collects a flow, emit the case *and then* an event the code always translates, and assert
  on both. Waiting only for the interesting one turns "the behaviour is missing" into "the suite hangs".

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

## The case almost nobody writes: a cancelled caller

`runCatching` catches `Throwable`, and so does `catch (e: Exception)`. Both therefore catch `CancellationException`, which means a caller that walked
away arrives as an ordinary failure and is logged, defaulted or retried like one. The work then carries on inside a coroutine that is already
cancelled, and whatever it writes describes something nobody asked for.

It is worth a case on **every suspending block wrapped in a broad catch**, because the symptom is never an exception — it is a row that is wrong
afterwards:

```kotlin
should("let a cancellation through, rather than deleting the row it still needs") {
    coEvery { daemon.removeSidecar(any(), any(), any()) } throws CancellationException("request abandoned")

    shouldThrow<CancellationException> { disableTunnel("instance:abc", "token") }

    coVerify(exactly = 0) { tunnels.delete(any(), any()) }
}
```

The assertion is two-part on purpose: that the cancellation escapes, **and** that the destructive step after it did not run. The first alone passes on
code that swallowed it and then failed for another reason.

**A timeout is not one of these.** `TimeoutCancellationException` is a `CancellationException`, and it usually *should* become a failure: it means the
dependency did not answer, not that the caller left. So this is a boundary rule, not a blanket one — convert a timeout into a domain failure where you
raise it, and let everything above treat what remains as the caller's own cancellation:

```kotlin
} catch (e: TimeoutCancellationException) {
    throw DependencyUnreachableException("$address timed out after $timeout")
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    …
}
```

Check that boundary exists before writing the tests above. If a timeout can still reach the code under test as a cancellation, rethrowing it there
turns a dependency outage into a silent no-op, which is worse than what you started with.

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
| `runCatching` around a suspending call, untested | A cancelled caller is logged as a failure, and the write after it still lands |
| Asserting picked fields instead of a schema      | A renamed field, a naming strategy, an empty value: all invisible       |
| Acceptance tests that boot `module()` in-process | Proves nothing about the image, the entrypoint, or the boot migration   |
