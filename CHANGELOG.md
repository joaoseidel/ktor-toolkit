# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — unreleased

First public release. Nothing was published before this, so the breaking changes below are recorded
for anyone who consumed the library from source.

### Breaking

- **The group id and base package moved to `com.github.joaoseidel`.** Dependencies are now
  `com.github.joaoseidel.ktor-toolkit:*`, and every import changes from `com.luizalabs.ktor.toolkit.*`
  to `com.github.joaoseidel.ktor.toolkit.*`. Nothing else about the API changed.
- **`PagedResponse.metadata.totalPages` now counts pages.** It previously held the *index* of the
  last page — 25 elements over a page size of 10 reported `2`. It now reports `3`; the last page
  index is `totalPages - 1`. Any client doing arithmetic on this field must be updated.
- **`PagedResponse.from` rejects a page size of zero or less** with `IllegalArgumentException`
  instead of dividing by zero.
- **`PagedResponse.content` is no longer nullable.** It was `List<T>?` defaulting to an empty list.
- **`PaginationRequest.from(Parameters)` clamps instead of throwing.** A malformed `page` or
  `pageSize` falls back to its default rather than raising `NumberFormatException`; `page` is
  floored at 0 and `pageSize` is coerced into `1..maxPageSize` (100 by default).
- **`Paged.sortedBy` and `Pagination.sortedBy` are now `sortBy`,** matching `PaginationRequest`.
- **`Paged.from(...)` is gone** — use the constructor.
- **`Sort.toGelOrderingExpression` is gone.** The Gel query DSL integration was dropped in favour of
  `Sort.toMongoSortExpression`; nothing else about sorting changed.
- **Error responses are served as `application/problem+json`,** not `application/json`.
- **`ResponseHandlers.handleGenericException` no longer echoes the exception message.** Pass
  `includeExceptionMessage = true` to restore the old behaviour.
- **Validation rules are typed by the property they apply to.** Each rule declares a receiver such
  as `PropertyValidator<*, String?>`, so `should be email()` on an `Int` property no longer compiles
  rather than failing at request time with `should be of type String`. `ValidationRule` is now a
  final class parameterised by the value type, `supportedTypes()` is gone, and a custom rule is
  built with the `validationRule` factory instead of by subclassing.
- **Validation rules no longer take `positiveMessage` / `negativeMessage`.** Override a message with
  `describedAs`, either on the rule or on the assertion: `should be email() describedAs "…"`.
- **`RequestValidationConfig.withValidationContext` is split into `rulesFor` and `rulesFrom`:**
  `rulesFor<CreateBookRequest> { … }` declares rules inline, and `rulesFrom(CreateBookValidator())`
  takes them from a `RequestValidator`.
- **`ValidationContext.getErrors()` is now the `errors` property,** `validateResult()` is
  `toValidationResult()`, and `PropertyValidator.propertyValue` is `value`.
- **`ValidationContext` and `PropertyValidator` are `@DslMarker`-scoped,** so an inner block can no
  longer reach the enclosing receiver — `property(A::x) { property(A::y) { } }` was legal before.
- **Temporal validation rules take a `timeZone` parameter,** inserted before the message parameters.
  Callers passing messages positionally must switch to named arguments. `future()` previously
  resolved in UTC while `past()` and `within()` used the system zone; all now default to the system
  zone.
- **A validation rule no longer reports an error for a `null` property.** Combine with
  `should notBe nil()` to require presence.
- **`ExpandSpec` fields take a configuration block** in place of their `nested` and `batch`
  parameters, and their `getter` / `setter` parameters are now `get` / `set`. `optionalField` is
  gone — `field` accepts a getter that returns null. `polymorphicField` takes `case(…) { }` blocks
  in place of its `batchers` map.
- **The two `toResource` overloads taking a required `links` list are gone,** replaced by a default
  parameter on the remaining two.
- **`ValidationContext.nested` takes a single `nullMessage`** in place of `positiveMessage` and
  `negativeMessage`.
- **`PropertyValidator`'s constructor is internal** and `errors` is a read-only `List`.
- **`inRange` takes `Number` bounds** rather than `Int`.
- **The JVM target is Java 21.**

### Fixed

- Pagination links in `hateoas` built their query strings by concatenation, so any carried-over
  parameter containing `&`, `=`, a space or a non-ASCII character produced a corrupt URL. They now
  go through Ktor's `ParametersBuilder`.
- `first` and `last` pagination links pointed at the wrong pages, because the link builder consumed
  `totalPages` as a count while it held an index.
- `?pageSize=10000000` was an unbounded allocation request; `?pageSize=0` caused a division by zero.
- A validation rule applied to a null property reported `should be of type String`, because
  `Class.isInstance(null)` is always false — optional fields could not be validated at all.
- The cache wrapped suspending calls in `runCatching`, swallowing `CancellationException` and
  breaking structured concurrency.
- `handleBadRequestException` looked for `MissingFieldException` at a fixed depth in the cause
  chain, so on the ContentNegotiation path a missing field was reported as a generic conversion
  failure. It now walks the chain.
- `after()` and `before()` disagreed on how to compare a `LocalDate` against a `LocalDateTime`.
- `min`, `max`, `positive` and `negative` matched only `Int`, `Long`, `Float` and `Double`, so a
  `Short`, `Byte`, `BigDecimal` or `BigInteger` property passed the type check and was then reported
  as violating a bound it met. All four now compare every `Number` type.
- `toResource(call, links)` published every caller-supplied link twice: they were seeded into the
  resource and then appended again.
- Two `ExpandSpec` fields could share one `?expand=` key, running two batches for the same request
  with the second overwriting the first. A duplicate or blank field name is now rejected at build
  time, as is a field with no `batch` and a polymorphic field with no cases.
- `Link`'s `require` checks ran in a secondary constructor and never fired on a deserialized link.
- `InMemoryCache` was an unbounded map keyed by request URL.
- Cache keys base64-encoded the full path and query, so their length was client-controlled. They are
  now hashed.

### Added

- Validation rules compose: `and`, `or`, `!` and `describedAs` operate on rules, and `satisfying`
  builds one from a predicate for a constraint no named rule covers.
- `ValidationContext.each` and `eachNested` validate collection elements, reporting at `tags[0]` and
  `authors[0].email`. `whenever` makes a group of rules conditional on the object under validation,
  which `target` now exposes, and `invariant` states a rule that no single property owns.
- `ExpandSpec` nested specs can be declared inline with `nested { }`, rather than built separately
  and held in a `val`.
- `resource(content) { link(…) }` wraps any content with its links, and `Resource.withLink` takes
  `rel` / `href` / `method` directly.
- `Sort` accepts a property reference, and `sortBy { desc(…); asc(…) }` builds an ordering from
  them, so a rename cannot leave a stale sort key behind.
- `Sort.toMongoSortExpression(...)` resolves sort criteria against an allow-list of field names or
  document property references, collapsing them into the single `Bson` document `find().sort(...)`
  takes; no criteria yields `{}`. The MongoDB driver is an optional dependency — add
  `org.mongodb:mongodb-driver-core`, which any MongoDB driver already brings, yourself.
- `StatusPagesConfig.problemDetails { }` registers every handler in one call, and `on<E> { }` inside
  it maps an application's own exceptions to problems.
- `ProblemDetail` carries the RFC 9457 `type` and `instance` members.
- `ApplicationCall.pagination` and `ApplicationCall.paginationRequest(...)`.
- `InMemoryCache` takes `maxSize`, `ttl` and an injectable `Clock`.
- `LettuceCache`, a `KeyValueCache` over Redis, so instances behind the same store share entries and
  see each other's invalidations. Lettuce is an optional dependency — add `io.lettuce:lettuce-core`
  yourself. Redis applies the TTL, keys are namespaced by `keyPrefix`, and key listing goes through
  `SCAN` rather than `KEYS`.
- `PaginationRequest.from` accepts `defaultPageSize` and `maxPageSize`.
- Public API dumps under `*/api/`, enforced by `apiCheck`.

### Changed

- Dependencies that appear in a module's public signatures are declared `api`, so the published POM
  resolves them. Exposed and the MongoDB driver remain optional — see the README.
- `ExpandSpec` collapsed from four near-duplicate field implementations to two; single-item
  expansion now delegates to the batched path, so the two can no longer diverge.
- ktlint and binary-compatibility-validator run as part of `build`.
- Test coverage went from one module to all six, with Kover gating at 85% line and 65% branch.
