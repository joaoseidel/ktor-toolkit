---
name: pagination
description: >-
  Paging and sorting with ktor-toolkit-paginator — reading ?page, ?pageSize and ?sortBy off the
  call, carrying Pagination through the port into the persistence adapter, and shaping the
  PagedResponse metadata that comes back. Use whenever an endpoint returns more than one of
  something, whenever a client can choose the order of results, and whenever you see hand-written
  page/limit/offset parsing, a bespoke {items, total, page} response, or a sort key going to the
  database unchecked. Covers Page, Sort, Pagination, Paged, PaginationRequest, PagedResponse, the
  sortBy { } DSL, Sort.toExposedQueryExpression and Sort.toMongoSortExpression.
---

# Pagination

## What the module already decided

Three query parameters, one shape, everywhere:

| Parameter | Default | Notes |
|---|---|---|
| `?page` | `0` | Zero-based. Negatives clamp to `0`. |
| `?pageSize` | `10` | Clamped to `1..100`. |
| `?sortBy` | none | `name,-createdAt` — a `-` prefix means descending. |

**Parsing never fails.** `?page=abc` falls back to the default, `?page=-3` becomes `0`, and
`?pageSize=5000` becomes `100`. That is a deliberate stance: paging parameters are navigation, not
data, and a client that fumbles one wants the first page rather than a 400 it has to handle. It also
means there is no error path to write in the route — do not add one.

Different bounds for one endpoint:

```kotlin
val pagination = call.paginationRequest(defaultPageSize = 20, maxPageSize = 200).toPagination()
```

## The five types, and where each lives

Getting these confused is the usual source of a layering mistake, so it is worth knowing which is
which before writing any of them. `ktor-toolkit:architecture` has the full rationale.

| Type | Module | What it is |
|---|---|---|
| `PaginationRequest` | `-adapters/web` | What the query string said. Knows about `Parameters`. |
| `Pagination` | `-core` | What the domain was asked for: a `Page` and a list of `Sort`. |
| `Page` | `-core` | `page` and `pageSize`, nothing else. |
| `Sort` | `-core` | A property name and a direction. |
| `Paged<T>` | `-core` | A slice of content plus `totalElements`. What a use case returns. |
| `PagedResponse<T>` | `-adapters/web` | The wire shape: `metadata` + `content`. |

The pairs matter. `PaginationRequest` → `Pagination` is the inbound boundary; `Paged` →
`PagedResponse` is the outbound one. Skip either and a layer starts knowing something it should not.

## The flow, end to end

### 1. The port — `-core`

```kotlin
interface BookRepository {
    suspend fun findAll(pagination: Pagination): List<Book>
    suspend fun count(): Long
}
```

The port takes `Pagination` whole. Do not flatten it to `findAll(page: Int, size: Int)` — that
throws away the sort criteria and forces every caller to reassemble what it already had.

`count()` is separate because the repository is the only thing that can answer it, and because some
endpoints will want to skip it (see below).

### 2. The use case — `-core`

```kotlin
class FindBooks(
    private val books: BookRepository,
) {
    suspend operator fun invoke(pagination: Pagination): Paged<Book> =
        Paged(
            page = pagination.page,
            sortBy = pagination.sortBy,
            content = books.findAll(pagination),
            totalElements = books.count(),
        )
}
```

`Paged` carries the request back out with the result. That is what lets the response layer compute
`totalPages` and `hasNext` without being told the page size a second time.

### 3. The persistence adapter — `-adapters/persistence`

```kotlin
class ExposedBookRepository : BookRepository {
    override suspend fun findAll(pagination: Pagination): List<Book> {
        val (page, pageSize) = pagination.page

        return Books
            .selectAll()
            .orderBy(*pagination.sortBy.toExposedQueryExpression(Books.title, Books.createdAt).toTypedArray())
            .limit(pageSize)
            .offset(page.toLong() * pageSize)
            .map { it.toBook() }
    }

    override suspend fun count(): Long = Books.selectAll().count()
}
```

`toExposedQueryExpression` takes an **explicit allow-list of sortable columns** — as a vararg, a
`List<Column<*>>`, or a whole `Table`. Anything outside it raises `IllegalArgumentException` before
a query is built. Name the columns rather than passing the table: `Books` exposes every column
including the ones you did not mean to make sortable, and an index only covers the ones you planned
for.

An empty `sortBy` produces an empty array, so `orderBy()` is a no-op and the query is unordered.
If the endpoint needs a stable default order, supply one — see below.

On MongoDB the adapter has the same shape, with `toMongoSortExpression`:

```kotlin
class MongoBookRepository(private val collection: MongoCollection<BookDocument>) : BookRepository {
    override suspend fun findAll(pagination: Pagination): List<Book> {
        val (page, pageSize) = pagination.page

        return collection
            .find()
            .sort(pagination.sortBy.toMongoSortExpression(BookDocument::title, BookDocument::createdAt))
            .skip(page * pageSize)
            .limit(pageSize)
            .map { it.toBook() }
            .toList()
    }

    override suspend fun count(): Long = collection.countDocuments()
}
```

Two differences from the Exposed conversion. The criteria collapse into **one** `Bson` document
rather than a list, because that is what `find().sort(...)` takes — no spread, no `toTypedArray()`;
an empty `sortBy` becomes `{}`, which MongoDB reads as unordered. And the allow-list is field names:
either `String`s, or property references of the **document** class, whose names must be the fields
as stored. Passing the domain entity's properties compiles and then silently stops matching the
moment the two diverge.

### 4. The route — `-adapters/web`

```kotlin
get {
    val paged = findBooks(call.pagination)
    call.respond(PagedResponse.from(paged) { it.toResponse() })
}
```

`PagedResponse.from` takes the mapper from entity to DTO, so the entity never reaches the wire. If
the content is already the response type, the mapper is optional: `PagedResponse.from(paged)`.

To attach `self`/`next`/`prev` links, one more call — `ktor-toolkit:hateoas` owns it:

```kotlin
call.respond(PagedResponse.from(paged) { it.toResponse() }.toResource(call))
```

### 5. Wiring — `-app`

Nothing pagination-specific. The use case is registered like any other; see `ktor-toolkit:di`.

## Sorting

### The default order

An unordered page is not stable: the same `?page=1` can return rows already seen on page 0. Give
every collection endpoint a default order, built from property references so a rename is a compile
error rather than a silently dead sort key:

```kotlin
val defaultOrder = sortBy {
    desc(Book::publishedAt)
    asc(Book::title)
}

val pagination = call.pagination.let {
    if (it.sortBy.isEmpty()) it.copy(sortBy = defaultOrder) else it
}
```

The DSL also accepts strings, for columns with no matching property. Prefer the property reference
whenever one exists — that is the whole reason the overload is there.

### The trap: an unknown sort key is a 500

Both conversions throw `IllegalArgumentException` for a property that is not in the allow-list.
Nothing in `problemDetails { }` maps that type, so it lands on the catch-all and the client gets
**500 for what is really a bad request**.

`?sortBy=titel` is a typo, not a server fault. Map it once, in `-adapters/web`:

```kotlin
on<IllegalArgumentException> {
    ProblemDetail.fromStatus(HttpStatusCode.BadRequest, it.message ?: "Invalid request")
}
```

Do this the first time an endpoint becomes sortable. See `ktor-toolkit:problem-details` for where
that block lives and how mappings resolve.

## The metadata block

`PagedResponse.from` computes the metadata; you never assemble it by hand.

```json
{
  "metadata": {
    "page": 0, "pageSize": 10,
    "totalPages": 3, "totalElements": 25,
    "hasNext": true, "hasPrevious": false,
    "isSorted": true,
    "sortCriteria": [{ "property": "title", "direction": "ASC" }]
  },
  "content": [ … ]
}
```

**`totalPages` is a count, not an index.** With `totalPages: 3`, the last page is `?page=2`. This is
the field clients most often get wrong, and it is worth stating in the API documentation
(`ktor-toolkit:openapi`) rather than leaving them to discover it.

`from` requires `pageSize > 0`. It cannot be violated through `call.pagination`, which clamps, but
it can be if you construct a `Page` yourself in a test or a background job.

## Counting

`totalElements` costs a second query, and on a large table `COUNT(*)` is not free. It is the right
default: clients need `totalPages` to render a pager, and one count per page request is cheap
compared to getting it wrong.

When it genuinely hurts — a feed, an admin export, a table in the tens of millions of rows — the
honest options are to cache the count (`ktor-toolkit:cache`, keyed on the filter, with a short TTL),
or to change the contract to cursor pagination. The toolkit does not do cursors; that is a real gap
worth naming rather than faking with a wrong `totalElements`.

Do not pass a fabricated total. Every downstream number in `metadata` is derived from it, so one
guess corrupts the whole block.

## Common mistakes

| Mistake | Why it hurts |
|---|---|
| `call.request.queryParameters["page"]?.toIntOrNull() ?: 0` | Re-decides clamping and defaults, differently from every other endpoint |
| `findAll(page: Int, size: Int)` on the port | Discards the sort criteria; every caller reassembles what it had |
| Use case returning `PagedResponse` | `-core` now depends on a web type to satisfy a wire shape |
| `toExposedQueryExpression(Books)` when only two columns should sort | Every column becomes sortable, including unindexed ones |
| `toMongoSortExpression(Book::…)` against the domain entity, not the document | Compiles, then stops matching the day the two names diverge |
| Sorting straight from the client string with no allow-list | A column name from the client reaches SQL |
| No default order | Pages overlap and drop rows between requests |
| Leaving `IllegalArgumentException` unmapped | A typo in `?sortBy` answers 500 |
| Hand-built `{ items, total, page }` response | Loses `hasNext`/`totalPages`, and no two endpoints agree |
| Treating `totalPages` as the last page index | Off-by-one at the end of every collection |
| `count()` inside the row-mapping loop | One count query per row |
