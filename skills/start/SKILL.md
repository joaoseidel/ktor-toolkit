---
name: start
description: >-
    Entry point for every task in a service built with Ktor Toolkit. Run this FIRST, before writing
    or changing any Kotlin, to work out which skills the task needs. Use for any request touching a
    route, use case, adapter, DTO, paging, links, error responses, validation, caching, DI, tests,
    Gradle, Docker or a commit — and for "where does this file go?" too. Route first, then implement.
---

# Ktor Toolkit — Start Here

The seven libraries are the easy part. The hard part is that everyone who touches the service solves the *surrounding* problem differently: one parses
`?page` by hand, one invents an error envelope, one puts the repository call in the route body. You get a codebase where every endpoint is defensible
alone and none of them match.

**Route before you build.** One cheap pass decides which skills apply, and it costs less than rewriting an endpoint that was invented instead of
assembled.

## The phases

Run these in order. Do not collapse them, and do not finish one without loading what it names.

| Phase            | Do this                                                          | Load                                                        |
|------------------|------------------------------------------------------------------|-------------------------------------------------------------|
| **0. Orient**    | Establish what the project already has                           | — (two commands, below)                                     |
| **1. Route**     | Match the request against the routing table                      | Whatever the table names — usually two or three             |
| **2. Implement** | Write the code the routed skills describe                        | `ktor-toolkit:codestyle` **always**, plus the routed skills |
| **3. Document**  | KDoc every public declaration; comment only what the code cannot | `ktor-toolkit:comments` **always**                          |
| **4. Verify**    | Run the project's own gate                                       | `ktor-toolkit:tests`, `ktor-toolkit:kover` when they apply  |
| **5. Record**    | Changelog entry and commit, together                             | `ktor-toolkit:changelog`, `ktor-toolkit:commit`             |

`codestyle` and `comments` are never routed — they apply to every line of Kotlin in the service. Load them whether or not the table sends you
anywhere.

## Phase 0 — Read the ground

Two commands, not an audit.

**Which toolkit modules are on the classpath.** A skill for a module the project does not depend on is premature: say so and offer the
`ktor-toolkit:install` skill.

```bash
grep -rn "ktor-toolkit" --include="*.gradle.kts" --include="*.toml" . | grep -v "^\./build"
```

**How this project builds and checks itself.** These skills write `make build`, `make lint` and `make verify` because that is the convention the
`ktor-toolkit:makefile` skill sets up. Your project may have no Makefile at all — Phase 4 owes you the gate *this* project runs, not the one an
example printed.

```bash
ls Makefile 2>/dev/null && grep -E '^[a-z_]+:' Makefile
```

No Makefile means `./gradlew build`. Note it and move on.

## When something a skill describes does not exist

These skills describe a complete service; a real project has built part of one. You will reach a skill that assumes a `report` module, a Dockerfile or
a `CHANGELOG.md` in a repo that has none.

**A missing artifact is neither a blocker nor permission to scaffold.** Every time:

1. **Say what is missing and what it would contain** — a sentence or two. "There is no `CHANGELOG.md`, and this change is breaking, so it wants an
   entry. I would add the file with a Keep a Changelog header and a `Breaking` section for this version."
2. **Ask, then wait.** A Makefile, a Dockerfile, a changelog or a new Gradle module changes how the project is run and built. That is the user's
   call — they may have a house pattern, a template, or a reason it is absent.
3. **Create it, then say you did.** Follow the skill that owns it and name the file in your report.

**Do not route around the gap either.** Skipping the changelog because there is no file, or inlining a `docker build` because there is no Makefile,
buries a decision the user never got to make.

The exception: a file the task cannot proceed without that has exactly one sensible form — a `db/migration` directory for a migration you were asked
to write. Create those and name them.

## Phase 1 — Route

Most tasks hit two or three rows. That is normal.

| The request involves                                                      | Load                           |
|---------------------------------------------------------------------------|--------------------------------|
| A new or changed HTTP route, of any kind                                  | `ktor-toolkit:endpoint`        |
| Where a file goes; layering; ports; "is this the right place?"            | `ktor-toolkit:architecture`    |
| Lists, `?page`, `?pageSize`, `?sortBy`, offsets, "return all X"           | `ktor-toolkit:pagination`      |
| `_links`, `self`/`next`/`prev`, resource envelopes                        | `ktor-toolkit:hateoas`         |
| `?expand=`, embedding a related resource, N+1 worry                       | `ktor-toolkit:expand`          |
| Rejecting bad input, required fields, formats, rules on a request         | `ktor-toolkit:validation`      |
| A status field with rules; "can this be cancelled yet?"; a lifecycle      | `ktor-toolkit:state-machine`   |
| Error responses, status codes, `problem+json`, exception handling         | `ktor-toolkit:problem-details` |
| Response caching, TTL, invalidation, Redis, "this endpoint is slow"       | `ktor-toolkit:cache`           |
| A table, column, index or constraint; anything the schema states          | `ktor-toolkit:migrations`      |
| Wiring dependencies, singletons, registration                             | `ktor-toolkit:di`              |
| Tests of any kind, fixtures, mocks, Testcontainers                        | `ktor-toolkit:tests`           |
| Coverage numbers, thresholds, exclusions                                  | `ktor-toolkit:kover`           |
| API documentation, OpenAPI, Swagger, Scalar                               | `ktor-toolkit:openapi`         |
| Log statements, correlation IDs, what to log                              | `ktor-toolkit:logging`         |
| Health, readiness or liveness endpoints, probes, Cohort                   | `ktor-toolkit:healthcheck`     |
| `build.gradle.kts`, `libs.versions.toml`, dependency scopes, a new module | `ktor-toolkit:gradle`          |
| Dockerfile, image size, JVM flags in containers, graceful shutdown        | `ktor-toolkit:container`       |
| Make targets, "how do I run this?"                                        | `ktor-toolkit:makefile`        |
| Adding the toolkit to a project, or installing these skills               | `ktor-toolkit:install`         |
| Writing any Kotlin at all                                                 | `ktor-toolkit:codestyle`       |
| Any comment, KDoc block or public declaration                             | `ktor-toolkit:comments`        |
| Anything a client or another team would notice; any release               | `ktor-toolkit:changelog`       |
| Any commit                                                                | `ktor-toolkit:commit`          |

**When in doubt, load it.** Reading a skill costs a few thousand tokens; shipping an endpoint that hand-rolls what a module already does costs a
review cycle and leaves the codebase less uniform than it was.

**`ktor-toolkit:endpoint` is a second-level router.** For anything route-shaped, load it and follow it — it asks the feature questions in the order
that keeps answers consistent and pulls in the feature skills itself. Do not pre-empt it by guessing the feature set.

## Phase 2 — Implement

**A skill you loaded is the decision, not a suggestion.** If `ktor-toolkit:pagination` puts the sortable-column allow-list at the adapter boundary, it
goes there — even where a different placement would be shorter in this one endpoint. Local optimisations are exactly what erode a shared style.

**Skills do not contradict each other.** If two seem to, re-read before inventing a compromise. A genuine conflict is a bug worth naming to the user.

**Explain only what the task forced you to decide** — which module you reached for, what you asked the user to choose. Do not narrate the parts of a
skill you merely followed. The user wants an endpoint, not a tour of the toolkit.

## Phase 4 — Verify

Run what Phase 0 found: `make verify` where the project follows the `ktor-toolkit:makefile` convention, otherwise `make build` or `./gradlew build`.

**Never report work finished on a build you did not run.** If a gate fails and you are leaving it failing, say so and show the output.

If the project publishes a library, a changed public signature also carries its refreshed `api/` dump in the same commit. Most services publish
nothing and have no `api/` directory — skip this unless the tree has one.

## Phase 5 — Record

One commit, not two: the changelog entry and the code land together or the record is already wrong.

## Signals a skill was skipped

These are the shapes code takes when someone builds past the toolkit instead of with it. Each sends you back to Phase 1 — whether you are writing the
code or reading someone else's.

| You are about to write                                                  | Stop and load                  |
|-------------------------------------------------------------------------|--------------------------------|
| `call.request.queryParameters["page"]?.toIntOrNull() ?: 0`              | `ktor-toolkit:pagination`      |
| A `data class` with `page`, `total`, `items` fields                     | `ktor-toolkit:pagination`      |
| String-concatenating a `?page=` URL for a next-page link                | `ktor-toolkit:hateoas`         |
| `call.respond(HttpStatusCode.BadRequest, mapOf("error" to …))`          | `ktor-toolkit:problem-details` |
| `try { … } catch (e: Exception) { call.respond(500) }` in a route       | `ktor-toolkit:problem-details` |
| `if (request.title.isNullOrBlank()) throw …` at the top of a handler    | `ktor-toolkit:validation`      |
| `!!` on a field of a request DTO                                        | `ktor-toolkit:validation`      |
| A `ConcurrentHashMap` used as a cache                                   | `ktor-toolkit:cache`           |
| A loop that queries per row to fill in a related object                 | `ktor-toolkit:expand`          |
| `if (order.state != PLACED) throw …` at the top of a use case           | `ktor-toolkit:state-machine`   |
| A `when (entity.status)` deciding what is allowed to happen next        | `ktor-toolkit:state-machine`   |
| A repository interface in the same module as its Exposed implementation | `ktor-toolkit:architecture`    |
| `SchemaUtils.create(…)` anywhere but a test                             | `ktor-toolkit:migrations`      |
| An edit to a migration file that has already run                        | `ktor-toolkit:migrations`      |
| A version literal in `build.gradle.kts`                                 | `ktor-toolkit:gradle`          |
| `get("/health") { call.respond(HttpStatusCode.OK) }`                    | `ktor-toolkit:healthcheck`     |
| `runCatching { }` around a suspending call                              | `ktor-toolkit:codestyle`       |
| `!!`, a bare `@Suppress`, or `require` in a secondary constructor       | `ktor-toolkit:codestyle`       |
| A comment restating the line below it                                   | `ktor-toolkit:comments`        |
| A wire-format change with no `CHANGELOG.md` entry                       | `ktor-toolkit:changelog`       |

## When nothing matches

Plenty of work has no toolkit opinion — a JSON field rename, a log level, a flaky test. Say the routing found nothing and get on with it; padding a
small task with skill machinery is its own noise. Phases 2, 3 and 5 still apply: it is still Kotlin, and it still gets a commit.

But notice repetition. The third time the same shape is solved from scratch in this service, name it to the user — a shared helper or a note in
`CLAUDE.md` is what stops the fourth.
