---
name: openapi
description: >-
  API documentation with Ktor's built-in comment-based OpenAPI generation, served through Scalar —
  KDoc blocks above route handlers using Tag / OperationId / Path / Query / Body / Responses, the
  ktor { openApi { } } compiler configuration, and a /docs.json + /scalar pair of routes. Use when
  adding or changing any endpoint's public contract, when a request asks to document an API or
  expose an API reference, and when documenting the query parameters the toolkit reads for you
  (?page, ?pageSize, ?sortBy, ?expand) or the problem+json errors it produces — none of which the
  compiler can infer. No annotation libraries, and Scalar rather than Swagger UI.
---

# OpenAPI

## Ktor generates it; you do not annotate it

Ktor's compiler plugin builds the specification from the routes themselves — the path, the method, the type passed to `call.respond`, the type read by
`call.receive` — and enriches it from a KDoc comment above the handler.

**Do not add an annotation library.** No `@Operation`, no `@ApiResponse`, no third-party Swagger DSL. They restate what the compiler already knows,
they go stale independently of the code, and they turn a route handler into a wall of metadata. The design here is that documentation sits in a
comment where a reader would look anyway, and everything derivable is derived.

If something genuinely cannot be expressed in a comment, the escape hatch is the runtime
`describe { }` builder from `ktor-server-routing-openapi` — still Ktor, no new dependency worth the name. It is experimental; reach for it only when a
comment cannot say what you need.

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

`onlyCommented = true` is the house setting: an endpoint appears in the specification when someone wrote a comment for it. Without it every route is
published the moment it exists, including the ones that are half-built, and the specification stops being a statement of intent. Documenting an
endpoint becomes a deliberate act.

There is **no generation task to run.** The plugin participates in the normal build, so the spec follows the code on every compile — that is what
makes drift hard rather than merely discouraged.

Add `ktor-server-openapi` and `ktor-server-routing-openapi` to the version catalog; neither is there yet (`ktor-toolkit:gradle`).

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

**Scalar over Swagger UI**, and not only as taste: the reference is a single CDN script against a document you already serve, so there is no bundled
UI to keep in step with the Ktor version, no static assets in the jar, and the URL is configuration rather than a compiled-in path. It also reads
better on the two things this toolkit produces a lot of — nested schemas and multiple error responses per operation.

Declaring `tags` on the document rather than only on routes is what gives the reference a sensible left-hand nav; a `Tag:` in a comment that matches
no declared tag still works but arrives undescribed.

Gate `/scalar` on configuration outside development. The document itself is usually fine to expose; an interactive console issuing real writes against
production is not.

Load `@scalar/api-reference` unversioned, as above, so the reference tracks upstream without a release of your own. Where an environment forbids
third-party CDNs, vendor the bundle into the module's resources and serve it yourself — the rest of the setup is unchanged.

### Match the schemas to your JSON naming

If responses are serialized snake_case — which they are wherever `JsonNamingStrategy.SnakeCase` is configured, including `problemDetails` — then the
**inferred schemas must be snake_case too**, or the reference documents field names no client will ever see. Override the inference once:

```kotlin
private object SnakeCaseJsonSchemaInference : JsonSchemaInference {
    private val delegate = KotlinxSerializerJsonSchemaInference.Default

    override fun buildSchema(type: KType): JsonSchema = delegate.buildSchema(type).applySnakeCase()
    // …recursively rename properties, required, oneOf, anyOf, items, additionalProperties
}
```

and install it via `JsonSchemaAttributeKey` before building the document, as above. This is easy to forget and produces documentation that is subtly,
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
     * Body: application/json [CreateBookRequest] The book to create
     *
     * Responses:
     *  - 201 application/json [BookResponse] The created book.
     *  - 400 application/problem+json [ProblemDetail] Validation failed; `properties` names each field.
     *  - 409 application/problem+json [ProblemDetail] That ISBN is already registered.
     */
    post("$BOOKS_ROUTE") {
        val book = createBook(call.receive<CreateBookRequest>().toDomain())
        call.respond(HttpStatusCode.Created, book.toResponse())
    }
}
```

Write the summary as what the endpoint *does for a caller* — "Register a new book", not "createBook".
`OperationId` is what client generators turn into a method name, so keep it stable: renaming one is a breaking change for every generated SDK.

Use a fully-qualified type in brackets when the simple name is ambiguous across packages.

## What is inferred, and what is not

| Inferred                      | From                                |
|-------------------------------|-------------------------------------|
| Path, method, path parameters | The route declaration               |
| Request body schema           | `call.receive<CreateBookRequest>()` |
| Success response schema       | `call.respond(…)`                   |

What it cannot see is the larger half, and all of it matters to a client:

**Query parameters the toolkit reads for you.** `call.pagination` and `call.expand` consume `?page`,
`?pageSize`, `?sortBy` and `?expand` without those strings appearing in your handler. Nothing infers them. A collection endpoint that does not
document them is undocumented in the way clients will actually notice.

**Anything thrown rather than responded.** `HttpStatusException`, a domain exception mapped in
`problemDetails { }`, a validation failure — none reach a `call.respond` the compiler can read. Every non-200 you care about is a line you write.

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
 *  - 200 application/json [PagedResponse] A page of books.
 *  - 400 application/problem+json [ProblemDetail] An unknown sort field.
 */
```

Three things there are the point of this skill:

**The defaults and the cap are stated.** They are enforced silently — `?pageSize=5000` returns 100 without complaint — so a client that does not read
them quietly gets less than it asked for and never learns why.

**The sortable fields are listed.** The allow-list is unguessable, and guessing wrong is a 400 (or a 500 if the mapping from `ktor-toolkit:pagination`
was never added).

**`totalPages` is explained.** It is a count, not an index — the single most commonly misread field in the whole response.

For expansion, document the supported fields and the depth you actually support;
`ktor-toolkit:expand` explains why unbounded nesting is a promise you do not want to make.

## Errors and examples

Document every status a client should handle, and say what causes it — a bare `400 Bad Request` tells a caller nothing they did not already know.
Always name `application/problem+json` on error responses: clients that branch on `Content-Type` need to know the error shape differs from the success
shape (`ktor-toolkit:problem-details`).

`500` is worth one mention per API rather than one per route. Every endpoint can fail; repeating it adds noise without adding information.

For anything a client must construct — a `sortBy` value, an `expand` path, a body — put a realistic example in the description, in backticks.
`"title": "string"` teaches nothing; `"title": "The Hobbit"`
shows the shape and the spirit at once.

## Keeping it in sync

Generation removes most of the drift, but not the parts that are prose. Treat the comment as part of the endpoint's contract, changed in the same
commit as the code — `ktor-toolkit:commit`.

The reviewable question on any endpoint change: **if this shipped, would the comment now be a lie?**
A new validation rule, a widened sort allow-list, a newly mapped exception, a renamed field — each changes what a client must know, and none will fail
a build.

With `onlyCommented = true`, an endpoint with no comment is simply absent from the specification. That is the honest outcome: better a documented
subset than a published lie.

## Common mistakes

| Mistake                                              | Why it hurts                                                                 |
|------------------------------------------------------|------------------------------------------------------------------------------|
| Adding an annotation library                         | Duplicates what the compiler infers, and drifts on its own schedule          |
| Swagger UI instead of Scalar                         | Bundled assets to keep in step with Ktor, for a worse read of nested schemas |
| Forgetting `.hide()` on the docs routes              | `/docs.json` and `/scalar` document themselves                               |
| Inferred schemas left camelCase with snake_case JSON | Every field name in the reference is one no client will see                  |
| `ktor { openApi { } }` in `-app`                     | The compiler plugin must run where the routes are compiled                   |
| No `Query:` lines on a paged endpoint                | The toolkit's parameters are invisible to inference and to clients           |
| Not listing the sortable fields                      | The allow-list is unguessable; wrong guesses are errors                      |
| Documenting only the success case                    | Clients discover the error shape in production                               |
| Renaming an `OperationId` casually                   | Breaks the method name in every generated client                             |
| Placeholder examples (`"string"`, `"foo"`)           | Restate the type the schema already gave                                     |
| Scalar exposed in production                         | An interactive console for real writes                                       |
