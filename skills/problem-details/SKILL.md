---
name: problem-details
description: >-
  RFC 9457 application/problem+json errors with ktor-toolkit-problem-details — problemDetails { }
  inside StatusPages, HttpStatusException for a deliberate status, and mapping your own exceptions
  with on<E>. Use whenever an endpoint can fail, when a domain exception needs an HTTP status, when
  errors come back as HTML or an ad-hoc JSON shape, and whenever you see
  call.respond(HttpStatusCode.BadRequest, mapOf("error" to …)) or a try/catch in a route.
---

# Problem Details

## One installer, four kinds of failure

`problemDetails { }` registers handlers for everything that can go wrong, so no route has to:

```kotlin
// -app/plugin/StatusPages.kt
install(StatusPages) {
    problemDetails {
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
}
```

That single block covers:

| What failed                                               | Becomes                                             |
|-----------------------------------------------------------|-----------------------------------------------------|
| `HttpStatusException` you threw                           | that status, with your detail and properties        |
| `RequestValidationException` from a rule                  | 400, one entry per failed rule, keyed by field path |
| `BadRequestException` — a body that would not deserialize | 400; missing fields are named individually          |
| Anything else                                             | 500, stack trace logged, message hidden             |

Install it before the first endpoint exists. Its value is in the cases nobody wrote code for, and a service that adds it later has already shipped a
different error contract to its clients.

Without it, a validation failure answers with Ktor's default HTML error page — which is not what any JSON client is prepared to parse.

## The response shape

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

`type` defaults to `about:blank`. Set it to a URL documenting the error class when you have one — that is the field RFC 9457 intends clients to switch
on, and it is more stable than parsing `detail`.

`title` comes from the status code's own description, so it is consistent across the API for free.
`detail` is the human-readable, case-specific part. `properties` carries machine-readable extras.

**`instance` fills itself in with the request path** unless your mapping sets one explicitly. You almost never need to set it.

The response is served as `application/problem+json`, with nulls omitted — an error with no `detail`
simply has no `detail` key rather than `"detail": null`.

## Throwing a deliberate status

For a failure that is genuinely about HTTP — a path parameter that names nothing, a precondition on the request itself — throw it directly:

```kotlin
throw HttpStatusException(
    HttpStatusCode.NotFound,
    "Book not found",
    mapOf("id" to id),
)
```

`properties` is a `Map<String, String>`. Put the identifiers a client needs to act on there rather than interpolating them into `detail` and forcing a
regex on the other side.

**This belongs in `-adapters/web`, never in `-core`.** An HTTP status is a transport decision. A domain that throws `HttpStatusException` cannot be
tested without deciding what a 404 means, and it has become a web layer — load the `ktor-toolkit:architecture` skill.

## Mapping your own exceptions

Domain code throws domain exceptions. The translation to a status happens once, here:

```kotlin
// -adapters/web/book/BookProblems.kt
fun ProblemDetailsConfig.bookProblems() {
    on<BookNotFoundException> {
        ProblemDetail.fromStatus(HttpStatusCode.NotFound, "No book ${it.id}")
    }
    on<IsbnAlreadyTakenException> {
        ProblemDetail.fromStatus(
            HttpStatusCode.Conflict,
            "That ISBN is already registered",
            properties = mapOf("isbn" to it.isbn),
        )
    }
}
```

```kotlin
install(StatusPages) {
    problemDetails {
        namingStrategy = JsonNamingStrategy.SnakeCase
        bookProblems()
    }
}
```

Keep the mappings next to the resource they concern, in `-adapters/web`, and call them from the install. One `problemDetails` block listing every
exception in the service becomes unreadable at about the fifteenth entry.

The lambda receives the exception and runs with the `ApplicationCall` as receiver, so a mapping can consult the request when it needs to.

**Mapping an exception also stops it being logged.** Only the catch-all writes a stack trace; a mapped exception is answered and forgotten, which is
right for a 404 and wrong for a 409 you are trying to measure. If a mapped case deserves a log line, log it where you throw it rather than inside the
mapping — load the `ktor-toolkit:logging` skill for the level to pick and what must never go in the message.

**Declaration order does not matter.** StatusPages resolves by nearest ancestor class, so a mapping for `BookNotFoundException` always beats the
`Throwable` catch-all, wherever it appears. You do not have to out-run the default, and a mapping on a base exception type covers every subtype that
has no closer match — which is the clean way to give a whole family one status.

## What happens to everything else

An unmapped exception is a bug, and the toolkit treats it as one:

- The full stack trace is logged at ERROR through the application log.
- The client gets a 500 with a fixed `"An unexpected error occurred."`

**The message is hidden on purpose.** Exception text routinely names the database, a table, a driver, or an internal host. That belongs in your logs,
not in a response.

While developing, turn it on:

```kotlin
problemDetails {
    includeExceptionMessage = true
}
```

Drive it from configuration rather than a literal, so it cannot be committed in the on position:

```kotlin
problemDetails {
    includeExceptionMessage = environment.config
        .propertyOrNull("app.exposeErrors")?.getString().toBoolean()
}
```

If a 500 shows up in production logs regularly, the answer is a mapping or a fix, not
`includeExceptionMessage`.

## snake_case

`namingStrategy` controls how **field paths inside validation and missing-field errors** are rendered — it does not rename the problem's own keys,
which RFC 9457 fixes as `type`, `title`,
`status`, `detail`, `instance`.

```kotlin
namingStrategy = JsonNamingStrategy.SnakeCase
```

With it, a violation on `authorEmail` reports against `author_email`. Set it to match whatever your
`ContentNegotiation` json uses. Leaving them inconsistent means a client is told a field is invalid under a name that does not appear in the request
it sent — a genuinely confusing bug to chase.

`JsonNamingStrategy` is `@ExperimentalSerializationApi`, so this needs
`-opt-in=kotlinx.serialization.ExperimentalSerializationApi` in the module's compiler options.

## Overriding a default

Handlers registered with `exception<T>` **after** `problemDetails()` take precedence, so you can replace any of the four defaults without forking the
block:

```kotlin
install(StatusPages) {
    problemDetails { }

    exception<RequestValidationException> { call, cause ->
        // your own envelope
    }
}
```

Reach for this rarely. The point of the module is that every error in every service looks the same; an override is a local dialect, and it needs a
reason better than taste.

## Two exceptions worth mapping early

**`IllegalArgumentException` → 400.** This is not mapped by default, so it lands on the catch-all as a 500. It is what `Sort.toExposedQueryExpression`
throws for a sort key outside the allow-list, which makes `?sortBy=titel` — a client typo — answer 500. Load the `ktor-toolkit:pagination` skill,
which covers this.

Map it deliberately rather than reflexively: `IllegalArgumentException` is also what a `require` in your own value objects throws, and those genuinely
are server faults when they fire after validation. If both cases exist, give the sort failure its own exception type instead of widening the mapping.
Blanket-mapping it turns real bugs into 400s that nobody investigates.

**A type mismatch is still a plain 400.** `{"quantity": "abc"}` fails inside the deserializer with no field path to recover, so the client gets
`detail` from the parser and nothing more. Nullable request properties do not help here — only a `String` field validated with `pattern` would. This
is a real limit worth knowing before promising per-field errors for every bad request.

## Common mistakes

| Mistake                                                                | Why it hurts                                                               |
|------------------------------------------------------------------------|----------------------------------------------------------------------------|
| `call.respond(HttpStatusCode.BadRequest, mapOf("error" to …))`         | A second error shape clients must handle separately                        |
| `try/catch` in a route body                                            | Competes with the global mapping and drifts from it within a month         |
| `HttpStatusException` thrown from `-core`                              | Business rules now depend on HTTP; untestable without picking status codes |
| `includeExceptionMessage = true` reaching production                   | Leaks schema, driver and host details to clients                           |
| Every mapping in one `problemDetails` block                            | Unreadable, and unrelated resources become one merge conflict              |
| Ordering mappings to beat the catch-all                                | Unnecessary — nearest ancestor already wins                                |
| `namingStrategy` different from the ContentNegotiation json            | Errors name fields the client never sent                                   |
| Identifiers interpolated into `detail` only                            | Clients parse prose; use `properties`                                      |
| Catching an exception to add context, then rethrowing bare `Exception` | Destroys the type the mapping resolves on                                  |
