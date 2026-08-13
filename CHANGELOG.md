# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — unreleased

First public release. Nothing was published before this, so the breaking changes below are recorded for anyone who consumed the library from source.

### Breaking

- **The group id is `io.github.joaoseidel` and the base package moved to `com.github.joaoseidel`.**
  Dependencies are now `io.github.joaoseidel:ktor-toolkit-*` — the group has to sit under
  `io.github.<user>` because that is the only namespace Maven Central verifies from a GitHub account. Every import changes from
  `com.luizalabs.ktor.toolkit.*` to
  `com.github.joaoseidel.ktor.toolkit.*`. Nothing else about the API changed.
- **`PagedResponse.metadata.totalPages` now counts pages.** It previously held the *index* of the last page — 25 elements over a page size of 10
  reported `2`. It now reports `3`; the last page index is `totalPages - 1`. Any client doing arithmetic on this field must be updated.
- **`PagedResponse.from` rejects a page size of zero or less** with `IllegalArgumentException`
  instead of dividing by zero.
- **`PagedResponse.content` is no longer nullable.** It was `List<T>?` defaulting to an empty list.
- **`PaginationRequest.from(Parameters)` clamps instead of throwing.** A malformed `page` or
  `pageSize` falls back to its default rather than raising `NumberFormatException`; `page` is floored at 0 and `pageSize` is coerced into
  `1..maxPageSize` (100 by default).
- **`Paged.sortedBy` and `Pagination.sortedBy` are now `sortBy`,** matching `PaginationRequest`.
- **`Paged.from(...)` is gone** — use the constructor.
- **`Sort.toGelOrderingExpression` is gone.** The Gel query DSL integration was dropped in favour of
  `Sort.toMongoSortExpression`; nothing else about sorting changed.
- **Error responses are served as `application/problem+json`,** not `application/json`.
- **`ResponseHandlers.handleGenericException` no longer echoes the exception message.** Pass
  `includeExceptionMessage = true` to restore the old behaviour.
- **Validation rules are typed by the property they apply to.** Each rule declares a receiver such as `PropertyValidator<*, String?>`, so
  `should be email()` on an `Int` property no longer compiles rather than failing at request time with `should be of type String`. `ValidationRule` is
  now a final class parameterised by the value type, `supportedTypes()` is gone, and a custom rule is built with the `validationRule` factory instead
  of by subclassing.
- **Validation rules no longer take `positiveMessage` / `negativeMessage`.** Override a message with
  `describedAs`, either on the rule or on the assertion: `should be email() describedAs "…"`.
- **`RequestValidationConfig.withValidationContext` is split into `rulesFor` and `rulesFrom`:**
  `rulesFor<CreateBookRequest> { … }` declares rules inline, and `rulesFrom(CreateBookValidator())`
  takes them from a `RequestValidator`.
- **`ValidationContext.getErrors()` is now the `errors` property,** `validateResult()` is
  `toValidationResult()`, and `PropertyValidator.propertyValue` is `value`.
- **`ValidationContext` and `PropertyValidator` are `@DslMarker`-scoped,** so an inner block can no longer reach the enclosing receiver —
  `property(A::x) { property(A::y) { } }` was legal before.
- **Temporal validation rules take a `timeZone` parameter,** inserted before the message parameters. Callers passing messages positionally must switch
  to named arguments. `future()` previously resolved in UTC while `past()` and `within()` used the system zone; all now default to the system zone.
- **A validation rule no longer reports an error for a `null` property.** Combine with
  `should notBe nil()` to require presence.
- **`ExpandSpec` fields take a configuration block** in place of their `nested` and `batch`
  parameters, and their `getter` / `setter` parameters are now `get` / `set`. `optionalField` is gone — `field` accepts a getter that returns null.
  `polymorphicField` takes `case(…) { }` blocks in place of its `batchers` map.
- **The two `toResource` overloads taking a required `links` list are gone,** replaced by a default parameter on the remaining two.
- **`ValidationContext.nested` takes a single `nullMessage`** in place of `positiveMessage` and
  `negativeMessage`.
- **`PropertyValidator`'s constructor is internal** and `errors` is a read-only `List`.
- **`inRange` takes `Number` bounds** rather than `Int`.
- **The JVM target is Java 21.**

### Fixed

- The `kover` skill's `report/build.gradle.kts` example could not compile: it omitted the `AggregationType`, `CoverageUnit` and `KoverReport` imports,
  and showed `filters { }` without saying it nests inside `reports { }` — where, at the top level, every exclusion silently applies to nothing. It is
  now one complete file.
- The `tests` skill's Testcontainers example declared a container and never started it, so copying it gave a connection refused that reads like
  misconfiguration. It now shows `beforeSpec` / `afterSpec` with the Flyway migration the surrounding prose already required.
- The `install` skill never said the modules need Java 21, so an older toolchain failed with `class file has wrong version` — which reads like a
  corrupt artifact. It is now the first step, with a check command and a troubleshooting row.
- The `container` skill mixed JDK 25 base images with a `distroless/java21` row and never said the tag must match the project's `jvmToolchain`. A jar
  built for 25 on a 21 runtime fails with `UnsupportedClassVersionError`, which names bytecode versions rather than the mismatch.
- Pagination links in `hateoas` built their query strings by concatenation, so any carried-over parameter containing `&`, `=`, a space or a non-ASCII
  character produced a corrupt URL. They now go through Ktor's `ParametersBuilder`.
- `first` and `last` pagination links pointed at the wrong pages, because the link builder consumed
  `totalPages` as a count while it held an index.
- `?pageSize=10000000` was an unbounded allocation request; `?pageSize=0` caused a division by zero.
- A validation rule applied to a null property reported `should be of type String`, because
  `Class.isInstance(null)` is always false — optional fields could not be validated at all.
- The cache wrapped suspending calls in `runCatching`, swallowing `CancellationException` and breaking structured concurrency.
- `handleBadRequestException` looked for `MissingFieldException` at a fixed depth in the cause chain, so on the ContentNegotiation path a missing
  field was reported as a generic conversion failure. It now walks the chain.
- Every error an `invariant` recorded reached the client empty. `handleValidationException` parsed a reason by matching the backticked property path
  `ValidationError` quotes, but an object-level error carries no path and renders as the bare message, so the match failed and the message was
  dropped. Such reasons are now reported as-is, under a `$` key.
- A property that broke more than one rule reported only the last of them: the reasons were folded into `properties` with `associate`, which keeps one
  entry per key. Every rule a property breaks is now reported, joined under that property's key.
- `after()` and `before()` disagreed on how to compare a `LocalDate` against a `LocalDateTime`.
- `min`, `max`, `positive` and `negative` matched only `Int`, `Long`, `Float` and `Double`, so a
  `Short`, `Byte`, `BigDecimal` or `BigInteger` property passed the type check and was then reported as violating a bound it met. All four now compare
  every `Number` type.
- `toResource(call, links)` published every caller-supplied link twice: they were seeded into the resource and then appended again.
- Two `ExpandSpec` fields could share one `?expand=` key, running two batches for the same request with the second overwriting the first. A duplicate
  or blank field name is now rejected at build time, as is a field with no `batch` and a polymorphic field with no cases.
- `Link`'s `require` checks ran in a secondary constructor and never fired on a deserialized link.
- `InMemoryCache` was an unbounded map keyed by request URL.
- Cache keys base64-encoded the full path and query, so their length was client-controlled. They are now hashed.
- `Resource`'s serial descriptor named `_links` and nothing else, so anything reading the type rather than an instance — OpenAPI schema inference
  above all — documented every wrapped response as a body carrying links and no content. It now declares the content's own fields alongside `_links`,
  matching what the serializer writes, and its serial name carries the content's so two different `Resource<T>` no longer collide on one schema
  component. Serializing and deserializing are unchanged: both work in `JsonObject` directly and never read the descriptor.

### Added

- Validation rules compose: `and`, `or`, `!` and `describedAs` operate on rules, and `satisfying`
  builds one from a predicate for a constraint no named rule covers.
- `ValidationContext.each` and `eachNested` validate collection elements, reporting at `tags[0]` and
  `authors[0].email`. `whenever` makes a group of rules conditional on the object under validation, which `target` now exposes, and `invariant` states
  a rule that no single property owns.
- `ExpandSpec` nested specs can be declared inline with `nested { }`, rather than built separately and held in a `val`.
- `resource(content) { link(…) }` wraps any content with its links, and `Resource.withLink` takes
  `rel` / `href` / `method` directly.
- `Sort` accepts a property reference, and `sortBy { desc(…); asc(…) }` builds an ordering from them, so a rename cannot leave a stale sort key
  behind.
- `Sort.toMongoSortExpression(...)` resolves sort criteria against an allow-list of field names or document property references, collapsing them into
  the single `Bson` document `find().sort(...)`
  takes; no criteria yields `{}`. The MongoDB driver is an optional dependency — add
  `org.mongodb:mongodb-driver-core`, which any MongoDB driver already brings, yourself.
- `StatusPagesConfig.problemDetails { }` registers every handler in one call, and `on<E> { }` inside it maps an application's own exceptions to
  problems.
- `ProblemDetail` carries the RFC 9457 `type` and `instance` members.
- `ApplicationCall.pagination` and `ApplicationCall.paginationRequest(...)`.
- `InMemoryCache` takes `maxSize`, `ttl` and an injectable `Clock`.
- `LettuceCache`, a `KeyValueCache` over Redis, so instances behind the same store share entries and see each other's invalidations. Lettuce is an
  optional dependency — add `io.lettuce:lettuce-core`
  yourself. Redis applies the TTL, keys are namespaced by `keyPrefix`, and key listing goes through
  `SCAN` rather than `KEYS`.
- `KeyValueCache.keys(prefix)` lists only the keys under a prefix, and `invalidateNamespace` asks for the namespace rather than fetching every key and
  filtering them itself. `LettuceCache` narrows with `SCAN MATCH`, so invalidating one namespace no longer carries every other namespace's keys back
  from Redis — the sweep is unchanged, what crosses the wire is not. The default implementation filters `keys()` as before, so an existing
  `KeyValueCache` keeps working without overriding it.
- `PaginationRequest.from` accepts `defaultPageSize` and `maxPageSize`.
- Public API dumps under `*/api/`, enforced by `apiCheck`.
- A `healthcheck` skill, covering health endpoints with Cohort: separate liveness and readiness registries, which dependencies may fail a probe,
  writing a `HealthCheck` that reports a failure instead of throwing it, and keeping the heapdump, sysprops and logging endpoints off the public
  internet.
- A `codestyle` skill, covering what ktlint cannot check: file and package naming, expression bodies, explicit public return types, exposing the
  narrowest type over private mutable state, `require` in the primary constructor so deserialization and `copy` cannot skip it, sealed hierarchies,
  the `@DslMarker` builder pattern, null handling without `!!`, and never swallowing `CancellationException`.
- A `changelog` skill, covering how this file is written: the section order, what an entry has to say from the consumer's side rather than the diff's,
  which changes earn one, and the rule that the entry, the code and the refreshed API dump land in a single commit.

### Changed

- **The public KDoc states contracts rather than restating signatures.** `RequestValidator`, `HttpStatusException`, `Link`, `Page`, `Sort`,
  `PagedResponse`, `PaginationRequest`, `ValidationError`, `Expandable`, `ExpandRequest` and `ExpandableSerializer` previously opened with a paragraph
  that named the type and listed its properties again. They now say what the type is for and what it does at the edges — what `Sort.fromString` does
  with a property no data source knows, that `ExpandRequest.from` reads only the first `expand` parameter, that an `Expandable.Partial` does not
  survive a round trip, that a `RequestValidator` is shared across concurrent requests and must hold no state of its own. `ExpandableSerializer`,
  `ExpandRequest.from`, `ExpandRequest.NONE` and `Expandable.Ref` / `.Resolved` had no documentation at all. No signature changed.
- Dependencies that appear in a module's public signatures are declared `api`, so the published POM resolves them. Exposed and the MongoDB driver
  remain optional — see the README.
- `ExpandSpec` collapsed from four near-duplicate field implementations to two; single-item expansion now delegates to the batched path, so the two
  can no longer diverge.
- The `openapi` skill now requires a fully-qualified type in every `[]` reference of an OpenAPI comment, rather than only where the simple name is
  ambiguous. Those brackets resolve against the route file's imports, so `[ProblemDetail]` on an error response — thrown and mapped, never named in
  the route's code — resolved to nothing and dropped the schema without a warning. The `comments` skill draws the same distinction for ordinary KDoc.
- **The skills are written for the repository that installs them.** They previously carried this repository's own rules as if they were universal:
  `start` branched on "is this the toolkit repo?", `commit` scoped a commit by toolkit module name, `kover` presented a 100% gate as the standard.
  Library-maintenance steps a service has no use for — `make api`, `apiCheck`, the `api/` dump — are now conditional on the project publishing a
  library, and `changelog` treats an HTTP contract change as the main case rather than a footnote.
- **A skill that describes a project artifact now says what to do when your repository has none.** `makefile`, `changelog`, `container`, `kover`,
  `tests`, `gradle`, `migrations`, `healthcheck`, `openapi`, `architecture` and `logging` each propose the file and wait, rather than scaffolding it
  unasked or silently working around its absence. `migrations` covers baselining an existing schema, which is what a live service actually needs from
  its first versioned migration.
- **`comments` now removes comments as well as governing new ones.** The default is writing none, with a stated test for whether one earns its place,
  and a `Delete as you read` section that clears stale and redundant comments from code you are already changing — flagging, never touching, anything
  outside that change.
- `start` and `install` are rewritten. `start` routes by phase — orient, route, implement, document, verify, record. `install` leads with the Java 21
  check and gains a step on imports, since the package root (`com.github.joaoseidel`) is not the group id (`io.github.joaoseidel`), each validator
  rule imports separately from `…validator.validators`, and `withCache` extends `ApplicationRequest`, so the call is `call.request.withCache(…)`.
- `codestyle` is rewritten and no longer assumes ktlint is configured: where the project has its own formatter that one wins, and where it has none
  the skill offers to add one as its own commit rather than reformatting inside a feature branch.
- Every skill description is roughly half its former length, so a skill triggers on the task it covers rather than on a table of contents.
- The prose is denser throughout — same rules and reasoning, several hundred words lighter across `tests`, `healthcheck`, `logging` and `openapi`. The
  `Common mistakes` tables now list only failures that leave the build green, since running the build already catches the loud ones.
- `makefile`'s target set gains `verify` — the single command `start` Phase 4 runs — and moves `api`, `api_check` and `publish_local` into a
  library-only section.
- The two regexes that parse a `MissingFieldException` are compiled once rather than on every malformed request body, matching the reason already
  stated for the validation-reason regex beside them.
- ktlint and binary-compatibility-validator run as part of `build`.
- Test coverage went from one module to all six, with Kover gating at 85% line and 65% branch.
