# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — unreleased

First public release. Nothing was published before this, so the breaking changes below are recorded
for anyone who consumed the library from source.

### Breaking

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
- **Error responses are served as `application/problem+json`,** not `application/json`.
- **`ResponseHandlers.handleGenericException` no longer echoes the exception message.** Pass
  `includeExceptionMessage = true` to restore the old behaviour.
- **Temporal validation rules take a `timeZone` parameter,** inserted before the message parameters.
  Callers passing messages positionally must switch to named arguments. `future()` previously
  resolved in UTC while `past()` and `within()` used the system zone; all now default to the system
  zone.
- **A validation rule no longer reports an error for a `null` property.** Combine with
  `should notBe nil()` to require presence.
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
- `Link`'s `require` checks ran in a secondary constructor and never fired on a deserialized link.
- `InMemoryCache` was an unbounded map keyed by request URL.
- Cache keys base64-encoded the full path and query, so their length was client-controlled. They are
  now hashed.

### Added

- `StatusPagesConfig.problemDetails { }` registers every handler in one call.
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
  resolves them. Exposed and gel-query-dsl remain optional — see the README.
- `ExpandSpec` collapsed from four near-duplicate field implementations to two; single-item
  expansion now delegates to the batched path, so the two can no longer diverge.
- ktlint and binary-compatibility-validator run as part of `build`.
- Test coverage went from one module to all six, with Kover gating at 85% line and 65% branch.
