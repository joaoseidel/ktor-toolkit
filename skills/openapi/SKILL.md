---
name: openapi
description: >-
    API docs from KDoc above route handlers, served through Scalar — Tag / Path / Query / Body /
    Responses with fully-qualified types in brackets, the ktor { openApi { } } config, and the
    /docs.json + /scalar routes. Use when adding or changing any endpoint's public contract, and to
    document what the compiler cannot infer: ?page, ?sortBy, ?expand and the problem+json errors.
---

# OpenAPI

## When the project has no API docs

A service with no `/docs.json` and no `openApi { }` block is not doing anything wrong, and turning documentation on is not a silent side effect of
adding one endpoint: it publishes a specification of *every* route, including ones nobody meant to advertise, and it adds a compiler plugin to the
build.

**Offer it once.** Say what it adds — the plugin configuration, the two routes, and whether the reference should be exposed publicly or behind the
same auth as everything else — and wait. That last question matters more than it sounds: `/scalar` on a public listener is a map of your API for
anyone who asks.

Where docs are already generated some other way — a hand-written `openapi.yaml`, a gateway that owns the spec — write to that instead of standing up a
second source. Two specifications that disagree are worse than one that is out of date.

## Ktor generates it; you do not annotate it

Ktor's compiler plugin builds the specification from the routes themselves — the path, the method, the type passed to `call.respond`, the type read by
`call.receive` — and enriches it from a KDoc comment above the handler.

**Do not add an annotation library.** No `@Operation`, no `@ApiResponse`, no third-party Swagger DSL: they restate what the compiler already knows, go
stale on their own schedule, and turn a route handler into a wall of metadata. Documentation belongs in a comment where a reader already looks, and
everything derivable stays derived.

The escape hatch, for what a comment genuinely cannot express, is the experimental `describe { }` builder from `ktor-server-routing-openapi`.

## Setup

The compiler plugin comes with `io.ktor.plugin`. Apply it and configure it in the module that **owns the routes** — `-adapters`, not `-app`:

```kotlin
// catalog-adapters/build.gradle.kts
plugins {
    alias(libs.plugins.ktor)
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = true
    }
}
```

**Keep `onlyCommented = true`.** An endpoint then appears in the specification only when someone wrote a comment for it, making documentation a
deliberate act. Without it every route is published the moment it exists, half-built ones included, and the specification stops being a statement of
intent.

There is **no generation task to run.** The plugin participates in the normal build, so the spec follows the code on every compile — that is what
makes drift hard rather than merely discouraged.

Add `ktor-server-openapi` and `ktor-server-routing-openapi` to the version catalog; neither is there yet (load the `ktor-toolkit:gradle` skill).

## Serving it: Scalar, not Swagger UI

Two routes in `-adapters/web/docs/`: one that renders the document, one that serves the reference UI. Both call `.hide()`, so the documentation
endpoints stay out of their own documentation.

```kotlin
// -adapters/web/docs/DocsRoutes.kt
@OptIn(ExperimentalKtorApi::class)
fun Route.configureDocsRoutes() {
    val scalarUrl = application.environment.config.property("scalar.url").getString()
    val scalarProxy = application.environment.config.property("scalar.proxy").getString()

    get("/docs.json") {
        call.application.attributes.put(JsonSchemaAttributeKey, SnakeCaseJsonSchemaInference)

        val docs = OpenApiDoc(
            info = OpenApiInfo(
                title = "Catalog API",
                version = "1.0",
                description = "Books and authors.",
            ),
            tags = listOf(
                Tag(name = "books", description = "Book catalogue and lifecycle."),
                Tag(name = "authors", description = "Author records."),
            ),
        ) + call.application.routingRoot.descendants() +
            call.application.findSecuritySchemesOrRefs()

        call.respondText(Json.encodeToString(docs), ContentType.Application.Json)
    }.hide()

    get("/scalar") {
        call.respondText(
            """
            <!doctype html>
            <html>
              <head>
                <title>API Reference</title>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
              </head>
              <body>
                <div id="app"></div>
                <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                <script>
                  Scalar.createApiReference('#app', {
                    url: '$scalarUrl',
                    proxyUrl: '$scalarProxy',
                  })
                </script>
              </body>
            </html>
            """.trimIndent(),
            ContentType.Text.Html,
        )
    }.hide()
}
```

```yaml
scalar:
    url: "$SCALAR_URL:http://localhost:8080/docs.json"
    proxy: "$SCALAR_PROXY:"
```

**Scalar over Swagger UI**, and not as taste: one CDN script against a document you already serve means no bundled UI to keep in step with Ktor, no
static assets in the jar, and a URL that is configuration rather than a compiled-in path. It also reads better on nested schemas and multiple error
responses per operation, which is most of what these modules produce.

**Declare `tags` on the document, not only on routes** — that is what gives the reference a sensible left-hand nav. A `Tag:` matching no declared tag
still works, but arrives undescribed.

**Gate `/scalar` on configuration outside development.** The document itself is usually fine to expose; an interactive console issuing real writes
against production is not.

Load `@scalar/api-reference` unversioned so the reference tracks upstream without a release of your own. Where CDNs are forbidden, vendor the bundle
into the module's resources and serve it yourself; nothing else changes.

### Exclude the routes that are not part of the API

`descendants()` walks the whole routing tree, and `onlyCommented` does not cover all of it: that setting belongs to the compiler plugin, so it only
knows routes in the module the plugin compiled. Health probes registered by Cohort in `-app`, or anything else installed outside `-adapters`, carry no
metadata at all — neither commented nor marked uncommented — and land in the document as `"/health/live": { "get": {} }`: an operation with no
summary, no responses and no schema, telling a client only that an endpoint it must never call exists.

`.hide()` cannot reach them either, since nothing hands you the `Route` a plugin registered. Filter them where the document is assembled, next to the
`hide()` calls, so every rule about what the document contains stays in one file:

```kotlin
) +call.application.routingRoot.descendants().filterNot(Route::isOperational) +
    call.application.findSecuritySchemesOrRefs()

private fun Route.isOperational(): Boolean = toString().startsWith("/health")
```

### Match the schemas to your JSON naming

Wherever `JsonNamingStrategy.SnakeCase` is configured — including `problemDetails` — the **inferred schemas must be snake_case too**, or the reference
documents field names no client will ever see. Override the inference once:

```kotlin
private object SnakeCaseJsonSchemaInference : JsonSchemaInference {
    private val delegate = KotlinxSerializerJsonSchemaInference.Default

    override fun buildSchema(type: KType): JsonSchema = delegate.buildSchema(type).applySnakeCase()
    // …recursively rename properties, required, oneOf, anyOf, items, additionalProperties
}
```

and install it via `JsonSchemaAttributeKey` before building the document, as above. Forgetting this produces documentation that is subtly and
consistently wrong.

## The comment syntax

Keywords take the form `Keyword: value`. Plural forms — `Tags:`, `Responses:` — introduce a bullet list. The first line is the summary.

| Keyword         | Form                                  |
|-----------------|---------------------------------------|
| `Tag:`          | `name`                                |
| `OperationId:`  | `camelCaseId`                         |
| `Description:`  | `text`                                |
| `Path:`         | `[Type] name description`             |
| `Query:`        | `[Type] name description`             |
| `Header:`       | `[Type] name description`             |
| `Cookie:`       | `[Type] name description`             |
| `Body:`         | `contentType [Type] description`      |
| `Response:`     | `code contentType [Type] description` |
| `Security:`     | `scheme`                              |
| `Deprecated:`   | `reason`                              |
| `ExternalDocs:` | `href`                                |

Every `[Type]` is a KDoc link, and it resolves the way KDoc links resolve — against what the *file* can see. Write them fully qualified; see below.

The comment goes **immediately above the route call**, inside the route function:

```kotlin
fun Route.createBookRoute() {
    val createBook: CreateBook by application.dependencies

    /**
     * Register a new book
     * OperationId: createBook
     * Description: Adds a book to the catalogue. The ISBN must not already be registered.
     * Tag: books
     *
     * Body: application/json [com.example.catalog.adapters.web.book.CreateBookRequest] The book to create
     *
     * Responses:
     *  - 201 application/json [com.example.catalog.adapters.web.book.BookResponse] The created book.
     *  - 400 application/problem+json [com.github.joaoseidel.ktor.toolkit.problemdetails.data.ProblemDetail] Validation failed; `properties` names each field.
     *  - 409 application/problem+json [com.github.joaoseidel.ktor.toolkit.problemdetails.data.ProblemDetail] That ISBN is already registered.
     */
    post(BOOKS_ROUTE) {
        val book = createBook(call.receive<CreateBookRequest>().toDomain())
        call.respond(HttpStatusCode.Created, book.toResponse())
    }
}
```

Write the summary as what the endpoint *does for a caller* — "Register a new book", not "createBook".
`OperationId` is what client generators turn into a method name, so keep it stable: renaming one is a breaking change for every generated SDK.

### Always write the fully-qualified name in brackets

**Every `[Type]` in an OpenAPI comment is fully qualified.** Not "when ambiguous" — always, in `Path:`, `Query:`, `Header:`, `Cookie:`, `Body:` and
every `Response:` line. The one exception is Kotlin's own built-ins, `[Int]`, `[String]`, `[Boolean]`, which are in scope in every file and can never
resolve to anything else. A generic type is not an exception to this rule so much as excluded from bracket references altogether — see
[Never name a generic type in brackets](#never-name-a-generic-type-in-brackets), which is a harder failure than anything in this section.

The reason is resolution. `[ProblemDetail]` is a KDoc reference resolved against **the imports of the file the comment sits in**, not against the
classpath. A route file imports `CreateBookRequest` and `BookResponse`, so those resolve by luck; it never mentions `ProblemDetail` in code, because
the 400 is *thrown* and mapped by `problemDetails { }` (load the `ktor-toolkit:problem-details` skill). With no import there is nothing to resolve,
and the failure is silent — the operation is published, the response still lists `application/problem+json`, and the schema is simply gone. It reads
as documented while telling a client nothing about the shape it receives.

**Do not add an import to satisfy a comment.** It is unused, the next cleanup removes it, and the endpoint is silently un-documented. A
fully-qualified name resolves from anywhere and survives that.

The toolkit types you will name most often:

| Type              | Write                                                                  |
|-------------------|------------------------------------------------------------------------|
| `ProblemDetail`   | `com.github.joaoseidel.ktor.toolkit.problemdetails.data.ProblemDetail` |
| `Link`            | `com.github.joaoseidel.ktor.toolkit.hateoas.data.Link`                 |
| `ValidationError` | `com.github.joaoseidel.ktor.toolkit.validator.data.ValidationError`    |

`PagedResponse` and `Resource` are deliberately absent: both are generic, and naming either is the mistake the next section is about.

Your own DTOs get the same treatment — `[com.example.catalog.adapters.web.book.BookResponse]`, not `[BookResponse]`. The length is the price of a
reference that cannot quietly lose a schema, and it makes review mechanical: check for a dot, not for whether a simple name happened to be imported.

### Never name a generic type in brackets

`[com.github.joaoseidel.ktor.toolkit.paginator.web.PagedResponse]` on a collection route is the one reference that does not fail quietly. Inference
resolves it to `PagedResponse<T>`, asks kotlinx-serialization for a serializer, and gets a `SerializationException` — nothing supplies an element type
through a KDoc link. The comment path does not catch it, so the exception escapes and **`/docs.json` returns 500 for the entire API**. Not one missing
schema: no document at all, taking every other endpoint's documentation with it.

**No bracket reference ever names a type with type parameters** — `PagedResponse`, `Resource`, `Paged`, `Expandable` or your own. Write no `[Type]` on
that line and let the description carry it.

That costs less than it looks: the compiler already infers the success schema from `call.respond`, where the type *is* concrete. A route responding
`PagedResponse<BookResponse>` inside a `Resource` documents correctly with no reference at all:

```kotlin
 *  -200 application / json A page of books, ordered by `slug,id` unless sorted. `content` holds the rows.
```

If inference cannot reach it — the type is only thrown, or built somewhere the compiler cannot follow — the escape hatch is `describe { }`, which
takes a real Kotlin type and therefore a real element type:

```kotlin
get(BOOKS_ROUTE) { … }.describe {
    responses { OK { schema = jsonSchema<PagedResponse<BookResponse>>() } }
}
```

Reach for that only when inference has genuinely failed. Read the generated document before assuming it has.

## What is inferred, and what is not

| Inferred                      | From                                |
|-------------------------------|-------------------------------------|
| Path, method, path parameters | The route declaration               |
| Request body schema           | `call.receive<CreateBookRequest>()` |
| Success response schema       | `call.respond(…)`                   |

What it cannot see is the larger half, and all of it matters to a client:

**Query parameters the toolkit reads for you.** `call.pagination` and `call.expand` consume `?page`, `?pageSize`, `?sortBy` and `?expand` without
those strings appearing in your handler, so nothing infers them. A collection endpoint that skips them is undocumented in the way clients notice.

**Anything thrown rather than responded.** `HttpStatusException`, a domain exception mapped in `problemDetails { }`, a validation failure — none reach
a `call.respond` the compiler can read. Every non-200 you care about is a line you write.

**Constraints.** Validation rules, the sortable-column allow-list, the maximum page size: all enforced at runtime, none visible in a signature.

## Documenting a toolkit endpoint

Because the toolkit's parameters are invisible to inference, a paged collection needs this block — copy it and adjust the sortable fields:

```kotlin
/**
 * List books
 * OperationId: listBooks
 * Description: A page of books. `metadata.totalPages` is a count, so the last page is
 *   `totalPages - 1`. Navigation links are in `_links`.
 * Tag: books
 *
 * Query: page [Int] Zero-based page index. Defaults to 0.
 * Query: pageSize [Int] Items per page. Defaults to 10, capped at 100.
 * Query: sortBy [String] Comma-separated fields, `-` prefix for descending. Sortable: `title`, `createdAt`.
 * Query: expand [String] Comma-separated fields to embed. Supported: `author`.
 *
 * Responses:
 *  - 200 application/json A page of books. `content` holds the rows, `metadata` the counts.
 *  - 400 application/problem+json [com.github.joaoseidel.ktor.toolkit.problemdetails.data.ProblemDetail] An unknown sort field.
 */
```

`ProblemDetail` is fully qualified for the reason above: it is never named in the route file's code, because the 400 is thrown rather than responded,
so the simple name has nothing to resolve against.

The 200 carries **no** bracket reference, and that is not an omission. `PagedResponse` is generic, so naming it 500s the whole document; and the
page's schema needs no help, because `call.respond` hands inference the concrete `Resource<PagedResponse<BookResponse>>` and it reads the whole
envelope —
`content` as an array of `BookResponse`, `metadata`, and `_links` — straight off the serializer. Write the description and let inference do the
schema.

Three things there are the point of this skill:

**The defaults and the cap are stated.** They are enforced silently — `?pageSize=5000` returns 100 without complaint — so a client that does not read
them quietly gets less than it asked for and never learns why.

**The sortable fields are listed.** The allow-list is unguessable, and guessing wrong is a 400 (or a 500 if the mapping from the
`ktor-toolkit:pagination` skill was never added).

**`totalPages` is explained.** It is a count, not an index — the single most commonly misread field in the whole response.

For expansion, document the supported fields and the depth you actually support; load the `ktor-toolkit:expand` skill for why unbounded nesting is a
promise you do not want to make.

## Errors and examples

Document every status a client should handle, and say what causes it — a bare `400 Bad Request` tells a caller nothing they did not already know.
Always name `application/problem+json` on error responses, paired with
`[com.github.joaoseidel.ktor.toolkit.problemdetails.data.ProblemDetail]`: clients that branch on `Content-Type` need to know the error shape differs
from the success shape (load the `ktor-toolkit:problem-details` skill). That type is the single most common place the simple name fails to resolve,
because error responses are produced by the mapper rather than by the route.

`500` is worth one mention per API rather than one per route. Every endpoint can fail; repeating it adds noise without adding information.

For anything a client must construct — a `sortBy` value, an `expand` path, a body — put a realistic example in the description, in backticks.
`"title": "string"` teaches nothing; `"title": "The Hobbit"`
shows the shape and the spirit at once.

## Keeping it in sync

Generation removes most of the drift, but not the parts that are prose. Treat the comment as part of the endpoint's contract, changed in the same
commit as the code — load the `ktor-toolkit:commit` skill.

The reviewable question on any endpoint change: **if this shipped, would the comment now be a lie?**
A new validation rule, a widened sort allow-list, a newly mapped exception, a renamed field — each changes what a client must know, and none will fail
a build.

With `onlyCommented = true`, an endpoint with no comment is simply absent from the specification. That is the honest outcome: better a documented
subset than a published lie.

## Mistakes that break the document silently

The first two are the ones to check on every comment you write: neither fails the build, and the second takes the whole reference down.

| Mistake                                              | What it does                                                                  |
|------------------------------------------------------|-------------------------------------------------------------------------------|
| A simple name in `[]` — `[ProblemDetail]`            | Resolves only if the file imports it; otherwise the schema vanishes, unwarned |
| A generic type in `[]` — `[PagedResponse]`           | `T` is never substituted: `/docs.json` 500s and the whole API loses its docs  |
| Adding an import just to make a `[]` reference work  | An unused import; the next cleanup un-documents the endpoint                  |
| `ktor { openApi { } }` in `-app`                     | The plugin must run where the routes are compiled — nothing is generated      |
| Inferred schemas left camelCase with snake_case JSON | Every field name in the reference is one no client will ever see              |
| No `Query:` lines on a paged endpoint                | `?page`, `?pageSize`, `?sortBy` are invisible to inference and to clients     |
| Not listing the sortable fields                      | The allow-list is unguessable, and a wrong guess is an error                  |
| Documenting only the success case                    | Clients discover the error shape in production                                |
| Forgetting `.hide()` on the docs routes              | `/docs.json` and `/scalar` document themselves                                |
| Renaming an `OperationId` casually                   | Breaks the method name in every generated client                              |
| Scalar reachable in production                       | An interactive console for real writes                                        |
