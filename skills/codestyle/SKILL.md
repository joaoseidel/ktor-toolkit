---
name: codestyle
description: >-
    How to write Kotlin in a Ktor Toolkit service — ktlint at 150 columns, file and package naming,
    expression bodies, explicit public return types, immutability, require in the primary
    constructor, sealed hierarchies, the @DslMarker builder pattern, no !!, and never swallowing
    CancellationException. Load before writing or reviewing any Kotlin file, when naming anything,
    and when a construct needs @Suppress.
---

# Kotlin style

## Let a formatter decide formatting

Formatting is not worth an opinion per file. The convention these skills assume is ktlint with `.editorconfig` as the source of truth:

```editorconfig
[*.{kt,kts}]
ktlint_code_style = ktlint_official
max_line_length = 150
indent_size = 4
indent_style = space
insert_final_newline = true
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
```

**Where the project already has a formatter, that one wins.** A house line length of 120, or a Spotless setup, is not something to migrate mid-task.
Adopt what is configured.

**Where the project has none**, say so and offer to add ktlint and an `.editorconfig` before the rules below can mean anything — then wait. Formatting
an existing codebase touches every file, so it is its own commit and its own decision. Never fold it into a feature branch.

Run the formatter before you read a diff, so review is about the code. **Never hand-format around it.** If you and the formatter disagree, the
formatter wins, and the fix for a construct that formats badly is to shorten the construct.

Everything below is what a formatter cannot check.

## Files and packages

**One primary declaration per file, named after it.** `Book.kt` holds `Book`; a `Book.Status` enum and a `book { }` builder live there too, because
they exist only to serve it. A file whose name does not predict its contents is the thing to avoid.

**Free-standing extensions collect into `<Subject>Extensions.kt`** — `BookResponseExtensions.kt`, `BookQueryExtensions.kt`. The name says what they
extend, so the import tells a reader where the receiver came from.

**A `@DslMarker` annotation gets its own file**, named for the DSL it guards: `CatalogDsl.kt`.

**Package by feature, not by kind.** `catalog.book` holding the entity, the port and the use cases beats `catalog.entity`, `catalog.port` and
`catalog.usecase` each holding one slice of every feature — that layout makes every change touch three packages and leaves no package readable alone.

Which *Gradle module* a file belongs to is the larger decision and comes first. Load the `ktor-toolkit:architecture` skill and let it place the file
before you name it.

**No wildcard imports.** An explicit import list is how a reader resolves a bare type name without an IDE, and it is what keeps the layering grep in
the `ktor-toolkit:architecture` skill honest.

**A section banner inside a file means the file should be two files.** Load the `ktor-toolkit:comments` skill.

## Declarations

**Expression bodies for anything that is one expression.**

```kotlin
fun fromString(token: String): Sort = Sort(token.removePrefix("-"), Direction.fromString(token))
```

A block body wrapping a single `return` adds two lines and no information. Do not force the opposite either: a multi-step function squeezed into one
expression through `let` and `run` chains trades clarity for line count.

**Public declarations carry an explicit return type.** Inference is fine for locals and for `private`. A public signature is a promise, and a changed
body must not be able to change it silently.

**Expose the narrowest type.** Mutable state is `private`; the public view is read-only:

```kotlin
class ValidationContext<T> internal constructor(
    val target: T,
    private val basePath: String,
    private val collected: MutableList<ValidationError>,
) {
    val errors: List<ValidationError> get() = collected
}
```

`collected` is a `MutableList`; `errors` is a `List`. No caller can corrupt the collection, and the class did not copy it to say so.

**`val` unless something forces `var`.** A `var` in a class body is state a reader tracks through every method. A `var` in a builder is fine — that is
what a builder is.

**`data class` for values, plain `class` for behaviour.** `data` says the type is defined by its contents, which makes `equals`, `hashCode` and `copy`
part of the contract you must keep.

**Name catch parameters, `_` when unused.** `catch (failure: Throwable)`, `catch (_: IllegalArgumentException)`.

## Invariants belong in the type

**Validate in the `init` block of the primary constructor, and name the offending value:**

```kotlin
init {
    require(maxSize > 0) { "maxSize must be greater than 0, but was $maxSize" }
    require(ttl > Duration.ZERO) { "ttl must be positive, but was $ttl" }
}
```

"but was $maxSize" is the difference between a report and a diagnosis.

**The primary constructor, specifically.** A `require` in a secondary constructor does not run for instances built by deserialization or by `copy`.
The invariant then holds for objects you construct by hand and fails silently for the ones arriving over the wire — which, in a web service, is every
object that matters.

`require` is for arguments a caller controls; `check` is for internal state that should be impossible. **Use neither on a request body.** That is the
validator's job, and the difference is that a failed request must become a 400, not a 500 — load the `ktor-toolkit:validation` skill.

## Nullability

**Model absence in the type.** A nullable field claims that absent is meaningful. Where it is not, the type should not be nullable and the constructor
should reject it.

**No `!!`.** The bar for a rare exception is a comment proving the null was already excluded — not "I know it is not null", but a written argument for
why nobody can make it null. If you cannot write that argument, the code needs the check.

**Prefer `?.let` to an `if (x != null)` ladder**, and one `let` to a chain of safe calls where the first already established non-nullness. Use the
elvis operator to state the fallback beside the expression rather than three lines below it.

## Closed hierarchies and DSLs

**`sealed interface` when the set of cases is closed**, so `when` is exhaustive without an `else` and adding a case is a compile error rather than a
runtime surprise. Make it `private sealed interface` when the cases are one file's implementation detail.

**The DSL pattern is fixed:** a builder class annotated with the module's `@DslMarker`, an entry-point function that applies the block and builds, and
an `internal` constructor on the result so instances cannot be assembled around the builder's checks.

```kotlin
fun catalogSearch(block: SearchBuilder.() -> Unit): SearchQuery = SearchBuilder().apply(block).build()
```

The `@DslMarker` is not decoration — it is what stops an inner block silently calling the outer receiver's methods.

**Validate in `build()`, not at use.** Duplicate keys, blank names, missing handlers: reject them while the developer is still looking at the
declaration.

## Coroutines and failure

**`suspend` all the way down.** No `runBlocking` outside tests, no `GlobalScope`, ever.

**Never swallow `CancellationException`.** `runCatching` around a suspending call catches it too, so a client that disconnects mid-request leaves the
coroutine running work nobody will read — and under load that is how a dispatcher fills with cancelled requests. Catch it first and rethrow:

```kotlin
try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    logger.warn(failure) { "Pricing lookup failed; falling back to the list price" }
    fallback()
}
```

**Log a caught exception with the throwable, not with `.message`.** The stack trace is the part worth having — load the `ktor-toolkit:logging` skill.

**Never catch in a route.** Let the exception reach the status-pages handler that maps it to a problem detail; a `try`/`catch` in a route body is how
one endpoint's errors stop matching every other endpoint's. Load the `ktor-toolkit:problem-details` skill.

## Naming

**Say the whole word.** `pageSize`, not `ps`. `validationContext`, not `vc`. The 150-column budget exists so names do not have to be short.

**Functions are verbs, properties are nouns, booleans read as assertions** — `hasErrors`, `isPublished`, `appliesToNull`.

**A conversion extension is named for what it produces:** `toResponse`, `toDomain`, `toResource`. A factory that parses is `fromString` or `from`.

**Type parameters say what they stand for when there is more than one.** `T` alone is fine; `T, R, V` in one signature wants KDoc saying which is
which.

**Do not encode the type in the name.** `bookList`, `strTitle`, `iCount` — the signature already said it.

## Every `@Suppress` carries its reason

```kotlin
// Safe: the discriminator was matched above, so this branch cannot see another subtype.
@Suppress("UNCHECKED_CAST")
```

An unexplained suppression is a risk nobody can evaluate and therefore nobody can ever remove. The same goes for an unchecked cast and for the rare
`!!`. Load the `ktor-toolkit:comments` skill for what a comment must earn.

## Before the code is done

**KDoc every public declaration** — the `ktor-toolkit:comments` skill says what it must contain, and it is stricter than "describe the parameters".

**A new branch of logic arrives with the test that reaches it** — load the `ktor-toolkit:tests` skill.

**Run the project's gate**, in the order a failure is cheapest to read:

```bash
make verify
```

Where there is no Makefile, `./gradlew build`. If the project publishes a library, a changed public signature also carries its refreshed `api/` dump
in the same commit; a service has no `api/` directory and skips this.

## Failures the formatter cannot catch

ktlint catches wildcard imports, line length and layout. These compile, lint clean, and go wrong later — check them by reading.

| Mistake                                   | What it silently does                                               |
|-------------------------------------------|---------------------------------------------------------------------|
| `require` in a secondary constructor      | Deserialization and `copy` skip it — the invariant is a fiction     |
| `runCatching` around a suspending call    | Swallows `CancellationException` and breaks structured concurrency  |
| `try`/`catch` inside a route body         | That endpoint's errors stop matching every other endpoint's         |
| `catch (e: Exception)` with no rethrow    | The failure and its stack trace disappear                           |
| Inferred return type on a public function | A body edit changes the published signature with nobody deciding to |
| A public `MutableList` / `MutableMap`     | Callers can corrupt state the class is responsible for              |
| `!!` with no written argument for why     | A crash waiting on an input nobody has tried yet                    |
| `@Suppress` with no reason                | An unevaluable risk, so it is never removed                         |
