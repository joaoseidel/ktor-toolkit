---
name: logging
description: >-
  Logging with KotlinLogging — the file-level `private val logger = KotlinLogging.logger {}` idiom,
  the lazy lambda message form, correlation IDs through CallId and CallLogging with an MDC, JSON
  output via the logstash encoder, and what must never reach a log line. Use when adding or
  reviewing any log statement, when choosing a level, when an exception needs recording, when a
  request needs to be traceable across services, and whenever a log line is about to contain a
  token, a password, an email address or a whole request body.
---

# Logging

## One idiom

A private logger at the top of the file, outside the class:

```kotlin
private val logger = KotlinLogging.logger {}
```

The empty lambda gives the logger the enclosing file's name, so nothing has to be kept in step with a rename. File-level and private means one per
file, shared by everything in it, and never exposed as API.

Do not declare it in a companion object, do not pass a logger as a constructor parameter, and do not inject one. There is exactly one way to get a
logger here.

## Always the lambda form

```kotlin
logger.info { "Reindexed $count books in ${elapsed.inWholeMilliseconds}ms" }
logger.warn(exception) { "License server unreachable; keeping cached entitlement" }
```

The message is a lambda, so the string is never built when the level is disabled. `logger.info("…")`
with string concatenation pays for the message whether or not anyone will read it — on a hot path with DEBUG off, that is pure waste, and it is the
single easiest logging mistake to make in Kotlin.

The exception goes in the **first parameter**, not interpolated into the message:
`logger.error(e) { "…" }`. That is what gets the stack trace rendered as a structured field rather than as text inside a message.

## Levels

Pick by who acts on it, not by how interesting it felt while writing.

| Level   | Means                                                        | Example                                                   |
|---------|--------------------------------------------------------------|-----------------------------------------------------------|
| `error` | Something failed and nobody handled it. Someone should look. | An unmapped exception; a write that lost data             |
| `warn`  | Degraded but handled. Fine once; a pattern is a problem.     | Cache unreachable, serving from origin; a retry succeeded |
| `info`  | A notable state change in the system's life.                 | Startup, shutdown, migration applied, license revalidated |
| `debug` | Detail useful while diagnosing, off in production.           | The query built, the branch taken                         |
| `trace` | Firehose. Rarely justified.                                  | Per-item work inside a loop                               |

```kotlin
logger.error(e) { "Failed to publish book $bookId to the search index; it will be missing from search" }
logger.warn(e) { "Search index unreachable after 3 attempts; book $bookId queued for retry" }
logger.info { "Started on port $port, profile=$profile" }
logger.debug { "Resolved sort to ${sort.joinToString()} against ${columns.size} sortable columns" }
logger.trace { "Row $index mapped in ${elapsed.inWholeMicroseconds}µs" }
```

Two rules that keep the levels meaningful:

**`info` is not "a request happened".** `CallLogging` already records every request. An `info` per request from your own code doubles the volume and
adds nothing.

**A handled failure is `warn`, not `error`.** If the code recovered — fell back to the origin, kept a cached value, retried successfully — the system
is working. Reserve `error` for what actually needs a human, or alerting stops meaning anything.

Production runs at `info`. Drive it from the environment so raising it does not need a deploy:

```xml
<root level="${LOG_LEVEL:-info}">
```

## Exceptions

```kotlin
logger.error(e) { "License server returned an unverifiable entitlement token; keeping cached one" }
```

Say what the system did about it. "Failed to fetch license" tells the reader what broke; "…; keeping cached one" tells them whether to get out of bed.

**Do not log and rethrow.** The exception gets logged again wherever it is finally handled, and the same failure appears two or three times under
different messages — which reads like three failures. Either handle it and log, or let it propagate and let one place log it.

The one place that already logs is the toolkit's catch-all: an unmapped exception is logged with its full stack trace by `problemDetails { }`, and the
client is told nothing. That means **an exception mapped with `on<E>` is deliberately not logged** — if a mapped case also deserves a log line, log it
where you throw it. Load the `ktor-toolkit:problem-details` skill.

## Writing the message

A log line is read months later by someone who does not have the code open, at speed, under pressure. Write for that reader.

**Say what happened, then what the system did about it.** The second half is what decides whether anyone needs to act.

```kotlin
// Weak — describes a symptom and stops
logger.warn { "Redis error" }

// Good — names the effect and the consequence
logger.warn(e) { "Cache unreachable; serving $bookId from the origin" }
```

**Name the thing, not the variable.** The reader does not know your locals.

```kotlin
logger.debug { "id=$id, n=$n, ok=$ok" }                       // meaningless in a search result
logger.debug { "Expanded $n authors for book $id" }           // reads as a sentence
```

**Include the values needed to act, and no more.** An id to look up, a count to judge scale, a duration to judge severity:

```kotlin
logger.info { "Reindexed $count books in ${elapsed.inWholeMilliseconds}ms" }
```

**Keep the leading words stable.** Logs are searched by prefix far more often than by regex, so
`"Reindexed 412 books"` groups with every other reindex line; `"412 books were reindexed"` does not.

**No punctuation theatre.** No `!!!`, no `====== STARTING ======`, no emoji. A JSON log has a level field; shouting adds nothing and breaks grep.

**Never log a bare "here" or a stray marker.** `logger.info { "here 2" }` is debugging residue — delete it before the commit rather than shipping it
at `debug`.

## Correlation IDs

One request must be followable across every line it produced, and across services:

```kotlin
// -app/plugin/Logging.kt
fun Application.installRequestTracing() {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        replyToHeader(HttpHeaders.XRequestId)
        generate()
    }

    install(CallLogging) {
        callIdMdc("call-id")
        disableDefaultColors()
        disableForStaticContent()
    }
}
```

`retrieveFromHeader` adopts an id a caller already has, so a trace spans services rather than restarting at your edge. `generate()` creates one when
there is none. `replyToHeader` returns it, so a client reporting a problem can quote the exact request.

`callIdMdc("call-id")` puts it in the MDC, which is what makes it appear on **every** line logged during that request without any of them mentioning
it.

Add your own scoped context the same way, rather than repeating an id in message strings:

```kotlin
withLoggingContext("bookId" to book.id) {
    logger.info { "Reindexed" }
    …
}
```

Everything logged inside the block carries `bookId`, including code further down the stack that has never heard of it. `disableForStaticContent()`
keeps asset requests out of the log entirely.

## Structured output

Logs are read by a machine first. Configure Logback to emit JSON rather than parse-it-later text:

```xml
<appender name="console" class="ConsoleAppender">
    <encoder class="LoggingEventCompositeJsonEncoder">
        <providers>
            <timestamp/>
            <logLevel/>
            <mdc/>
            <loggerName/>
            <message/>
            <throwableClassName/>
            <stackTrace>
                <throwableConverter class="ShortenedThrowableConverter">
                    <maxDepthPerThrowable>200</maxDepthPerThrowable>
                    <maxLength>5000</maxLength>
                    <rootCauseFirst>true</rootCauseFirst>
                </throwableConverter>
            </stackTrace>
        </providers>
    </encoder>
</appender>
```

`<mdc/>` is what publishes `call-id` and anything from `withLoggingContext` as queryable fields — without that provider the context is collected and
then thrown away. `rootCauseFirst` puts the actual cause at the top, where the useful line in a twelve-frame wrapped exception usually is.

This needs `net.logstash.logback:logstash-logback-encoder`, which is **not** in the version catalog yet — load the `ktor-toolkit:gradle` skill.

A line then arrives as an object you can query, with the MDC flattened alongside the message:

```json
{
  "@timestamp": "2026-08-06T17:41:02.318Z",
  "level": "WARN",
  "call-id": "6f1c9a2e-3b77-4a0e-9f21-0c5a1d8e44b3",
  "bookId": "book-7421",
  "logger_name": "com.example.catalog.adapters.search.SearchIndexAdapter",
  "message": "Search index unreachable after 3 attempts; book book-7421 queued for retry",
  "throwableClassName": "java.net.SocketTimeoutException"
}
```

`call-id` and `bookId` are queryable because `<mdc/>` published them — the code that logged this never mentioned either. That is the payoff for
`callIdMdc` and `withLoggingContext`, and it is why
`"…all lines for request 6f1c9a2e…"` is a search rather than an investigation.

Console-friendly text is fine locally; keep JSON for anything deployed. A field only becomes searchable if it is a field.

## Never log these

The rule that covers most of it: **log the identifier, not the object.**

| Never                                                   | Instead                                     |
|---------------------------------------------------------|---------------------------------------------|
| Passwords, even wrong ones                              | Nothing. There is no safe version.          |
| Tokens, JWTs, API keys, secrets                         | The subject or key id                       |
| `Authorization` headers, session cookies                | The `call-id`                               |
| Card numbers, CVV, bank details                         | An order or payment id                      |
| Email addresses, phone numbers, national ids, addresses | The user id                                 |
| Full request or response bodies                         | The endpoint, the status, a field count     |
| An entire entity                                        | Its id, and the one field the line is about |

That last row is where the damage usually comes from. `logger.debug { "Saving $user" }` looks harmless and prints an email, a hashed password, and
whatever gets added to the class next year. Nobody re-reviews that line when the field is added.

Two consequences worth internalising:

**Logs outlive the request and travel further than the response.** They are shipped, indexed, and retained by systems with different access rules from
the database — often for years. A secret in a log is a secret in a search index.

**`includeExceptionMessage` is about the client, not the log.** Exception text is always logged in full; that flag only controls whether it is also
echoed to the caller, and it stays off in production.

If a log line genuinely needs to identify a person for support, log the id and let whoever is debugging join it to the database, where access is
controlled.

## Worked examples

**Startup and shutdown — `-app`.** The two lines that answer "what is actually running?" Log the resolved configuration, never the secrets in it:

```kotlin
fun Application.module() {
    logger.info { "Starting catalog ${BuildConfig.VERSION} on port $port, profile=$profile" }

    monitor.subscribe(ApplicationStopped) {
        logger.info { "Stopped; draining complete" }
    }
}
```

**A retry in an adapter — `-adapters`.** Log the recovery, not each attempt. One line per failed call turns a transient blip into a page of noise:

```kotlin
suspend fun index(book: Book) =
    retry(times = 3) { attempt ->
        runCatching { client.put(book) }
            .onFailure { if (attempt == 3) logger.warn(it) { "Search index unreachable after 3 attempts; book ${book.id} queued for retry" } }
    }
```

**A decision in a use case — `-core`.** Log the branch a reader would otherwise have to reconstruct from data:

```kotlin
if (entitlement.isExpired(now)) {
    logger.warn { "Entitlement expired ${entitlement.expiresAt}; falling back to the free tier" }
    return Tier.Free
}
```

**Scoped context around a unit of work.** Everything inside carries `bookId`, including code far down the stack:

```kotlin
withLoggingContext("bookId" to book.id) {
    val indexed = searchIndex.index(book)
    logger.info { "Reindexed in ${indexed.elapsed.inWholeMilliseconds}ms" }
}
```

**A security-relevant event.** Worth recording, and the hardest place to keep secrets out. Log the subject and the outcome, never the credential or
the address:

```kotlin
logger.warn { "Sign-in rejected for user ${user.id}: password mismatch" }   // id, not email
logger.info { "Password reset requested for user ${user.id}" }             // no token, no link
```

**Timing something.** Measure, then log once with the duration — not a "starting" line and a
"finished" line, which double the volume and cannot be correlated when they interleave:

```kotlin
val elapsed = measureTime { repository.reindexAll() }
logger.info { "Reindexed $count books in ${elapsed.inWholeMilliseconds}ms" }
```

## Where logging belongs

Logging is allowed in `-core`. `kotlin-logging` is a facade over SLF4J — no framework, no I/O contract, nothing that compromises the module's
independence, so a use case may log a decision it made.

Keep it sparse there anyway. A use case logging every step is usually compensating for a test that does not exist. The natural homes are: startup and
shutdown in `-app`, integration failures and retries in `-adapters`, and genuinely notable decisions in `-core`.

## Performance

- **The lambda form is the whole optimisation.** Keep expensive work inside it — `logger.debug { "…
  ${expensiveSummary()}" }` never calls `expensiveSummary()` when DEBUG is off.
- **Do not log per item.** A line per row of a page turns one request into a hundred lines. Log the count.
- **Do not log inside a hot loop** even at `trace`; the level check is cheap but not free at that volume.
- **`disableForStaticContent()`**, so asset requests do not dominate the log.
- Logging is I/O. A synchronous appender on a slow sink makes the log part of your latency; use an async appender for anything shipping over a
  network.

## Common mistakes

| Mistake                                     | Why it hurts                                                           |
|---------------------------------------------|------------------------------------------------------------------------|
| `logger.info("Found " + count + " books")`  | Builds the string even when the level is off                           |
| `logger.error { "Failed: ${e.message}" }`   | Loses the stack trace; pass the exception as the first argument        |
| Log and rethrow                             | The same failure appears several times and reads like several failures |
| `error` for something the code handled      | Alerting stops meaning anything                                        |
| An `info` line per request                  | `CallLogging` already does it; you doubled the volume                  |
| Logging a whole entity                      | Prints today's PII and next year's new field                           |
| An id interpolated into every message       | Use `withLoggingContext`; it reaches code that never saw the id        |
| Plain-text logs in production               | Fields are not searchable unless they are fields                       |
| No `<mdc/>` provider                        | `call-id` is collected and then silently discarded                     |
| A logger in a companion object, or injected | Three idioms where one will do                                         |
| Paired "starting…" / "finished…" lines      | Double the volume, and uncorrelatable once requests interleave         |
| `logger.debug { "here 2" }` left in         | Debugging residue; delete it rather than shipping it                   |
| `"===== STARTED ====="` or emoji            | The level is already a field; decoration only breaks grep              |
| A message built from local variable names   | `id=7, ok=true` means nothing in a search result                       |
| Logging every retry attempt                 | A transient blip becomes a page of noise; log the recovery             |
