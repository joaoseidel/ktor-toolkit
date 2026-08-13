---
name: cache
description: >-
    Response caching with ktor-toolkit-cache — serving a route through withCache(namespace, cache),
    choosing between InMemoryCache and LettuceCache (Redis), picking a TTL, and invalidating after a
    write. Use when an endpoint is slow or its result is reusable across clients, when deciding who
    invalidates what, and whenever you see a ConcurrentHashMap used as a cache.
---

# Caching

## Ask first: which store

This is the first question, before any code, because it is the one that is expensive to change later — it decides whether invalidation is correct on
one node or on all of them.

| Option                     | Right when                                                                                                |
|----------------------------|-----------------------------------------------------------------------------------------------------------|
| **`InMemoryCache`**        | One instance serves the traffic, or the data is cheap to recompute and staleness across nodes is harmless |
| **`LettuceCache`** (Redis) | More than one instance serves the same traffic and a write on one must be visible to the others           |
| **Custom `KeyValueCache`** | You already run a different store, or entries need behaviour the two above do not have                    |

**If the user is unsure, ask one question: how many instances run in production?** More than one and the answer is Redis. With `InMemoryCache` behind
a load balancer, each instance holds its own copy and `invalidateNamespace` only clears the node that handled the write — the other nodes keep serving
stale data until their TTL expires. That is not a subtle bug, but it is invisible in development, where there is only ever one instance.

If the answer is genuinely one instance today and more later, `InMemoryCache` now is fine: both implement `KeyValueCache`, so the swap is a line in
the DI wiring. Say that out loud so the decision is recorded rather than forgotten.

## What the module caches, and what it does not

`withCache` caches **an HTTP response, keyed by the request**. It is read-through: on a miss it runs your block, stores the result, and returns it.

```kotlin
get {
    val books = call.request.withCache("books", cache) {
        findBooks(call.pagination)
    }
    call.respond(PagedResponse.from(books) { it.toResponse() })
}
```

There is no write-through, no automatic invalidation and no warming. Those are deliberate omissions — each depends on knowing what a write touches,
which only your code knows. The sections below cover how to do each one.

**A cache failure is not a request failure.** Redis being down is logged at WARN and the request is served from the origin. You never need a
`try/catch` around `withCache`. Coroutine cancellation is the one thing that still propagates, so a client that disconnects does not leave work
running.

## The key

The key is the request path plus its query parameters, sorted by name and by value, hashed with SHA-256, and prefixed with the namespace in the clear:

```
books.mQ4v8s2R…
```

Sorting means `?a=1&b=2` and `?b=2&a=1` are one entry. The namespace stays readable so
`invalidateNamespace` can prefix-match it.

**Exclude parameters that do not change the response**, or every distinct trace id becomes its own entry and the hit rate collapses:

```kotlin
call.request.withCache("books", cache, excludeQueryKeys = setOf("traceId", "requestId")) { … }
```

Two things the key does **not** include, both of which will bite:

**Headers.** A response that varies by `Accept-Language`, tenant header or auth scope will be served to the wrong client. If a response depends on who
is asking, either do not cache it or put the distinguishing value in the namespace: `withCache("books.$tenantId", cache) { … }`.

**Path parameters are included** — they are part of `request.path()` — so `/books/1` and `/books/2`
are separate entries, as you would expect.

Choose namespaces per resource, not per endpoint. Every entry a write can invalidate should share one, since `invalidateNamespace` is the cheap tool
and it works on the prefix.

## Serialization

`withCache` serializes with `Json.Default`, **not** your ContentNegotiation `Json`. If your responses depend on custom configuration — a naming
strategy, `explicitNulls`, polymorphic modules — pass the same instance, or what comes back from the cache will not match what a miss produces:

```kotlin
call.request.withCache("books", cache, json = appJson) { … }
```

The cached type must be `@Serializable`. Cache the domain result rather than the framework wrapper where you can: `Paged<Book>` round-trips cleanly,
and it leaves the response mapping outside the cache so a change to `toResponse()` does not require an invalidation.

## TTL

TTL is a property of the store, not of a call site:

```kotlin
InMemoryCache(maxSize = 1_000, ttl = 5.minutes)
LettuceCache(connection.async(), ttl = 5.minutes)
```

**Open the Redis connection with `LettuceCache.Codec`.** The cache stores raw bytes, so its constructor takes
`RedisClusterAsyncCommands<String, ByteArray>`. A plain `connect()` gives you a `<String, String>` connection that does not fit, and the compile error
names two Lettuce generics rather than the mistake:

```kotlin
val connection = RedisClient.create("redis://localhost:6379").connect(LettuceCache.Codec)
val cache = LettuceCache(connection.async(), ttl = 5.minutes)
```

Both objects are built once in `-app` and registered with a `cleanup` so the connection closes on shutdown — load the `ktor-toolkit:di` skill.

`InMemoryCache` defaults to `maxSize = 1_000` and no expiry; it is LRU-bounded, so entries leave when the bound is hit. `LettuceCache` lets Redis
apply the expiry itself.

Two caches with different TTLs is a legitimate configuration — a five-second cache for a hot list and an hour for a rarely-changing reference table
are different objects, injected separately. Do not stretch one TTL to cover both.

**Pick a TTL you could defend to a user who saw stale data.** "Up to five minutes out of date" is a product decision, not a technical one — ask rather
than assume. And set one even when invalidation is thorough: it is the backstop for the invalidation you forgot.

## Invalidation

Two tools, and a strong preference between them:

```kotlin
cache.invalidateNamespace("books")   // every entry under the namespace
cache.invalidateContaining(bookId)   // entries whose payload mentions this id
```

**Reach for `invalidateNamespace` first.** It asks the store for the keys under the prefix and deletes them in parallel — against Redis the prefix
goes into `SCAN MATCH`, so the other namespaces' keys never cross the wire.

`invalidateContaining` reads **every key and every value** in the store and does a substring match on the serialized payload. It exists for the case
where an entity appears in entries whose keys do not name it — a book inside a cached author response — and it is honest about its cost: fine for a
modest in-memory cache, expensive against Redis, where `keys()` walks the keyspace with `SCAN`. Keep both off the request path; they belong on writes.

Because it is a substring match, it is also approximate: an id that happens to appear inside another field's text will evict an unrelated entry. That
is a wasted recompute rather than a correctness problem, but it is a reason to prefer namespaces.

### Where invalidation goes

**In the route, immediately after the write returns.** The cache is keyed by HTTP requests and namespaced by a string only the web adapter knows, so
the web adapter is what invalidates it:

```kotlin
post {
    val book = createBook(call.receive<CreateBookRequest>().toDomain())
    cache.invalidateNamespace("books")
    call.respond(HttpStatusCode.Created, book.toResponse())
}
```

`-core` must not know a cache exists. A use case that invalidates has taken on an infrastructure concern and can no longer be tested without one.

Invalidate **after** the write succeeds — an exception from the use case skips it, which is what you want. And invalidate every namespace the write
affects: creating a book may need `books` and
`authors` cleared, and missing the second is the classic stale-data bug.

## Read-through, write-through, and warming

**Read-through** is what `withCache` does; you get it for free.

**Write-through** — updating the cache as part of the write rather than dropping the entry — is not provided at the response layer, and mostly should
not be: a response body depends on the request that produced it, so there is nothing to write through to. If you genuinely need it, it belongs one
layer down, as a decorating repository in `-adapters`:

```kotlin
class CachedBookRepository(
    private val delegate: BookRepository,
    private val cache: KeyValueCache,
) : BookRepository { … }
```

That decorator implements the domain port, so `-core` is unchanged and the DI wiring picks which one to provide. This is also where a **data cache**
belongs, as opposed to a response cache — reach for it when several endpoints need the same expensive lookup, rather than caching each response
separately.

**Warming** is awkward at the response layer, and it is worth knowing why rather than fighting it:
the key is derived from an `ApplicationRequest`, so there is no request to build a key from at startup. The practical options are to issue real
requests against the instance after it starts, or to warm the decorating repository instead, where keys are yours to choose. If warming matters, that
is a reason to cache at the data layer.

## Common mistakes

| Mistake                                                    | Why it hurts                                                                                                 |
|------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `ConcurrentHashMap` as a cache                             | No bound, no TTL, no invalidation across nodes; grows until the heap does                                    |
| `RedisClient.connect()` without `LettuceCache.Codec`       | The connection is `<String, String>` and will not fit the constructor                                        |
| `InMemoryCache` with several instances running             | Each node caches and invalidates its own copy; stale reads that only appear in production                    |
| Caching a response that varies by header or caller         | Headers are not in the key — one client's data is served to another                                          |
| Trace ids left in the key                                  | Every request is a miss, and the cache is pure overhead                                                      |
| `invalidateContaining` on the read path                    | Reads and deserializes the entire store per request                                                          |
| Invalidation inside a use case                             | `-core` grows an infrastructure dependency and becomes untestable without one                                |
| Invalidating before the write                              | An exception leaves the cache cleared but the data unchanged — merely wasteful, unless it hid a real failure |
| Forgetting a second affected namespace                     | The classic stale-data bug; the endpoint you tested looks fine                                               |
| Different `Json` in `withCache` than in ContentNegotiation | Hits and misses produce different payloads                                                                   |
| No TTL because invalidation is "complete"                  | It never is; TTL is the backstop                                                                             |
| `try/catch` around `withCache`                             | Already handled — failures fall back to the origin                                                           |
