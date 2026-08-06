---
name: validation
description: >-
  Request validation with ktor-toolkit-validator — the rulesFor<T> { property(…) { should be … } }
  DSL over Ktor's RequestValidation, where rules live, and the toolkit's nullable-DTO convention
  (request properties are nullable with defaults; nullability ends at toDomain()). Use whenever an
  endpoint accepts a body or constrained query parameters, when deciding whether a rule is
  syntactic or a business rule, when a required field needs enforcing, and whenever you see manual
  isNullOrBlank checks at the top of a handler or `!!` on a request DTO. Covers blank, email, size,
  min, max, inRange, uuid, pattern, past, future, before, after, within, nil, satisfying, rule
  composition, describedAs, nested, each, eachNested, whenever and invariant.
---

# Request validation

## Two kinds of validation, one boundary

**Syntactic validation** answers a question about the request alone: is the title present, is that a
well-formed email, is the quantity positive, is `publishedAt` before `expiresAt`. Everything needed
is in the payload. This is what `RequestValidation` is for, and it is declarative.

**Business validation** answers a question about the world: is this ISBN already taken, may this
account place an order, is there stock. It needs a repository, a clock, or another service.

The boundary is one question: **does answering it require I/O?** If yes, it is not request
validation — it belongs in the use case or the entity, in `-core`, and it fails by throwing a
domain exception that `ktor-toolkit:problem-details` maps. Putting an ISBN-uniqueness check in
`rulesFor` would drag a repository into the plugin configuration and make the rule untestable
without a database.

Cross-field rules are still syntactic as long as both fields are in the request. `invariant` exists
for exactly those.

## Before writing rules, ask

The toolkit cannot guess business constraints, and a wrong guess ships as a 400 for a legitimate
request. Ask the user directly:

- Which fields are **required**, and which are genuinely optional?
- What are the **bounds** — maximum title length, allowed quantity range, earliest date?
- Which fields have a **format** — email, UUID, a code with a pattern?
- Are there rules **between fields** — a date range, a field required only when another is set?
- What should the **message** say? The default is generic; a good message names the constraint.

Then propose what they did not think to mention. These are worth suggesting almost every time,
because their absence is a bug that only shows up in production:

| Propose | Because |
|---|---|
| A `size(max = …)` on every free-text `String` | Unbounded text reaches the database and the response payload |
| `size(min = 1)` on collections that must not be empty | An empty list usually means the client built the request wrong |
| `positive()` on quantities, amounts and counts | A negative quantity is rarely a domain concept |
| A `past()` / `future()` bound on dates | A birthdate in 2350 is a typo the client should hear about |
| `notBe nil()` on anything the domain cannot construct without | Otherwise the failure surfaces as a 500 at mapping time |

Say which ones you added on your own initiative, so the user can reject them.

## Where rules live

Beside the DTO they constrain, in `-adapters/web`, exposed as an extension that `-app` installs.
Keeping the rule next to the type is what stops them drifting apart when a field is renamed.

```kotlin
// -adapters/web/book/BookValidation.kt
fun RequestValidationConfig.bookRules() {
    rulesFor<CreateBookRequest> {
        property(CreateBookRequest::title) {
            should notBe nil()
            should notBe blank()
            should be size(min = 3, max = 200)
        }
        property(CreateBookRequest::authorEmail) {
            should be email()
        }
        property(CreateBookRequest::publishedAt) {
            should be past()
        }
    }
}
```

```kotlin
// -app/plugin/Validation.kt
install(RequestValidation) {
    bookRules()
}
```

For a request with more than a handful of fields, implement `RequestValidator<T>` and register it
with `rulesFrom(validator)`. The validator is then a plain object with no Ktor in sight, which means
it can be unit-tested directly — see `ktor-toolkit:tests`.

## The DSL

Rules attach to properties **by reference**, so renaming a field is a compile error rather than a
silently dead rule. They are also typed: `should be email()` on an `Int` is an unresolved reference,
not a runtime surprise, and completion inside a `property { }` block offers only the rules that
apply to that type.

| Rule | Applies to |
|---|---|
| `blank`, `email`, `pattern(regex)` | `String` |
| `uuid` | `String`, `UUID`, `Uuid` |
| `size(min, max)` | `String`, `Collection`, `Map`, `Array` |
| `min`, `max`, `inRange(min, max)`, `positive`, `negative` | any `Number` |
| `past`, `future`, `before(date)`, `after(date)`, `within(duration)` | `LocalDate`, `LocalDateTime`, `Instant` |
| `nil`, `satisfying(message) { }` | any |

**Absence is not a failure.** A rule stays silent when the property is `null` — `should be email()`
says nothing about an optional field that was not sent. `nil` is the only rule with an opinion about
absence, so requiring a field and constraining it are two separate assertions:

```kotlin
property(CreateBookRequest::authorEmail) {
    should notBe nil()
    should be email()
}
```

That separation is deliberate. It is what lets the same rules describe a PATCH body, where every
field is optional but each must still be well formed if present.

**Composition.** Rules are values — combine with `and`, `or`, `!`, and write a one-off with
`satisfying`. Watch the parentheses; infix calls associate to the left:

```kotlin
property(CreateBookRequest::isbn) {
    should be (uuid() or satisfying("should be an ISBN") { it.isValidIsbn() })
}
```

**Messages.** Every rule has a default. Override with `describedAs`, which needs no parentheses
after an assertion:

```kotlin
should be email() describedAs "should be a work email address"
```

**Conditions, collections and invariants.** `whenever` makes a group conditional, `each` validates
elements as values and `eachNested` as objects — reporting at `tags[0]` and `authors[0].email` —
and `invariant` states a rule no single property owns:

```kotlin
rulesFor<CreateBookRequest> {
    each(CreateBookRequest::tags) { should notBe blank() }

    eachNested(CreateBookRequest::authors) {
        property(Author::email) { should be email() }
    }

    whenever(target.draft != true) {
        property(CreateBookRequest::publishedAt) { should notBe nil() }
    }

    invariant("should not be dated before its author was born") {
        it.publishedAt != null && it.authorBornAt != null && it.publishedAt > it.authorBornAt
    }
}
```

`target` is the object under validation, so a rule can depend on a sibling: `should be
after(target.startsAt)`.

**Time zones.** Temporal rules take an explicit `timeZone`, defaulting to the system zone. Pass
`TimeZone.UTC` when the verdict must not depend on where the server runs, and `now` to make a test
deterministic.

## The nullability convention

**Request DTO properties are nullable, with `= null` defaults.**

```kotlin
@Serializable
data class CreateBookRequest(
    val title: String? = null,
    val isbn: String? = null,
    val authorEmail: String? = null,
    val tags: List<String>? = null,
)
```

This looks like giving up on the type system, and it is the opposite — it is what makes the type
system useful one layer later. The reason is how kotlinx-serialization fails:

A non-null `val title: String` with the key missing throws `MissingFieldException` during
deserialization, **before any rule runs**. The toolkit handles that case gracefully — `problemDetails`
digs the exception out of the cause chain and reports the missing fields with their paths — so this
is not about getting an unusable error. It is about getting a *complete* one.

Deserialization and validation are two different mechanisms, and a request only ever reaches one of
them. A body that omits `title` and also carries a malformed `authorEmail` fails at deserialization,
reports the missing title, and says nothing about the email. The client fixes it, resubmits, and
only then learns about the second problem.

Nullable with a default gives the deserializer nothing to complain about, so every field arrives at
validation and every violation — missing, malformed, out of range — comes back in one response,
through one mechanism, with messages you control via `describedAs`. It also puts "required" in one
place: with non-null properties it is decided by the type *and* by the rules, and those two drift.

The `= null` default is not optional. Without it, kotlinx-serialization still requires the key to be
present even for a nullable property, and you are back to `MissingFieldException`.

### Nullability ends at `toDomain()`

Nullable types must not travel further than the mapper. `-core` takes non-null parameters and value
objects; nothing inward should ever ask "but what if the title is null?" when validation has
already answered.

```kotlin
// -adapters/web/book/CreateBookRequest.kt
fun CreateBookRequest.toDomain(): NewBook =
    NewBook(
        title = title.orFail(this::title),
        isbn = Isbn(isbn.orFail(this::isbn)),
        authorEmail = Email(authorEmail.orFail(this::authorEmail)),
        tags = tags.orEmpty(),
    )

private fun <T : Any> T?.orFail(property: KProperty0<T?>): T =
    this ?: error("`${property.name}` was null after validation — is a `should notBe nil()` rule missing?")
```

`orFail` throws `IllegalStateException`, which becomes a 500 — and that is right. Reaching it means
a rule is missing, which is a server bug, not something the client did. The message names the fix so
the next person does not have to reconstruct the reasoning. `requireNotNull(title) { … }` works
equally well if you would rather not carry a helper; the point is that the check exists in exactly
one place and says what is wrong.

Note `tags.orEmpty()`. An absent collection and an empty one usually mean the same thing to the
domain, so collapse them at the boundary rather than pushing a nullable list inward.

### Value objects belong in the mapper, not on the DTO

It is tempting to declare `val isbn: Isbn?` and let the value object's `init` do the validating.
Do not — the failure lands in the wrong place. `Isbn`'s `require` throws during *deserialization*,
so it becomes the same pathless `BadRequestException` as a missing field, and the client is told the
body was malformed rather than which field was wrong.

Validate the raw `String?` on the DTO with `pattern` or `satisfying`, and construct the value object
in `toDomain()`. The rule reports at `isbn`, and the constructor's `require` becomes a
belt-and-braces check that only fires if the rule and the value object ever disagree.

## How failures reach the client

`ValidationResult.Invalid` raises `RequestValidationException`, which the toolkit's
`problemDetails { }` turns into an `application/problem+json` response with one entry per failed
rule, keyed by field path and rendered through the configured naming strategy. You do not write any
of that — but you do need `problemDetails { }` installed, or Ktor answers with its default error
page instead. `ktor-toolkit:problem-details` covers the envelope.

## Common mistakes

| Mistake | Why it hurts |
|---|---|
| `if (request.title.isNullOrBlank()) throw …` in the handler | Imperative, stops at the first failure, and invisible to anything reading the DTO |
| `!!` on a request property in the mapper | Fails with no explanation of which rule is missing |
| Non-null DTO properties | Missing fields and rule violations can never appear in the same response |
| Nullable property without `= null` | Still throws `MissingFieldException`; the convention silently does nothing |
| `should be email()` as the only rule on a required field | Silent on `null` — the field is now optional and nobody meant that |
| A value object as a DTO property type | Its `require` fires during deserialization, losing the field path |
| A repository call inside `rulesFor` | Business validation in the wrong layer; untestable without a database |
| Rules declared in `-app` next to `install(RequestValidation)` | Drift from the DTO they constrain; a rename leaves them stale |
| Passing nullable request types into a use case | `-core` grows null handling for a case validation already ruled out |
