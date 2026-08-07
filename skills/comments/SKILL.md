---
name: comments
description: >-
    The best comment is no comment — default to writing none, and delete the ones you pass while
    working through a file. Comments explain why, never what; KDoc is for public declarations and
    states the contract, especially what happens when input is absent or wrong. Use when writing or
    reviewing any comment or KDoc block, when tempted to explain a line of code, when adding a public
    declaration, when a `@Suppress` needs justifying, and when cleaning up a file.
---

# Comments and KDoc

## The best comment is no comment

**Default to writing none.** A comment is not free and does not start neutral: it is a second thing to keep in step with the code, and only one of the
two is checked by anything. The question is never "is this comment accurate?" — it is "would deleting it lose something the code cannot say?"

Most candidates fail that. Well under one inline comment per hundred lines is not an aspiration, it is simply what a file looks like once the bar is
applied honestly.

The reason to be this strict is rot. Code is executed and tested, so it stays true. A comment is verified by nobody, and a wrong comment is worse than
no comment because it is believed. Every comment you do not write is one that cannot go stale.

**Apply the test before writing one:** draft it, then delete it and read the code alone. If nothing became unknowable, it stays deleted. If the code
only reads correctly *with* the comment, change the code instead — see [Prefer changing the code](#prefer-changing-the-code).

The comment must clear that bar on its own. "It might help someone" does not; that is true of every sentence anyone could write about any line.

## When a comment earns its place

These are the cases that qualify. Each answers *why*; none describes *what*.

**Why this shape, and not the obvious one.** Otherwise the next reader "simplifies" it back.

```kotlin
// One `let` rather than a chain of safe calls: past the first, nothing can be null.
```

**Why a suppression or a cast is safe.** A `@Suppress` without a reason is an unexplained risk.

```kotlin
// Safe: `appliesToNull` is false, so ShouldScope never reaches a null value.
```

**What a dense expression is.** Name the algorithm and give one worked case.

```kotlin
// Ceiling division: 25 elements over a page size of 10 spans 3 pages.
val totalPages = ((totalElements + pageSize - 1) / pageSize).toInt()
```

**A library behaviour a reader would have to look up.**

```kotlin
// accessOrder = true makes iteration order least-recently-used first, which is the eviction order.
```

**What a guard prevents.** A validation whose absence would cause something subtle.

```kotlin
// Two fields under one key would run two batches for the same `?expand=` request, the
// second silently overwriting the first.
```

**Why the code is defensive.** So nobody trims it back to the happy path.

```kotlin
// Walk the chain rather than assume a depth: how deeply Ktor wraps the serialization
// failure depends on the content negotiation path the request took.
```

**Why something is *where* it is.**

```kotlin
// In the primary constructor, so it also guards values arriving from deserialization.
```

**A property that must not be lost.** Usually performance.

```kotlin
// One batch call per discriminator, not per item.
```

## What never gets a comment

```kotlin
// Increment the counter
counter++

// Loop through the books
books.forEach { … }

/** Gets the title. */
fun getTitle(): String

// Create the repository
val repository = ExposedBookRepository()
```

These add reading work and nothing else. The signature, the name and the expression already said it.

## Delete as you read

Holding new comments to the bar still lets the existing ones accumulate. **Treat every file you open as a chance to remove some** — while you are
already in the file is the only time it is cheap.

**Delete without asking, inside code you are already changing:**

| Delete on sight                           | Because                                      |
|-------------------------------------------|----------------------------------------------|
| A comment restating the line below it     | Two things to keep in step; one is unchecked |
| A comment that no longer matches the code | Actively misleading — the worst kind         |
| Commented-out code, at any age            | Git has it                                   |
| `// TODO` with no owner and no issue      | Never actioned, never removed                |
| `// modified by … 2024-03-11`             | That is `git blame`                          |
| `/** Gets the title. */` on `getTitle()`  | Fills the KDoc slot, adds nothing            |
| `// ───── helpers ─────` banners          | Usually means the file should be two files   |

These are unambiguous, and they ride along in a diff a reviewer is already reading.

**Flag rather than delete when the comment sits outside the change you were asked to make.** A diff full of unrelated comment removals buries the real
change and a reviewer cannot tell which lines matter — the same reason the `ktor-toolkit:commit` skill separates formatting from behaviour. Name what
you found and offer a follow-up commit that does only that.

**Never delete a comment you do not understand.** A comment that looks redundant may be the only surviving record of why the code is not the obvious
shape — precisely the kind this skill says to keep. If you cannot tell, it stays, and you say so.

## Prefer changing the code

Most comments are a symptom. Reach for the fix first.

| Tempted to write                     | Do instead                                                     |
|--------------------------------------|----------------------------------------------------------------|
| `// check if the user can edit`      | Extract `fun canEdit(user, book): Boolean`                     |
| `// p = page, s = size`              | Rename `p` and `s`                                             |
| `// this is the total including tax` | Rename to `totalWithTax`, or a `Money` value object            |
| `// step 3: map to response`         | Extract a function per step                                    |
| `// must be called after init()`     | Make it impossible — take the initialised thing as a parameter |
| `// title must not be blank`         | A validation rule — load the `ktor-toolkit:validation` skill   |

A comment explaining a name is a naming bug. A comment explaining a sequence is a decomposition bug. A comment explaining a constraint is usually a
type that has not been introduced yet — load the `ktor-toolkit:architecture` skill for the value object argument.

## KDoc

**Public declarations carry KDoc. Nothing else needs it.** `internal` and `private` declarations get a comment only when they would have earned an
inline one.

The rule for what to write: **say what the thing is for, and what it does when the input is absent or wrong — not what the signature already says.**

```kotlin
/**
 * Everything the catalogue needs from wherever books are stored.
 *
 * Implemented by `ExposedBookRepository` against Postgres; a test may substitute an in-memory one.
 *
 * Implementations must honour [Pagination] at the query, not by slicing a full result in memory —
 * the catalogue is large enough that the difference is the endpoint's latency budget.
 */
interface BookRepository {
    /** Returns the requested slice in the requested order, or an empty list when the page is past the end. */
    suspend fun findAll(pagination: Pagination): List<Book>

    /** Returns the book, or `null` when no book has that id. Does not distinguish deleted from never-existed. */
    suspend fun findById(id: BookId): Book?

    /** The total across every page, for pagination metadata. One count query per call. */
    suspend fun count(): Long
}
```

Look at what each line adds beyond the signature. `findAll` names the past-the-end case. `findById` says what `null` does *not* tell you, which is the
distinction a caller would otherwise assume. `count` says it costs a query, which is what stops someone calling it in a loop. The interface block says
what an implementer must guarantee, not just what the methods are called.

Compare the version that says nothing:

```kotlin
/**
 * Gets a book.
 * @param id the id
 * @return the book
 */
suspend fun findById(id: BookId): Book?
```

**Document the contract, not the mechanism.** The things worth stating, because a caller cannot see them:

- What happens for absent, empty or invalid input — returns `null`, throws, clamps, ignores.
- What it throws, and when.
- Whether it is safe to share across threads or coroutines.
- Whether it performs I/O, and how much — "one query per call", "walks the keyspace".
- Anything a caller must do in a particular order, or must not do.

**Use `[References]`** to related types; they become links and they survive renames. A simple name is fine when the file can already see the type —
same package, or already imported, as with `[Pagination]` above. It is *not* fine in the OpenAPI comment above a route: those brackets are read by the
compiler plugin to attach a schema, an unresolved one is dropped without a warning, and the fully-qualified name is mandatory there — load the
`ktor-toolkit:openapi` skill.

**`@param` only when the name does not carry it.** A `@param id the id` is noise. A
`@param excludeQueryKeys Query parameters that must not take part in the cache key` is the contract.

**Show the API when the shape is not obvious.** A short code block in the KDoc of a DSL entry point saves every caller a trip to the tests.

**For an extension point — an interface others implement — say what an implementer must guarantee**, not just what the methods do. That is the
difference between documentation and a signature dump.

## In tests

The same rule, with one addition worth naming: a shared test helper gets one line saying why it exists, because its purpose is rarely obvious from its
shape.

```kotlin
/** A clock the test moves by hand, so expiry can be exercised without sleeping. */
private class TestClock(…)
```

Test names carry the rest — load the `ktor-toolkit:tests` skill. A comment above a `should` block is a sign the name is not doing its job.

## Two habits that carry the rest

**Read the comment above every line you edit.** A stale comment is the one kind that actively misleads, and the moment you change the line beneath it
is the only moment anyone is looking. Fix it or delete it in the same commit.

**Never leave a `@Suppress`, cast or `!!` unexplained.** Nobody can evaluate the risk, so nobody can ever remove it, and it outlives the condition
that made it safe. If you cannot write the argument, the code needs a check rather than a suppression.
