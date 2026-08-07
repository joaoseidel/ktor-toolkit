---
name: hateoas
description: >-
  Hypermedia links with ktor-toolkit-hateoas — wrapping a response in a Resource so it carries a
  _links array, generating self/next/prev/first/last for a paged collection with toResource(call),
  and declaring a resource's own links with the resource { link(…) } builder. Use whenever a
  response should advertise navigation or available actions, whenever a collection endpoint needs
  page links, whenever you see a next-page URL being built by string concatenation, and when
  choosing relation names or deciding which links to show for a given resource state. Covers Link,
  Resource, LinksBuilder, createPaginationLinks and the flattened _links wire format.
---

# HATEOAS

## What links are actually for

The pitch for hypermedia is usually discovery — a client that explores an API by following links. That client rarely exists, and building for it is
how `_links` blocks turn into payload nobody reads.

The value that does hold up is narrower and worth the bytes: **a link is a statement about state and permission that the client would otherwise have
to infer.** A draft book carries a `publish`
link; a published one does not. A caller who may not delete a book never sees `delete`. The client renders buttons from the links it got instead of
reimplementing your rules in its own language and drifting from them.

That is the test for adding a link: would the client otherwise have to compute this? Pagination passes — the client would have to know the page
arithmetic. A `self` link on an object the client just fetched by URL usually does not.

## The wire format: content is flattened

This surprises people, so get it right before writing any of it. `Resource<T>` does **not** nest your payload under a `content` key. It serializes `T`
's fields directly into the object and appends
`_links` beside them:

```kotlin
call.respond(
    resource(book.toResponse()) {
        link("self", "/books/${book.id}")
    },
)
```

```json
{
  "id": "42",
  "title": "The Hobbit",
  "_links": [
    { "rel": "self", "href": "/books/42", "method": "GET" }
  ]
}
```

Two consequences follow directly from that flattening, and both are runtime failures rather than compile errors:

**The content must serialize to a JSON object.** The serializer reads `T`'s output as a
`JsonObject`, so `resource(listOf(a, b))` or `resource("some string")` throws when the response is written — a 500 from a route that compiled fine.
Wrap a collection in `PagedResponse` (below) or in a small object of your own; never hand a bare list to `resource`.

**A field named `_links` on your DTO will be overwritten.** The links are added last. This is rare and obvious once seen, but it fails silently rather
than loudly.

`Resource` also requires JSON content negotiation — it casts to a `JsonEncoder`. That is a safe assumption for these services, but it does mean a CBOR
or protobuf negotiation path will not work.

## Paged collections

For a page, the links are entirely derivable, so the module derives them:

```kotlin
get {
    val paged = findBooks(call.pagination)
    val response = PagedResponse.from(paged) { it.toResponse() }
    call.respond(response.toResource(call))
}
```

```json
{
  "metadata": { "page": 1, "pageSize": 10, "totalPages": 3, "hasNext": true },
  "content": [ … ],
  "_links": [
    { "rel": "self",  "href": "/books?page=1&pageSize=10", "method": "GET" },
    { "rel": "next",  "href": "/books?page=2&pageSize=10", "method": "GET" },
    { "rel": "prev",  "href": "/books?page=0&pageSize=10", "method": "GET" },
    { "rel": "first", "href": "/books?page=0&pageSize=10", "method": "GET" },
    { "rel": "last",  "href": "/books?page=2&pageSize=10", "method": "GET" }
  ]
}
```

**Only links that point at a page that exists are emitted.** On the first page there is no `prev`
or `first`; on the last there is no `next` or `last`; a single-page result carries `self` alone. A client can therefore treat "is there a `next`
link?" as the answer to "is there a next page?" — do not undermine that by adding one unconditionally.

**Every other query parameter is carried over, percent-encoded.** A request for
`/books?q=tolkien&expand=author&page=1` produces page links that keep `q` and `expand`, so following
`next` does not silently drop the filter. Only `page` and `pageSize` are replaced.

Publish extra links after the generated ones with the second argument:

```kotlin
call.respond(response.toResource(call, listOf(Link("create", "/books", HttpMethod.Post))))
```

## Single resources, and conditional links

`resource { }` is where the interesting part lives, because here the links depend on domain state:

```kotlin
// -adapters/web/book/BookLinks.kt
fun Resource<BookResponse>.withBookLinks(book: Book): Resource<BookResponse> =
    withLinks(
        buildList {
            add(Link("self", "/books/${book.id}"))
            if (book.isDraft) add(Link("publish", "/books/${book.id}/publish", HttpMethod.Post))
            if (book.isDraft) add(Link("delete", "/books/${book.id}", HttpMethod.Delete))
        },
    )
```

or inline, when there are only a couple:

```kotlin
call.respond(
    resource(book.toResponse()) {
        link("self", "/books/${book.id}")
        if (book.isDraft) link("publish", "/books/${book.id}/publish", HttpMethod.Post)
    },
)
```

**The condition reads domain state; the adapter owns the URL.** `book.isDraft` is a property of the entity and belongs in `-core`. The path
`/books/{id}/publish` is a routing decision and belongs in
`-adapters/web`. Never invert that: a domain that knows its own URLs has become a web layer, and the `ktor-toolkit:architecture` skill explains what
that costs — load it.

Keep link building in its own file next to the response DTO — `BookLinks.kt` beside
`BookResponse.kt`. Route bodies stay readable, and the rules for a resource stay in one place instead of being re-derived in each of the three
endpoints that return it.

## Relation names

`rel` is the contract. Clients switch on it, so it is as breaking to rename as a field.

Use the standard IANA names where one fits, because clients and tooling already understand them:
`self`, `next`, `prev`, `first`, `last`, `up`, `related`, `collection`, `item`, `edit`. The pagination links already use the first five.

For anything else, name the **action or the relationship**, in lowercase, hyphenated if it needs two words: `publish`, `cancel`, `reviews`,
`cover-image`. Not `bookPublishLink`, not `POST_publish`, not a full URL template. The method already lives in the link, so `rel` does not need to
repeat it.

One name means one thing across the whole API. If `related` means something different on books than on authors, clients cannot write shared handling,
and the links stop being worth their bytes.

## A trap worth knowing

`Link` rejects a blank `rel` or `href` with `IllegalArgumentException`, in the constructor, so it also guards values arriving from deserialization.
Nothing in `problemDetails { }` maps that type by default, so a link built from an id that turned out to be empty answers **500**.

That is usually correct — it is a server bug, not a client one. But it is the same unmapped
`IllegalArgumentException` that the `ktor-toolkit:pagination` skill maps to 400 for bad sort keys. If you add that mapping, be aware it will also
catch this, and turn a genuine server fault into a 400. Prefer mapping a narrower exception, or keep the two concerns apart.

## Common mistakes

| Mistake                                          | Why it hurts                                                                      |
|--------------------------------------------------|-----------------------------------------------------------------------------------|
| `"/books?page=${page + 1}"` built by hand        | Drops the other query parameters and skips percent-encoding                       |
| Adding `next` unconditionally                    | Clients can no longer use its presence to detect the last page                    |
| `resource(listOf(…))`                            | Content is not a JSON object; serialization throws at response time               |
| Expecting the payload under a `content` key      | `Resource` flattens; only `PagedResponse` has a `content` field of its own        |
| Links built inside the route body, per endpoint  | The same resource advertises different links depending on which route returned it |
| Domain entity exposing its own URL               | `-core` starts owning routing decisions                                           |
| `rel` naming the method (`post-publish`)         | Duplicates `method`, and breaks when the method changes                           |
| `_links` on a resource with no conditional state | Payload that no client reads                                                      |
