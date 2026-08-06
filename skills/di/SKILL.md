---
name: di
description: >-
  Dependency injection with Ktor's native DI plugin — registering with dependencies { provide<Port>
  { Impl(resolve()) } }, consuming with `by dependencies`, organising registrations per feature in
  -app, lifetimes, named dependencies, shutdown cleanup and overriding in tests. Use whenever a new
  use case, repository, adapter or client needs wiring, when a route needs a dependency, when
  deciding where an object is constructed, and when a dependency fails to resolve or resolves
  ambiguously. No Koin, no Dagger, no third-party container.
---

# Dependency injection

## Ktor's own, and nothing else

Ktor has a DI plugin. Use it. No Koin, no Dagger, no service locator, no `object` singletons holding
state.

The reason is not novelty — it is that the graph is the one thing `-app` exists to own. A
third-party container adds annotations to classes in `-core` and `-adapters` that only the container
understands, which is precisely the framework coupling those modules are structured to avoid. With
Ktor's DI, a repository is a class with a constructor, and the only file that knows how it is built
is the one whose job that is.

## Register

```kotlin
// -app/config/Dependencies.kt
internal fun Application.registerBookDependencies() {
    dependencies {
        provide<BookRepository> { ExposedBookRepository(resolve()) }
        provide<SearchIndexPort> { OpenSearchIndexAdapter(resolve(), resolve()) }

        provide<FindBooks> { FindBooks(resolve()) }
        provide<CreateBook> { CreateBook(resolve(), resolve()) }
    }
}
```

Three conventions, all load-bearing:

**Register against the interface, construct the implementation.** `provide<BookRepository> {
ExposedBookRepository(…) }` is what makes the port substitutable — that is the whole point of
declaring one. `provide<ExposedBookRepository> { … }` throws the inversion away and every consumer
now names the technology.

**`resolve()` for constructor arguments.** The type is inferred from the parameter position, so the
call reads as "whatever this argument needs". Registration order does not matter: providers are lazy,
so a `resolve()` for something registered later works fine.

**Use cases are registered too.** They have dependencies and they are constructed once; there is no
reason for them to be the one thing built by hand at a call site.

Objects created outside the graph — a database connection, a configured `Json`, a coroutine scope —
are registered by value:

```kotlin
dependencies {
    provide<DataSource> { dataSource }
    provide<Json> { json }
    provide<CoroutineScope> {
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("catalog-background"))
    }
}
```

## Organise by feature

One `register…Dependencies()` per feature area, all in `-app/config/Dependencies.kt`, called in one
place:

```kotlin
fun Application.module() {
    val settings = loadRuntimeSettings()

    installContentNegotiation(json)
    installRequestTracing()
    installValidationAndErrorHandling()

    dependencies {
        provide<DataSource> { dataSource }
        provide<Json> { json }
    }

    registerBookDependencies()
    registerAuthorDependencies(settings)
    registerSearchDependencies(settings)

    configureRouting()
}
```

The functions are `internal`: they are wiring for this deployable, not API. A single
`dependencies { }` block listing eighty registrations is unreadable and turns every feature branch
into the same merge conflict.

**Configuration enters as a parameter, not through the graph.** `registerSearchDependencies(settings)`
is clearer than resolving a settings object inside each provider, and it keeps the read of
`application.yaml` in one place. See `ktor-toolkit:architecture`.

## Consume

In a route, take what you need at the top of the route function:

```kotlin
// -adapters/web/book/BookRoutes.kt
fun Route.bookRoutes() {
    val findBooks: FindBooks by application.dependencies
    val createBook: CreateBook by application.dependencies

    route("/books") {
        get { … }
        post { … }
    }
}
```

`by dependencies` on an `Application`, `by application.dependencies` inside a `Route` extension. The
delegate resolves on first access, so declaring it costs nothing until the route is built.

**Resolve at the top, not inside the handler.** A `resolve()` per request repeats work the container
already cached and buries the dependency where a reader will not look for it.

**Only `-app` and route functions resolve.** A use case never reaches into the container for a
collaborator; it takes constructor parameters. A repository never resolves another repository. Once
domain or adapter code can pull arbitrary objects out of a container, the constructor stops telling
the truth about what a class needs, and nothing can be constructed in a test without the container.

## Lifetimes

**Everything is an application-scoped singleton, created lazily on first resolution and cached.**
There is one `FindBooks` for the life of the process, and one `ExposedBookRepository` behind it.

Two consequences worth being deliberate about:

**Registered objects must be safe to share.** Requests are served concurrently on many threads, so a
use case or repository holding mutable state is a race, not a cache. Keep them stateless; put
per-request state in parameters and local variables.

**There is no request scope.** The container cannot give you "the current user" or "this request's
tenant" — nor should it. Read it from the call in the route and pass it inward as a parameter, which
is also what makes the use case testable without a request.

## Two implementations of one type

When one interface has two live implementations, name them:

```kotlin
dependencies {
    provide<ObjectStore>("primary") { S3ObjectStore(resolve()) }
    provide<ObjectStore>("archive") { GlacierObjectStore(resolve()) }

    provide<BackupService> { BackupService(resolve("primary"), resolve("archive")) }
}
```

```kotlin
val store: ObjectStore by application.dependencies.named("primary")
```

Prefer distinct types where the distinction is real — `PrimaryObjectStore` and `ArchiveObjectStore`
as separate interfaces say more than two strings, and the compiler checks them. Names are right when
the implementations are genuinely interchangeable and only the configuration differs.

## Shutdown

For anything holding a resource — a connection pool, an HTTP client, a background scope — register a
cleanup so it closes on shutdown rather than at process death:

```kotlin
dependencies {
    provide<HttpClient> { HttpClient(CIO) }
    cleanup<HttpClient> { it.close() }
}
```

Without it, a redeploy can drop in-flight work and leak connections for as long as the old process
lingers. It also matters for graceful shutdown behind a load balancer — see
`ktor-toolkit:container`.

## Overriding in a test

A test registers its own implementation before the module registers the real one, or configures the
plugin to let the later registration win:

```kotlin
testApplication {
    application {
        install(DI) { conflictPolicy = overridePrevious }

        module()

        dependencies {
            provide<BookRepository> { mockk<BookRepository>().also { … } }
        }
    }

    val response = client.get("/books")
}
```

For an `-adapters` route test, do not boot `module()` at all — register only the mock the route needs
and install only the plugins it uses. `ktor-toolkit:tests` covers the split between that and an
acceptance test.

## When resolution fails

The failures are distinct, and each says something different about the wiring:

| Failure | Means |
|---|---|
| Missing dependency | Nothing was registered for that type — usually a `register…Dependencies()` never called from `module()` |
| `AmbiguousDependencyException` | Two registrations match; name them, or narrow the type |
| `CircularDependencyException` | A depends on B depends on A — a design problem, not a wiring one |
| Conflict on registration | The same type registered twice; intentional in a test, a mistake in `module()` |

Resolution is lazy, so a missing registration surfaces on **first use**, not at startup. An endpoint
nobody exercised can be broken in a build that is otherwise green — which is one more reason for the
acceptance test in `ktor-toolkit:tests`.

A circular dependency is worth reading as a message about the design. Two use cases that need each
other usually share a third thing that has not been named yet.

## Common mistakes

| Mistake | Why it hurts |
|---|---|
| Adding Koin, Dagger or another container | Annotations from a framework land in `-core` and `-adapters` |
| `provide<ExposedBookRepository> { … }` | Registers the implementation; every consumer now names the technology |
| A use case resolving from the container | Its constructor stops describing what it needs, and it cannot be built in a test |
| `object Repository` holding state | Not injectable, not substitutable, and shared mutable state |
| One giant `dependencies { }` block | Unreadable, and every feature branch conflicts in the same place |
| Resolving inside a request handler | Repeats work per request and hides the dependency |
| Mutable state in a registered singleton | One instance serves every concurrent request |
| Expecting a request scope | There is none; pass request data as parameters |
| No `cleanup` for pooled resources | Connections leak across a redeploy |
| Reading `application.yaml` inside providers | Configuration reads scatter; pass settings in as parameters |
