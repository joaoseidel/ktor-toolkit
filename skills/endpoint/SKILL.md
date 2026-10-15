---
name: endpoint
description: >-
    Implements a new HTTP endpoint end to end across -core, -adapters and -app. Use for ANY
    route-shaped request: "add an endpoint", "expose X over HTTP", "GET /orders should return…",
    "let clients create a Y", or changing a route's contract. Asks the paging, links, validation,
    caching, errors and auth questions in order and loads the skill that owns each.
---

# Implementing an endpoint

## An endpoint is assembled, not invented

Nearly everything a route does is already decided by a toolkit module: parsing `?page`, rejecting a blank title, shaping a 404, adding a `next` link.
What is left to write is the part that is genuinely about *this* resource: the entity, the port, the use case, the mapping.

So the work is not "write an endpoint". It is: settle the contract, find out which decided problems this endpoint has, load those skills, and then
write the small remainder. An endpoint that took three hours usually spent them re-deciding something.

Load the `ktor-toolkit:architecture` skill first if you have not this session. Every file below lands in a specific module, and getting that wrong is
the expensive kind of mistake.

## Step 1: Settle the contract

Ask only what changes the code. Most of it is inferable from the request; ask about what is not.

- **Resource and operation.** The path shape and the verb: `GET /books`, `POST /books`, `GET /books/{id}`. Collections are plural nouns; verbs live
  in the method, not the path. `POST /books/search` is a smell worth one question before accepting it.
- **One or many.** A collection response drags pagination and links with it; a single resource does not. This is the single biggest fork in the work.
- **What the client sends**, if anything, and which parts are optional.
- **What failure looks like.** "Book not found" is a 404 with a domain exception behind it; "ISBN already taken" is a 409. Knowing these now means
  the exceptions get written with the entity rather than bolted on later.
- **Who may call it.** Public, authenticated, or restricted to a role.

If the user cannot answer one of these, propose the conventional default and say you are assuming it. Blocking a whole endpoint on a detail you can
safely default is worse than being explicit about the assumption.

## Step 2: Interrogate the features

Go down this list for the endpoint at hand. Each **yes** means loading that skill *before* writing code, because each one changes types that other
layers depend on. Discovering pagination after the port is written means rewriting the port.

| Ask                                                       | If yes, load this skill                      |
|-----------------------------------------------------------|----------------------------------------------|
| Does it return more than one of something?                | `ktor-toolkit:pagination`                    |
| Can the client choose the order?                          | `ktor-toolkit:pagination`, which owns `Sort` |
| Can the client narrow it, `?filterBy=`?                   | `ktor-toolkit:filtering`                     |
| Should the payload carry `next`/`prev`/`self` links?      | `ktor-toolkit:hateoas`                       |
| May the client pull in related resources with `?expand=`? | `ktor-toolkit:expand`                        |
| Does it accept a body, or query parameters with rules?    | `ktor-toolkit:validation`                    |
| Is the result expensive and reusable across clients?      | `ktor-toolkit:cache`                         |
| Can it fail in a way the client should understand?        | `ktor-toolkit:problem-details`               |

That last row is not really a question. Every endpoint can fail, so the `ktor-toolkit:problem-details` skill is in scope for every endpoint. The only
variable is whether this one adds a new exception mapping.

A collection endpoint typically answers yes to the first three. That is normal: pagination, sorting and links are one feature wearing three hats, and
the skills expect to be used together.

Six more concerns have **no toolkit module**. They are still your job, so decide them here rather than discovering them in review. The house
positions are in the section below.

Authentication · authorization · idempotency · multipart uploads · streaming · rate limiting

## Step 3: Build inside-out

Write in this order. It is not ceremony: each step compiles against the previous one, so the compiler tells you when a signature is wrong, and you
never write a DTO for a use case that turned out not to need it.

**1. `-core`: the domain.** Entity, value objects, domain exceptions, the port, the use case. Pagination enters here as `Pagination` if the endpoint
is a collection, and filters as `List<Filter>`. Nothing in this step mentions HTTP. Every route gets a use case, including a read that only
delegates to the port: the route never calls a repository, so the first business rule the endpoint grows has a place to go.

**2. `-adapters/persistence`: satisfy the port.** The Exposed table and the repository implementation, each method inside `suspendTransaction`.
Sorting and filtering arrive here as allow-lists of columns. A new table or column is also a migration, written in the same step. Load the
`ktor-toolkit:migrations` skill.

**3. `-adapters/web`: the route and its DTOs.** Request DTO, response DTO, mappers, validation rules, problem mappings, links.

**4. `-app`: wiring.** Register the new use case and adapter with `dependencies { }`, add the route to `configureRouting()`, and the validation rules
and problem mappings to their installs. An endpoint that compiles but was never wired fails only when called.

Steps 1 and 2 are testable before a server exists, which is the point. If you find yourself starting at step 3 because it feels faster, you are about
to design the domain around a JSON shape.

## The two shapes

Almost every endpoint is one of these. Start from the closer one.

### A collection

```kotlin
// -adapters/web/book/BookRoutes.kt
fun Route.bookRoutes() {
    val findBooks: FindBooks by dependencies
    val findBook: FindBook by dependencies
    val createBook: CreateBook by dependencies

    route("/books") {
        get {
            val paged = findBooks(call.filters, call.pagination)
            val response = PagedResponse.from(paged) { it.toResponse() }
            call.respond(response.toResource(call))
        }

        post {
            val book = createBook(call.receive<CreateBookRequest>().toDomain())
            call.response.header(HttpHeaders.Location, "/books/${book.id.value}")
            call.respond(HttpStatusCode.Created, book.toResponse())
        }

        get("/{id}") {
            val book = findBook(BookId(call.parameters.getOrFail("id")))
            call.respond(book.toResource())
        }
    }
}
```

Five things in there are deliberate and worth not undoing:

**Every handler calls a use case.** Not a repository, not two use cases stitched together in the route: read the call, invoke one use case, map,
respond. Orchestration that needs a second call belongs in the use case.

**Dependencies are resolved once, at the top of the route function**, not inside a handler, where the work repeats per request and a reader will not
look for it. `-app` only names the route:

```kotlin
// -app/plugin/Routing.kt
fun Application.configureRouting() {
    routing {
        bookRoutes()
    }
}
```

A test overrides the registration rather than passing arguments; load the `ktor-toolkit:di` skill for how.

**`call.pagination` never fails.** An unparseable `?page=abc` falls back to the default rather than 400-ing, so there is no error path to write. Use
`call.paginationRequest(defaultPageSize, maxPageSize)` when this endpoint needs different bounds from the toolkit's `1..100`. `call.filters`, by
contrast, rejects a malformed condition with a 400, because a dropped filter would answer with rows the client asked to exclude.

**No try/catch.** `findBook` throws `BookNotFoundException` and `problemDetails { }` turns it into a 404 once, for every endpoint. A `catch` in a
route body is a mapping that will disagree with the global one within a month.

**A `201` names what it created.** The `Location` header is how a client finds the new resource without parsing the body for an id.

**No validation code.** The rules live in `install(RequestValidation)`, so `call.receive()` either returns a valid object or never returns. That is
what lets `toDomain()` be a straight mapping instead of a second validation pass.

### A single resource

Same shape minus the paging: `call.receive()`, one use case call, `respond`. Reach for `resource { }` only if the client genuinely navigates by
links. An unused `_links` block is payload nobody reads. `book.toResource()` above lives in `BookLinks.kt`, beside the response DTO, and the
`ktor-toolkit:hateoas` skill covers it.

## Features without a toolkit module

No module owns these. Follow the defaults below unless the endpoint has a stated reason not to, and flag repetition: the third endpoint to hand-roll
the same thing wants a shared helper, not a fourth copy.

**Authentication.** Ktor's `Authentication` plugin, installed in `-app`, wrapping routes in `authenticate { }`. Identity reaches the use case as an
explicit parameter, a `CallerId` value object rather than the `Principal`, and never by the use case reading a thread-local or a call.

**Authorization.** Coarse checks (is this caller an admin?) belong in the route. Rules that depend on the data (may this caller edit *this* book?)
belong in `-core`, because they are business rules and need testing without a server.

**Idempotency.** Require an `Idempotency-Key` header on non-idempotent writes and record the key in the database, in the same transaction as the
write, under a unique constraint. A replay finds the key and returns the stored outcome; a concurrent duplicate fails the constraint instead of
writing twice. Do not keep keys in the response cache: it is best-effort by design and forgets them exactly when Redis has a bad minute.

**Multipart uploads.** `call.receiveMultipart()` in the route, streaming straight to the storage adapter. Do not buffer a whole upload into memory to
hand a `ByteArray` to a use case; the port should take a stream.

**Streaming.** `call.respondBytesWriter { }` or `respondOutputStream`. Note that this is incompatible with the caching in the `ktor-toolkit:cache`
skill, which serializes the whole value. Do not wrap a streaming route in `withCache`.

**Rate limiting.** Ktor's `RateLimit` plugin, configured in `-app`. Keep the limits in `application.yaml` so they are environment-specific. Its
counters live in memory per instance, so behind a load balancer the effective limit is the configured one times the instance count.

## Before you call it done

- The use case has a test that does not start a server (load the `ktor-toolkit:tests` skill).
- The endpoint has an acceptance test in `acceptance-tests` covering the success path and the interesting failure.
- New exceptions have a `problemDetails` mapping, or deliberately fall through to the catch-all. A mapped one is no longer logged by the catch-all,
  so anything worth a log line has one where it is thrown (load the `ktor-toolkit:logging` skill).
- Sortable columns are an explicit allow-list, not whatever the client sent.
- The route is documented (load the `ktor-toolkit:openapi` skill) and the DI registration exists. An endpoint that compiles but was never wired into
  `configureRouting()` is the quietest possible bug.
- The commit is scoped (load the `ktor-toolkit:commit` skill).

## Common mistakes

| Mistake                                                      | Why it hurts                                                                    |
|--------------------------------------------------------------|---------------------------------------------------------------------------------|
| Writing the route first, then the domain                     | The domain ends up shaped like the JSON, and the port leaks HTTP concepts       |
| A repository resolved and called from the route              | Skips the use case, so the next business rule lands in the route body           |
| Hand-parsing `?page` / `?sort`                               | Re-decides clamping and fallbacks the toolkit already settled, differently      |
| `try/catch` in the route                                     | Competes with the global problem mapping and drifts from it                     |
| `if (request.title.isNullOrBlank())` at the top of a handler | Validation belongs in `RequestValidation`, where it is declarative and reusable |
| Returning the entity directly                                | Storage shape becomes wire shape; the next rename is a breaking change          |
| Resolving dependencies inside a handler                      | Repeats per request, and hides what the route depends on                        |
| Sorting by whatever string the client sent                   | Straight to the database as a column name                                       |
| One route function per endpoint file                         | Related endpoints drift apart; group them under one `route("/books")`           |
