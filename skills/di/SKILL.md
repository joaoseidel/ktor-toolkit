---
name: di
description: >-
  Wiring with Ktor's native DI plugin: dependencies { provide<Port> { Impl(resolve()) } },
  consuming with `by dependencies`, registrations organised per feature in -app, lifetimes, shutdown
  cleanup and overriding in tests. Use whenever a use case, repository, adapter or client needs
  wiring, or a dependency fails to resolve. No Koin, no Dagger, no third-party container.
---

# Dependency injection

## Ktor's own, and nothing else

Ktor has a DI plugin. Use it. No Koin, no Dagger, no service locator, no `object` singletons holding state.

The reason is not novelty. It is that the graph is the one thing `-app` exists to own. A third-party container adds annotations to classes in `-core`
and `-adapters` that only the container understands, which is precisely the framework coupling those modules are structured to avoid. With Ktor's DI,
a repository is a class with a constructor, and the only file that knows how it is built is the one whose job that is.

## Register

```kotlin
// -app/plugin/Dependencies.kt
internal fun Application.registerBookDependencies() {
    dependencies {
        provide<BookRepository> { ExposedBookRepository(resolve()) }
        provide<SearchIndex> { OpenSearchIndex(resolve(), resolve()) }

        provide<FindBooks> { FindBooks(resolve()) }
        provide<FindBook> { FindBook(resolve()) }
        provide<CreateBook> { CreateBook(resolve(), resolve()) }
    }
}
```

Three conventions, all load-bearing:

**Register against the interface, construct the implementation.** `provide<BookRepository> { ExposedBookRepository(…) }` is what makes the port
substitutable, which is the whole point of declaring one. `provide<ExposedBookRepository> { … }` throws the inversion away and every consumer now
names the technology.

**`resolve()` for constructor arguments.** The type is inferred from the parameter position, so the call reads as "whatever this argument needs".
Registration order does not matter: providers are lazy, so a `resolve()` for something registered later works fine.

**Use cases are registered too.** They have dependencies and they are constructed once; there is no reason for them to be the one thing built by hand
at a call site.

Infrastructure is registered the same way: the pool, the Exposed `Database` on top of it, a configured `Json`:

```kotlin
dependencies {
    provide<HikariDataSource> { HikariDataSource(settings.database.toHikariConfig()) }
    provide<Database> { Database.connect(resolve<HikariDataSource>()) }
    provide<Json> { appJson }
}
```

Background work does not get a hand-made `CoroutineScope` registered beside it: launch it from the `Application`, which is a `CoroutineScope`
already and is cancelled on shutdown. A scope built from `SupervisorJob() + Dispatchers.Default` belongs to nothing, so nothing cancels it.

## Organise by feature

One `register…Dependencies()` per feature area, all in `-app/plugin/Dependencies.kt`, called in one place:

```kotlin
fun Application.module() {
    val settings = RuntimeSettings.from(environment.config)

    configureSerialization()
    configureLogging()
    configureStatusPages()
    configureValidation()

    registerInfrastructure(settings)
    registerBookDependencies()
    registerAuthorDependencies()
    registerSearchDependencies(settings.search)

    configureDatabase()
    configureRouting()
}
```

The functions are `internal`: they are wiring for this deployable, not API. A single `dependencies { }` block listing eighty registrations is
unreadable and turns every feature branch into the same merge conflict.

**Configuration enters as a parameter, not through the graph.** `registerSearchDependencies(settings.search)` is clearer than resolving a settings
object inside each provider, and it keeps the read of `application.yaml` in one place, parsed once into a typed `RuntimeSettings` that fails at
startup on a missing key. Load the `ktor-toolkit:architecture` skill.

## Consume

In a route, take what you need at the top of the route function:

```kotlin
// -adapters/web/book/BookRoutes.kt
fun Route.bookRoutes() {
    val findBooks: FindBooks by dependencies
    val createBook: CreateBook by dependencies

    route("/books") {
        get { … }
        post { … }
    }
}
```

`by dependencies` works on both an `Application` and a `Route`. The delegate resolves on first access, so declaring it costs nothing until the route
is built.

**A route resolves use cases**, plus the web-side infrastructure it genuinely owns, such as a response cache. Never a repository: a route that can
resolve `BookRepository` will call it, and the use case layer is bypassed one endpoint at a time.

**Resolve at the top, not inside the handler.** A `resolve()` per request repeats work the container already cached and buries the dependency where a
reader will not look for it.

**Only `-app` and route functions resolve.** A use case never reaches into the container for a collaborator; it takes constructor parameters. A
repository never resolves another repository. Once domain or adapter code can pull arbitrary objects out of a container, the constructor stops
telling the truth about what a class needs, and nothing can be constructed in a test without the container.

## Lifetimes

**Everything is an application-scoped singleton, created lazily on first resolution and cached.** There is one `FindBooks` for the life of the
process, and one `ExposedBookRepository` behind it.

Two consequences worth being deliberate about:

**Registered objects must be safe to share.** Requests are served concurrently on many threads, so a use case or repository holding mutable state is
a race, not a cache. Keep them stateless; put per-request state in parameters and local variables.

**There is no request scope.** The container cannot give you "the current user" or "this request's tenant", nor should it. Read it from the call in
the route and pass it inward as a parameter, which is also what makes the use case testable without a request.

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
val store: ObjectStore by dependencies.named("primary")
```

Prefer distinct types where the distinction is real. `PrimaryObjectStore` and `ArchiveObjectStore` as separate interfaces say more than two strings,
and the compiler checks them. Names are right when the implementations are genuinely interchangeable and only the configuration differs.

## Shutdown

**`AutoCloseable` registrations are closed for you** when the application stops: a `HikariDataSource`, a Ktor `HttpClient`, a Lettuce connection.
Anything that releases its resources some other way needs an explicit `cleanup`:

```kotlin
dependencies {
    provide<RedisClient> { RedisClient.create(settings.redis.url) }
    cleanup<RedisClient> { it.shutdown() }
}
```

Without it, a redeploy can drop in-flight work and leak connections for as long as the old process lingers. It also matters for graceful shutdown
behind a load balancer. Load the `ktor-toolkit:container` skill.

## Overriding in a test

Under `testApplication` the plugin ignores conflicts and keeps the **first** registration, so a test registers its substitute *before* calling
the module, and the module's own registration for that type is skipped:

```kotlin
testApplication {
    application {
        dependencies {
            provide<SearchIndex> { FakeSearchIndex() }
        }

        module()
    }

    val response = client.get("/books")
}
```

Order is the whole mechanism: a registration after `module()` is the one ignored. Where a test genuinely needs the later one to win, say so
explicitly with `install(DI) { conflictPolicy = OverridePrevious }`.

For an `-adapters` route test, do not boot `module()` at all. Register only the mock the route needs and install only the plugins it uses. Load the
`ktor-toolkit:tests` skill for the split between that and an acceptance test.

## When resolution fails

The failures are distinct, and each says something different about the wiring:

| Failure                        | Means                                                                                                  |
|--------------------------------|--------------------------------------------------------------------------------------------------------|
| Missing dependency             | Nothing was registered for that type, usually a `register…Dependencies()` never called from `module()` |
| `AmbiguousDependencyException` | Two registrations match; name them, or narrow the type                                                 |
| `CircularDependencyException`  | A depends on B depends on A: a design problem, not a wiring one                                        |
| Conflict on registration       | The same type registered twice; intentional in a test, a mistake in `module()`                         |

Resolution is lazy, so a missing registration surfaces on **first use**, not at startup. An endpoint nobody exercised can be broken in a build that
is otherwise green, which is one more reason for the acceptance test in the `ktor-toolkit:tests` skill.

A circular dependency is worth reading as a message about the design. Two use cases that need each other usually share a third thing that has not
been named yet.

## Common mistakes

| Mistake                                         | Why it hurts                                                                     |
|-------------------------------------------------|----------------------------------------------------------------------------------|
| Adding Koin, Dagger or another container        | Annotations from a framework land in `-core` and `-adapters`                     |
| `provide<ExposedBookRepository> { … }`          | Registers the implementation; every consumer now names the technology            |
| A use case resolving from the container         | Its constructor stops describing what it needs, and it cannot be built in a test |
| `object Repository` holding state               | Not injectable, not substitutable, and shared mutable state                      |
| One giant `dependencies { }` block              | Unreadable, and every feature branch conflicts in the same place                 |
| Resolving inside a request handler              | Repeats work per request and hides the dependency                                |
| Mutable state in a registered singleton         | One instance serves every concurrent request                                     |
| Expecting a request scope                       | There is none; pass request data as parameters                                   |
| No `cleanup` for a non-`AutoCloseable` resource | Its threads and connections outlive a redeploy                                   |
| A repository resolved in a route                | The route calls it, and the use case layer is bypassed one endpoint at a time    |
| A test override registered after `module()`     | Under `testApplication` the first registration wins; the override is ignored     |
| A hand-made `CoroutineScope` registered         | Belongs to nothing, so shutdown never cancels it; launch from `Application`      |
| Reading `application.yaml` inside providers     | Configuration reads scatter; pass settings in as parameters                      |
