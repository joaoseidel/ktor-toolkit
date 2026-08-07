---
name: comments
description: >-
  The project's documentation philosophy — comments are rare and explain why, never what; KDoc is
  for public declarations and states the contract, especially what happens when input is absent or
  wrong. Use when writing or reviewing any comment or KDoc block, when tempted to explain a line of
  code, when adding a public declaration to a module, when a `@Suppress` or an unusual construct
  needs justifying, and when a comment is about to restate what the code already says.
---

# Comments and KDoc

## Comments are exceptional

The main source of this toolkit carries **22 inline comments across 3,571 lines** — under one line in a hundred. That is not neglect; it is the
target. Every one of those 22 says something the code cannot.

The reason to be strict is that comments rot. Code is executed and tested, so it stays true. A comment is checked by nobody, and a wrong comment is
worse than no comment because it is believed. The fewer you have, the likelier each is still accurate.

So: **a comment must say something the code cannot say.** If it restates the code, delete it. If the code needs explaining, change the code.

## When a comment earns its place

Every real comment in this codebase falls into one of these. Each answers *why*; none describes *what*.

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

Also delete on sight: commented-out code (git remembers it), `// TODO` with no owner or issue, change-log comments (`// modified by … 2024-03-11` —
that is `git blame`), and section banners inside a file (`// ───── helpers ─────` usually means the file should be two files).

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
 * The minimal contract the toolkit needs from a cache.
 *
 * [InMemoryCache] is provided for single-node deployments and tests, and [LettuceCache] for Redis;
 * implement it over Memcached or anything else you already run.
 *
 * Implementations should treat failures as recoverable — callers go through `cacheCatching`, which
 * logs and falls back to the origin rather than failing the request.
 */
interface KeyValueCache {
    /** Returns the stored value, or `null` when the key is absent or expired. */
    suspend fun get(key: String): ByteArray?

    /** Stores [value] under [key], replacing any previous entry. */
    suspend fun put(key: String, value: ByteArray)

    /** Every live key. Used by the namespace and content invalidation helpers. */
    suspend fun keys(): List<String>
}
```

Look at what each line adds beyond the signature. `get` names the two absent cases. `put` promises replacement rather than failure. `keys` says *why
it exists*, which is what tells an implementer how expensive it is allowed to be. The interface block says what to implement and what implementers
must guarantee.

Compare the version that says nothing:

```kotlin
/**
 * Gets a value.
 * @param key the key
 * @return the value
 */
suspend fun get(key: String): ByteArray?
```

**Document the contract, not the mechanism.** The things worth stating, because a caller cannot see them:

- What happens for absent, empty or invalid input — returns `null`, throws, clamps, ignores.
- What it throws, and when.
- Whether it is safe to share across threads or coroutines.
- Whether it performs I/O, and how much — "one query per call", "walks the keyspace".
- Anything a caller must do in a particular order, or must not do.

**Use `[References]`** to related types; they become links and they survive renames.

**`@param` only when the name does not carry it.** A `@param key the key` is noise. A
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

## Common mistakes

| Mistake                                   | Why it hurts                                      |
|-------------------------------------------|---------------------------------------------------|
| A comment restating the line below it     | Two things to keep in step; one is unchecked      |
| `/** Gets the name. */` on `getName()`    | Fills the KDoc slot without adding anything       |
| `@param`/`@return` that repeat the names  | Noise that trains readers to skip KDoc entirely   |
| A `@Suppress` with no reason              | An unexplained risk that nobody can safely remove |
| Commented-out code                        | Git remembers; the file just gets harder to read  |
| `// TODO` with no owner or issue          | Never actioned, never removed                     |
| A comment explaining a variable name      | Rename the variable                               |
| A comment listing the steps of a function | Extract the steps                                 |
| KDoc on a private helper that needed none | Volume that dilutes the blocks that matter        |
| A comment left after the code changed     | Actively misleading — worse than none             |
