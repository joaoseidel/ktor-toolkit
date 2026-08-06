---
name: expand
description: >-
  Expandable resources with ktor-toolkit-expander — declaring an ExpandSpec so a client can ask for
  ?expand=author or ?expand=author.books, serving a whole page's references in one batched query
  instead of one per row, and the Expandable wire contract (a bare id string while unresolved, a
  full object once expanded). Use when a response references another resource by id, when a client
  needs related data without a second round trip, when deciding which fields should be expandable,
  and whenever you see a loop that queries per row to fill in a related object. Covers ExpandSpec,
  field, listField, polymorphicField, batch, nested, field projection and the N+1 characteristics
  of nested expansion.
---

# Expandable resources

## What it is for, and when to say no

A `BookResponse` carries an author id. A client that wants author names for a page of 20 books
either makes 20 more requests or you invent `/books?includeAuthors=true`. Expansion is the general
version of that: the client names what it wants, the server resolves it in one batched query, and
the wire format says which it got.

**Ask before building it.** Expansion is a permanent contract and it is not free — every expandable
field is a batcher to write, a query to index, and a payload that varies by request. Before adding
it, ask:

- **Which fields do clients actually follow?** Usually one or two per resource. Making every
  reference expandable produces surface nobody uses.
- **How many distinct resources does a page touch?** One batch per expandable field per page is the
  design point. If a field's target is itself a large object, the payload grows fast.
- **How deep?** `?expand=author.books` is supported and costs meaningfully more than `?expand=author`
  — see the performance section, which is not a formality.
- **Is a dedicated endpoint clearer?** If a client always wants the expansion, an endpoint that
  returns the joined shape is simpler for everyone than an optional parameter that is never omitted.

A good answer is a short list. Two expandable fields on the resources clients actually navigate
beats twelve on everything.

## The wire contract

The field's type is `Expandable<T>`, and its JSON tells the client what it got:

```kotlin
@Serializable
data class BookResponse(
    val id: String,
    val title: String,
    val author: Expandable<AuthorResponse>,
)
```

| State | JSON | Means |
|---|---|---|
| `Ref(id)` | `"author": "auth-7"` | Not expanded — here is the id |
| `Resolved(value)` | `"author": { "id": "auth-7", "name": … }` | Expanded in full |
| `Partial(value, fields)` | `"author": { "name": … }` | Expanded, projected to requested fields |

A client can therefore branch on the JSON type rather than tracking what it asked for. Build the
response with `Expandable.Ref(book.authorId)` in the mapper; the spec replaces it if asked.

Two consequences of the serializer worth knowing: a `Ref` becomes a bare **string**, so ids must be
`String` at this boundary; and a `Partial` cannot round-trip — deserializing an object always yields
`Resolved`. That matters only if a service consumes its own responses.

## Declaring a spec

```kotlin
// -adapters/web/book/BookExpansion.kt
fun bookExpandSpec(authors: AuthorRepository, reviews: ReviewRepository) =
    ExpandSpec.build<BookResponse> {
        field(
            "author",
            get = { it.author },
            set = { copy(author = it) },
        ) {
            batch { ids, fields -> authors.findAllById(ids, fields).associateBy { it.id } }
        }

        listField(
            "reviews",
            get = { it.reviews },
            set = { copy(reviews = it) },
        ) {
            batch { ids, _ -> reviews.findAllById(ids).associateBy { it.id } }
        }
    }
```

`field` covers a single reference, including a nullable one. `listField` covers
`List<Expandable<F>>`. `polymorphicField` covers a field whose type varies per row, with one `case`
per discriminator and a separate batcher for each.

**`batch` is mandatory and is the whole point.** It receives *every* unresolved id on the page at
once and returns a map keyed by id. One call, one query — that is the N+1 this module exists to
prevent. Its second parameter is the projection (below); ignore it with `_` if the query cannot
narrow.

The builder rejects a duplicate field name and a missing `batch` at construction time, so a spec
that builds is wired correctly.

**The spec belongs in `-adapters/web`, and its batcher calls a port.** The spec is about the shape
of a response, so it is an adapter concern — but the data comes from `-core`'s repository
interfaces, not from Exposed directly. Build it where the dependencies are available and hand it to
the routes; `ktor-toolkit:architecture` has the layering.

## Applying it

For a single resource:

```kotlin
call.respond(bookSpec.apply(book.toResponse(), call.expand))
```

For a page, expand the content before wrapping it, since `PagedResponse` is the outermost shape:

```kotlin
val paged = findBooks(call.pagination)
val expanded = bookSpec.apply(paged.content.map { it.toResponse() }, call.expand)

call.respond(
    PagedResponse.from(Paged(paged.page, paged.sortBy, expanded, paged.totalElements)),
)
```

`apply` is `suspend` and does nothing when the client asked for nothing — an absent `?expand=`
leaves every field a `Ref` without touching a batcher.

Field names are matched case-insensitively and trimmed, so `?expand=Author, reviews` works.

## Nesting

A field can carry its own spec, declared inline or reused:

```kotlin
field("author", get = { it.author }, set = { copy(author = it) }) {
    batch { ids, fields -> authors.findAllById(ids, fields).associateBy { it.id } }

    nested {
        listField("books", get = { it.books }, set = { copy(books = it) }) {
            batch { ids, _ -> books.findAllById(ids).associateBy { it.id } }
        }
    }
}
```

`?expand=author.books` then resolves authors, and each author's books. `nested(existingSpec)` reuses
a spec you already built, which is how `author.books.author` stays finite and consistent.

## Projection

When a client names a field the nested spec does not know, the request is read as a **projection**
rather than a deeper expansion:

```
?expand=author.name
```

The `batch` lambda receives `setOf("name")` as its second parameter, so the query can select fewer
columns, and the response is filtered to those keys — that is the `Partial` state.

The rule the module applies: if every key under a field is a known nested field, it nests; if any
key is unknown, the whole set becomes a projection. So `?expand=author.name` projects, and
`?expand=author.books` nests, without the client having to say which it meant.

Honour the projection in the batcher when the query can narrow. Ignoring it is correct behaviour —
the response is filtered regardless — but it wastes the round trip the client was trying to make
cheaper.

## Performance: read this before nesting

**One level is batched per page.** `?expand=author` on a page of 20 books issues one call to the
author batcher with 20 ids. That is the design point and it holds.

**Nested levels are batched per parent, not per page.** `?expand=author.books` resolves the 20
authors in one call, then applies the nested spec to *each author individually* — 20 more calls, one
per author. The N+1 is gone at the top level and returns one level down.

This is worth planning around rather than discovering:

- Keep expansion **one level deep by default**. Add a second level when a client genuinely needs it,
  not because the DSL supports it.
- If a second level is required, make its batcher cheap — a `ktor-toolkit:cache` lookup inside the
  batcher collapses repeated fetches across parents, and is usually the smallest fix.
- Consider whether the nested data belongs in the parent's own query instead. A join that returns
  authors with their book ids already populated turns the nested batch into a no-op.
- Cap the depth you support in `ktor-toolkit:openapi` and mean it. `?expand=a.b.c.d` multiplies.

Also size the payload, not just the queries: `?expand=reviews` on a page of 50 with 100 reviews each
is a 5,000-object response that no pagination limit is protecting.

## Common mistakes

| Mistake | Why it hurts |
|---|---|
| A `map { repository.findById(it.authorId) }` in the route | The N+1 this module exists to remove |
| A `batch` that queries per id internally | Same N+1, now hidden behind the right-looking API |
| Making every reference expandable | Surface nobody uses, each with a batcher to maintain |
| Deep nesting because the DSL allows it | Per-parent batching multiplies with depth |
| Ignoring the `fields` parameter on a query that could narrow | The client asked for less and paid for everything |
| Non-`String` ids at the response boundary | A `Ref` serializes as a bare string; nothing else round-trips |
| Building the spec in `-core` | The domain starts owning response shape |
| Constructing the spec per request | Rebuilds the field list on every call for no benefit — build once, inject it |
| Expecting `Partial` to survive a round trip | Deserializing an object always yields `Resolved` |
