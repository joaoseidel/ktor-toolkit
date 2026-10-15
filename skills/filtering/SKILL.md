---
name: filtering
description: >-
  Narrowing collections with ktor-toolkit-filter: reading ?filterBy=price:gte:1000,title:like:kotlin
  off the call, carrying List<Filter> through the port into the persistence adapter, and applying it
  through an allow-list with toExposedCondition or toMongoFilter. Use whenever a client should be able
  to narrow a list, and whenever you see ad-hoc ?status= / ?minPrice= parameters, a query parameter
  turned into a WHERE clause by hand, or a count that ignores the filters its page applied.
---

# Filtering

## What the module already decided

One query parameter, one syntax, everywhere:

```
?filterBy=price:gte:1000,title:like:kotlin
?filterBy=status:in:draft|published
?filterBy=subtitle:exists:false
?filterBy=price:gte:1000&filterBy=price:lte:5000
```

Each condition is `property:operator:value`, and every condition must hold. Conditions are separated by `,` or by repeating the parameter. The value
is everything after the second `:`, so `startsAt:eq:12:30` is fine; `\` makes the next character literal, so `title:eq:Hello\, world` is one
condition.

| Operator              | Means                                      | Value                   |
|-----------------------|--------------------------------------------|-------------------------|
| `eq` `notEq`          | Equal, not equal                           | one                     |
| `gt` `gte` `lt` `lte` | Ordering                                   | one                     |
| `like` `notLike`      | Contains the text, ignoring case           | one; text fields only   |
| `in` `notIn`          | Any of, none of                            | one or more, `\|`-split |
| `exists`              | Has a value (`true`) or has none (`false`) | `true` or `false`       |

`notEq`, `notLike` and `notIn` also match a row with no value. MongoDB behaves that way natively; the Exposed conversion adds `OR column IS NULL` on
a nullable column to agree with it, because SQL's `NULL <> x` alone would drop the row.

At most 10 conditions per request. Raise or lower it per endpoint with `call.filters(maxFilters = 20)`.

**Do not invent per-field parameters.** `?status=draft&minPrice=10` is a second syntax every client has to learn per endpoint, and each one is
parsing code you own. `?filterBy=status:eq:draft,price:gte:10` is the same request in the shape every other listing already speaks.

## Bad input is a 400, unlike paging

Paging never fails: a fumbled `?page` is navigation, and the first page is a fine answer. **A filter is data.** Dropping a malformed one would answer
with rows the client asked to exclude, and the client would never know. So every mistake raises `InvalidFilterException`:

| Mistake                                 | Raised by                    |
|-----------------------------------------|------------------------------|
| `price`, `price:gte`, `price:between:1` | `call.filters`, when parsing |
| More than `maxFilters` conditions       | `call.filters`               |
| A property outside the allow-list       | the persistence conversion   |
| `pages:eq:many`, `status:eq:LOST`       | the persistence conversion   |
| `like` on a field that is not text      | the persistence conversion   |

`InvalidFilterException` carries no HTTP status. Map it to 400, so the problem body names the field the client got wrong. It carries `property`
whenever parsing got that far:

```kotlin
on<InvalidFilterException> { failure ->
    ProblemDetail.fromStatus(
        HttpStatusCode.BadRequest,
        failure.message,
        properties = failure.property?.let { mapOf("filterBy" to it) },
    )
}
```

It belongs in the shared `commonProblems()` block beside the `InvalidSortException` mapping. Load the `ktor-toolkit:problem-details` skill.
Without it, every filter typo answers **500**. It extends `IllegalArgumentException`, but do not map that type instead: it would also turn a
failed `require` inside the service into a 400.

The messages are written for the client, so `pages:eq:many` answers `"many" is not a valid value for "pages".` They name only what the client typed,
never the columns the allow-list leaves out.

## The types, and where each lives

| Type                                  | Module                  | What it is                                              |
|---------------------------------------|-------------------------|---------------------------------------------------------|
| `Filter`                              | `-core`                 | A property name, an operator, and the operands as text. |
| `InvalidFilterException`              | `-core`                 | The client asked for something unanswerable.            |
| `FilterQuery`, `call.filters`         | `-adapters/web`         | Reads `?filterBy=`.                                     |
| `toExposedCondition`, `toMongoFilter` | `-adapters/persistence` | Applies the filters through an allow-list.              |
| `filterableFields { }`                | `-adapters/persistence` | MongoDB's typed allow-list, beside the document class.  |

A `Filter` carries names and text on purpose, like `Sort`: it crosses from the query string to the repository without either end depending on the
other's types, and nothing is trusted until the persistence adapter resolves it against an allow-list.

## The flow, end to end

### 1. The port: `-core`

```kotlin
interface BookRepository {
    suspend fun findAll(filters: List<Filter>, pagination: Pagination): List<Book>
    suspend fun count(filters: List<Filter>): Long
}
```

The port takes `List<Filter>` whole. Do not flatten it into `findAll(status: String?, minPrice: Int?)`: that is the per-field parameter mistake
again, one layer down, and every new filter becomes a signature change through three modules.

A listing whose filter is fixed by the server rather than chosen by the client, such as "the books on this shelf", is a different port method with a
typed parameter, `findByShelf(shelfId: ShelfId, pagination: Pagination)`. `List<Filter>` is for what the client narrows.

**`count` takes the same filters.** `totalElements` drives `totalPages`, `hasNext` and every link. Counting the unfiltered table reports pages no
client can ever reach. It is the most common filtering bug, and invisible until someone pages to the end.

### 2. The use case: `-core`

```kotlin
class FindBooks(
    private val books: BookRepository,
) {
    suspend operator fun invoke(filters: List<Filter>, pagination: Pagination): Paged<Book> =
        Paged(
            page = pagination.page,
            sortBy = pagination.sortBy,
            content = books.findAll(filters, pagination),
            totalElements = books.count(filters),
        )
}
```

A use case that must always narrow, for tenant scoping, soft deletes or "only published", adds its own `Filter` to the list rather than trusting the
client to send it. Better still, apply that rule inside the repository: a scope the client can see in `filterBy` reads as one it can remove.

### 3. The persistence adapter: `-adapters/persistence`

Exposed, from `ktor-toolkit-filter-exposed`, with an **explicit column allow-list**:

```kotlin
private val filterable = listOf(Books.title, Books.price, Books.status, Books.publishedAt)

override suspend fun findAll(filters: List<Filter>, pagination: Pagination): List<Book> =
    suspendTransaction(database, readOnly = true) {
        val (page, pageSize) = pagination.page

        Books
            .selectAll()
            .where(filters.toExposedCondition(filterable))
            .orderBy(*pagination.sortBy.toOrder().toTypedArray())
            .limit(pageSize)
            .offset(page.toLong() * pageSize)
            .map { it.toBook() }
    }

override suspend fun count(filters: List<Filter>): Long =
    suspendTransaction(database, readOnly = true) {
        Books.selectAll().where(filters.toExposedCondition(filterable)).count()
    }
```

`toOrder()` is the default-order-plus-tie-breaker helper from the `ktor-toolkit:pagination` skill. As with sorting, **a filter key is the column's
SQL name**: `?filterBy=list_price:gte:1000` against `decimal("list_price", 10, 2)`. List the keys in the API documentation.

Each value is typed by the column it targets: numbers for numeric columns, `true`/`false` for booleans, the constant's name for an enum, and whatever
the column itself parses otherwise. The allow-list *is* the schema. An empty list becomes `Op.TRUE`. Declare the allow-list once and use it in both
methods, so `findAll` and `count` cannot drift apart.

`toExposedCondition(Books)` accepts every column, including the ones nobody meant to expose and the ones no index covers. Name the columns.

MongoDB (`ktor-toolkit-filter-mongo`) has no schema, so its allow-list declares the types, next to the document class:

```kotlin
private val filterable = filterableFields {
    field(BookDocument::title)
    field(BookDocument::price)
    field(BookDocument::status)
    field<Instant>("published_at")
}

override suspend fun findAll(filters: List<Filter>, pagination: Pagination): List<Book> {
    val (page, pageSize) = pagination.page

    return collection
        .find(filters.toMongoFilter(filterable))
        .sort(pagination.sortBy.toMongoSortExpression(BookDocument::title, BookDocument::publishedAt))
        .skip(page * pageSize)
        .limit(pageSize)
        .map { it.toBook() }
        .toList()
}

override suspend fun count(filters: List<Filter>): Long = collection.countDocuments(filters.toMongoFilter(filterable))
```

`field` reads the type off the property, so it needs no reflection library. Supported: `String`, `Int`, `Long`, `Double`, `BigDecimal`, `Boolean`,
enums (by name), `kotlin.time.Instant`, `java.time.Instant`, `java.time.LocalDate` and `ObjectId`; anything else fails when the declaration is built,
at startup. As with sorting, point it at the **document** class: the field has to be named as it is stored.

### 4. The route: `-adapters/web`

```kotlin
get {
    val paged = findBooks(call.filters, call.pagination)
    call.respond(PagedResponse.from(paged) { it.toResponse() }.toResource(call))
}
```

Nothing else. The page links from `ktor-toolkit:hateoas` already carry `filterBy` over, so `next` stays filtered.

### 5. Documenting it

The filterable fields are as unguessable as the sortable ones. List them in the endpoint's OpenAPI comment. The `ktor-toolkit:openapi` skill has the
`Query: filterBy` line to copy.

## Indexes

Every field on an allow-list is a query a client can send. Before adding one, know which index serves it, or that the table is small enough not to
need one. `like` is a contains search: no B-tree index helps, so keep it to fields where a scan is acceptable, or give the field a trigram or text
index. Load the `ktor-toolkit:migrations` skill for that. A short allow-list of indexed fields beats a long one the database cannot answer quickly.

## Common mistakes

| Mistake                                                             | Why it hurts                                                          |
|---------------------------------------------------------------------|-----------------------------------------------------------------------|
| `?status=` / `?minPrice=` parameters per endpoint                   | A second syntax per endpoint, and parsing code you own                |
| `findAll(status: String?, minPrice: Int?)` on the port              | Every new filter is a signature change through three modules          |
| `count()` without the filters                                       | `totalPages` and `hasNext` describe rows no page will show            |
| `toExposedCondition(Books)` instead of named columns                | Every column becomes filterable, including unindexed and private ones |
| `filterableFields { field(Book::…) }` on the domain entity          | Compiles, then stops matching the day the stored name differs         |
| Catching `InvalidFilterException` and serving the unfiltered list   | The client gets rows it asked to exclude, and no signal               |
| No `InvalidFilterException` mapping                                 | A typo in `?filterBy` answers 500                                     |
| `count` and `findAll` declaring separate allow-lists                | The two drift, and the total stops matching the pages                 |
| A security scope sent by the client as a filter                     | What the client sends, the client can leave out                       |
| `like` on a large unindexed field                                   | A full scan per request, at the client's discretion                   |
