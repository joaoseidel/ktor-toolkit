---
name: pagination
description: >-
  Paging and sorting with ktor-toolkit-paginator: reading ?page, ?pageSize and ?sortBy off the
  call, carrying Pagination through the port into the persistence adapter, and shaping the
  PagedResponse metadata. Use whenever an endpoint returns more than one of something, and whenever
  you see hand-written page/limit/offset parsing, a bespoke {items, total, page} response, or a sort
  key reaching the database unchecked.
---

# Pagination

## What the module already decided

Three query parameters, one shape, everywhere:

| Parameter   | Default | Notes                                                 |
|-------------|---------|-------------------------------------------------------|
| `?page`     | `0`     | Zero-based. Negatives clamp to `0`.                   |
| `?pageSize` | `10`    | Clamped to `1..100`.                                  |
| `?sortBy`   | none    | `title,-published_at`; a `-` prefix means descending. |

**Parsing never fails.** `?page=abc` falls back to the default, `?page=-3` becomes `0`, and `?pageSize=5000` becomes `100`. That is a deliberate
stance: paging parameters are navigation, not data, and a client that fumbles one wants the first page rather than a 400 it has to handle. It also
means there is no error path to write in the route. Do not add one.

Different bounds for one endpoint:

```kotlin
val pagination = call.paginationRequest(defaultPageSize = 20, maxPageSize = 200).toPagination()
```

## The five types, and where each lives

Getting these confused is the usual source of a layering mistake, so it is worth knowing which is which before writing any of them. The
`ktor-toolkit:architecture` skill has the full rationale. Load it.

| Type                | Module          | What it is                                                        |
|---------------------|-----------------|-------------------------------------------------------------------|
| `PaginationRequest` | `-adapters/web` | What the query string said. Knows about `Parameters`.             |
| `Pagination`        | `-core`         | What the domain was asked for: a `Page` and a list of `Sort`.     |
| `Page`              | `-core`         | `page` and `pageSize`, nothing else.                              |
| `Sort`              | `-core`         | A property name and a direction.                                  |
| `Paged<T>`          | `-core`         | A slice of content plus `totalElements`. What a use case returns. |
| `PagedResponse<T>`  | `-adapters/web` | The wire shape: `metadata` + `content`.                           |

The pairs matter. `PaginationRequest` → `Pagination` is the inbound boundary; `Paged` → `PagedResponse` is the outbound one. Skip either and a layer
starts knowing something it should not.

## The flow, end to end

### 1. The port: `-core`

```kotlin
interface BookRepository {
    suspend fun findAll(pagination: Pagination): List<Book>
    suspend fun count(): Long
}
```

The port takes `Pagination` whole. Do not flatten it to `findAll(page: Int, size: Int)`. That throws away the sort criteria and forces every caller
to reassemble what it already had.

`count()` is separate because the repository is the only thing that can answer it, and because some endpoints will want to skip it (see below).

A listing the client can narrow takes its filters through the same port, and **`count` takes them too**, because a total over the unfiltered table
makes every page number in `metadata` wrong:

```kotlin
interface BookRepository {
    suspend fun findAll(filters: List<Filter>, pagination: Pagination): List<Book>
    suspend fun count(filters: List<Filter>): Long
}
```

The `ktor-toolkit:filtering` skill owns `?filterBy=` end to end. Load it.

### 2. The use case: `-core`

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

`Paged` carries the request back out with the result. That is what lets the response layer compute `totalPages` and `hasNext` without being told
the page size a second time.

The use case exists even though it only delegates: the route calls it, never the repository, so the first rule this listing grows, such as "only
published books" or "hide what the caller may not see", lands in `-core` instead of the route body.

### 3. The persistence adapter: `-adapters/persistence`

```kotlin
class ExposedBookRepository(
    private val database: Database,
) : BookRepository {
    override suspend fun findAll(pagination: Pagination): List<Book> =
        suspendTransaction(database, readOnly = true) {
            val (page, pageSize) = pagination.page

            Books
                .selectAll()
                .orderBy(*pagination.sortBy.toOrder().toTypedArray())
                .limit(pageSize)
                .offset(page.toLong() * pageSize)
                .map { it.toBook() }
        }

    override suspend fun count(): Long =
        suspendTransaction(database, readOnly = true) { Books.selectAll().count() }
}

private val sortable = listOf(Books.title, Books.publishedAt)

private fun List<Sort>.toOrder(): List<Pair<Expression<*>, SortOrder>> =
    toExposedQueryExpression(sortable).ifEmpty { listOf(Books.publishedAt to SortOrder.DESC) } + (Books.id to SortOrder.ASC)
```

`toExposedQueryExpression` takes an **explicit allow-list of sortable columns**, as a vararg, a `List<Column<*>>` or a whole `Table`. Anything
outside it raises `InvalidSortException` before a query is built. Name the columns rather than passing the table: `Books` exposes every column
including the ones you did not mean to make sortable, and an index only covers the ones you planned for.

**A sort key is the column's SQL name.** The allow-list matches `Column.name`, so `Books.publishedAt = date("published_at")` is sorted by
`?sortBy=published_at`, not `publishedAt`. With snake_case columns and a snake_case JSON naming strategy, that is the name the client already reads
in the response. Keep the two aligned rather than translating between them, and list the keys in the API documentation.

`toOrder()` does the two things no client should have to ask for: a **default order** when none was requested, and a **unique tie-breaker** always.
Without the first the page is unordered; without the second, rows that tie on `published_at` can swap between requests, so one shows on two pages
and another on none. The repository owns both because only it knows which columns are unique and indexed.

Every method runs in its own `suspendTransaction` against the injected `Database`. Exposed throws without a transaction in context, and a repository
that relies on one being opened somewhere above it works until the first caller that did not.

On MongoDB the adapter has the same shape, with `toMongoSortExpression`:

```kotlin
class MongoBookRepository(
    private val collection: MongoCollection<BookDocument>,
) : BookRepository {
    override suspend fun findAll(pagination: Pagination): List<Book> {
        val (page, pageSize) = pagination.page
        val requested =
            if (pagination.sortBy.isEmpty()) {
                Sorts.descending(BookDocument::publishedAt.name)
            } else {
                pagination.sortBy.toMongoSortExpression(BookDocument::title, BookDocument::publishedAt)
            }

        return collection
            .find()
            .sort(Sorts.orderBy(requested, Sorts.ascending("_id")))
            .skip(page * pageSize)
            .limit(pageSize)
            .map { it.toBook() }
            .toList()
    }

    override suspend fun count(): Long = collection.countDocuments()
}
```

Two differences from the Exposed conversion. The criteria collapse into **one** `Bson` document rather than a list, because that is what
`find().sort(...)` takes, with no spread and no `toTypedArray()`; an empty `sortBy` becomes `{}`, which MongoDB reads as unordered. And the
allow-list is field names: either `String`s, or property references of the **document** class, whose names must be the fields as stored. Passing the
domain entity's properties compiles and then silently stops matching the moment the two diverge. The default order and the `_id` tie-breaker are
there for the same reasons as above.

### 4. The route: `-adapters/web`

```kotlin
get {
    val paged = findBooks(call.pagination)
    call.respond(PagedResponse.from(paged) { it.toResponse() })
}
```

`findBooks` was resolved once at the top of the route function, `val findBooks: FindBooks by dependencies`. Load the `ktor-toolkit:endpoint` skill
for the whole route.

`PagedResponse.from` takes the mapper from entity to DTO, so the entity never reaches the wire. If the content is already the response type, the
mapper is optional: `PagedResponse.from(paged)`.

To attach `self`/`next`/`prev` links, one more call. The `ktor-toolkit:hateoas` skill owns it, so load that too:

```kotlin
call.respond(PagedResponse.from(paged) { it.toResponse() }.toResource(call))
```

### 5. Wiring: `-app`

Nothing pagination-specific. The use case is registered like any other; load the `ktor-toolkit:di` skill.

## Sorting

### The default order

An unordered page is not stable: the same `?page=1` can return rows already seen on page 0. The repository supplies the default and the tie-breaker,
as `toOrder()` does above. Not the route, which would have to know column names, and not the client, who cannot be trusted to send one.

`metadata.isSorted` then reports what the *client* asked for, which is what it can act on; the default is documented rather than echoed.

`sortBy { }` builds a `List<Sort>` from property references, so a rename is a compile error rather than a silently dead key. Use it where the
property names *are* the stored names, as on a MongoDB document class, and in tests. Against Exposed, where keys are SQL column names, build the
default from the columns as above.

### The trap: an unknown sort key is a 500

Both conversions throw `InvalidSortException` for a property that is not in the allow-list. Nothing in `problemDetails { }` maps that type by
default, so it lands on the catch-all and the client gets **500 for what is really a bad request**.

`?sortBy=titel` is a typo, not a server fault. The `ktor-toolkit:problem-details` skill maps `InvalidSortException` to 400 once, in the shared
`commonProblems()` block, with the key under `sortBy`. Add it the first time an endpoint becomes sortable. The message names only the key the client
sent, never the columns the allow-list leaves out.

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

**`totalPages` is a count, not an index.** With `totalPages: 3`, the last page is `?page=2`. This is the field clients most often get wrong, and it
is worth stating in the API documentation (load the `ktor-toolkit:openapi` skill) rather than leaving them to discover it.

Field names follow the application's `ContentNegotiation` naming strategy like any other DTO. Under `JsonNamingStrategy.SnakeCase` the block reads
`page_size`, `total_pages` and so on. The query parameters do not change: they stay `page`, `pageSize` and `sortBy`.

`from` requires `pageSize > 0`. It cannot be violated through `call.pagination`, which clamps, but it can be if you construct a `Page` yourself in a
test or a background job.

## Counting

`totalElements` costs a second query, and on a large table `COUNT(*)` is not free. It is the right default: clients need `totalPages` to render a
pager, and one count per page request is cheap compared to getting it wrong.

When it genuinely hurts, as with a feed, an admin export or a table in the tens of millions of rows, the honest options are to cache the count, keyed
on the filter with a short TTL, or to change the contract to cursor pagination. The toolkit does not do cursors; that is a real gap worth naming
rather than faking with a wrong `totalElements`. The `ktor-toolkit:cache` skill covers the first.

Do not pass a fabricated total. Every downstream number in `metadata` is derived from it, so one guess corrupts the whole block.

## Common mistakes

| Mistake                                                                      | Why it hurts                                                            |
|------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| `call.request.queryParameters["page"]?.toIntOrNull() ?: 0`                   | Re-decides clamping and defaults, differently from every other endpoint |
| `findAll(page: Int, size: Int)` on the port                                  | Discards the sort criteria; every caller reassembles what it had        |
| Use case returning `PagedResponse`                                           | `-core` now depends on a web type to satisfy a wire shape               |
| `toExposedQueryExpression(Books)` when only two columns should sort          | Every column becomes sortable, including unindexed ones                 |
| `toMongoSortExpression(Book::…)` against the domain entity, not the document | Compiles, then stops matching the day the two names diverge             |
| Sorting straight from the client string with no allow-list                   | A column name from the client reaches SQL                               |
| No default order, or no unique tie-breaker                                   | Pages overlap and drop rows between requests                            |
| `sortBy { desc(Book::publishedAt) }` against an Exposed table                | Property name `publishedAt` never matches the column `published_at`     |
| A query outside `suspendTransaction`                                         | Throws without an ambient transaction, or joins one it does not own     |
| The route calling the repository instead of a use case                       | The next rule about what may be listed has nowhere to go but the route  |
| Leaving `InvalidSortException` unmapped                                      | A typo in `?sortBy` answers 500                                         |
| Hand-built `{ items, total, page }` response                                 | Loses `hasNext`/`totalPages`, and no two endpoints agree                |
| Treating `totalPages` as the last page index                                 | Off-by-one at the end of every collection                               |
| `count()` inside the row-mapping loop                                        | One count query per row                                                 |
| `count()` ignoring the filters `findAll` applied                             | `totalElements` counts rows no page will ever show                      |
