---
name: start
description: >-
  Entry point for every task in a service built with Ktor Toolkit (com.luizalabs.ktor-toolkit —
  paginator, hateoas, validator, problem-details, expander, cache). Run this FIRST, before writing or
  changing any Kotlin, to work out which toolkit modules a task touches and which
  ktor-toolkit skills to load. Use it whenever the request involves a Ktor route, endpoint, use
  case, repository, adapter, request or response DTO, paging, sorting, filtering, links, error
  responses, validation, caching, field expansion, dependency injection, tests, Gradle, Docker or
  a commit in such a project — and also for read-only questions like "how should this be
  structured?" or "where does this file go?". Do not start implementing and consult the toolkit
  afterwards; route first, then implement.
---

# Ktor Toolkit — Start Here

## Why this skill exists

Ktor Toolkit is a set of six small libraries that already solve the problems every JSON API
re-solves: paging, links, validation, error shape, expansion, caching. The libraries are the easy
part. The hard part is that ten contributors — human or agent — will each solve the *surrounding*
problem a different way: one parses `?page` by hand, one invents a bespoke error envelope, one puts
the repository call in the route body.

The result is a codebase where every endpoint is defensible on its own and none of them look
alike. That is the failure this collection prevents. Its job is to make the *n*-th endpoint in a
service look like the first one, whoever wrote it.

So: route before you build. Deciding which skills apply costs one cheap pass over the request and
saves rewriting an endpoint that was invented instead of assembled.

## Step 1 — Read the ground

Before routing, establish two facts. Both change the answer.

**Which modules are on the classpath.** A skill for a module the project does not depend on is
premature; the honest move is to say so and offer `ktor-toolkit:install`.

```bash
grep -rn "ktor-toolkit" --include="*.gradle.kts" --include="*.toml" . | grep -v "^\./build"
```

**Whether this is the toolkit repo or a service that uses it.** In the toolkit repo
(`rootProject.name = "ktor-toolkit"`, modules named `ktor-toolkit-*`) the rules are stricter: public
API dumps under `api/`, 100% Kover coverage, KDoc on every public declaration. Check `settings.gradle.kts`.
`ktor-toolkit:gradle`, `ktor-toolkit:tests`, `ktor-toolkit:kover` and `ktor-toolkit:comments` all
behave differently in the two cases, and they say how.

Keep this quick. Two commands, not an audit.

## Step 2 — Route

Match the request against the table. Most real tasks hit two or three rows — that is normal and
expected, not a sign you have over-matched.

| The request involves | Load |
|---|---|
| A new or changed HTTP route, of any kind | `ktor-toolkit:endpoint` |
| Where a file goes, layering, ports, adapters, "is this the right place?" | `ktor-toolkit:architecture` |
| Lists, `?page`, `?pageSize`, `?sortBy`, offsets, limits, "return all X" | `ktor-toolkit:pagination` |
| `_links`, `self`/`next`/`prev`, discoverability, resource envelopes | `ktor-toolkit:hateoas` |
| `?expand=`, sparse fieldsets, embedding a related resource, N+1 worry | `ktor-toolkit:expand` |
| Rejecting bad input, required fields, formats, business rules on a request | `ktor-toolkit:validation` |
| Error responses, status codes, `problem+json`, exception handling | `ktor-toolkit:problem-details` |
| Response caching, TTL, invalidation, Redis, "this endpoint is slow" | `ktor-toolkit:cache` |
| Wiring dependencies, singletons, scopes, module registration | `ktor-toolkit:di` |
| Tests of any kind, fixtures, mocks, Testcontainers | `ktor-toolkit:tests` |
| Coverage numbers, thresholds, exclusions | `ktor-toolkit:kover` |
| API documentation, Swagger, OpenAPI | `ktor-toolkit:openapi` |
| Log statements, correlation IDs, what to log | `ktor-toolkit:logging` |
| `build.gradle.kts`, `libs.versions.toml`, dependency scopes, new module | `ktor-toolkit:gradle` |
| Dockerfile, image size, JVM flags in containers, graceful shutdown | `ktor-toolkit:container` |
| Make targets, "how do I run this" | `ktor-toolkit:makefile` |
| Whether to write a comment or KDoc | `ktor-toolkit:comments` |
| Any commit | `ktor-toolkit:commit` |
| Adding the toolkit to a project, or installing these skills | `ktor-toolkit:install` |

**When in doubt, load it.** Reading a skill costs a few thousand tokens. Shipping an endpoint that
hand-rolls something a module already does costs a review cycle and leaves the codebase less
uniform than it was.

**`ktor-toolkit:endpoint` is a second-level router.** For anything route-shaped, load it and follow
it — it asks the feature questions (does this page? does it need links? does it cache?) in the order
that keeps the answers consistent, and pulls in the feature skills itself. Do not pre-empt it by
guessing the feature set yourself.

## Step 3 — Implement

Read the SKILL.md of each routed skill, then build. Two things to hold onto while you do:

**A skill you loaded is the decision, not a suggestion.** If `ktor-toolkit:pagination` says the
allow-list of sortable columns lives at the adapter boundary, it lives there — even if a different
placement would be shorter in this one endpoint. Local optimizations are exactly what erode a
shared style.

**Skills do not contradict each other.** If two seem to, you have misread one; re-read before
inventing a compromise. If they genuinely conflict, that is a bug in the collection worth naming to
the user rather than papering over.

Explain only the decisions this task actually forced — which module you reached for and why, what
you asked the user to choose. Do not narrate the parts of the skill you merely followed. The user
wants an endpoint, not a lecture on the toolkit.

## Signals that a skill was skipped

These are the shapes hand-written code takes when someone builds past the toolkit instead of with
it. Treat each as a prompt to go back to Step 2 — whether you are writing the code or reading
someone else's.

| You are about to write | Stop and load |
|---|---|
| `call.request.queryParameters["page"]?.toIntOrNull() ?: 0` | `ktor-toolkit:pagination` |
| A `data class` with `page`, `total`, `items` fields | `ktor-toolkit:pagination` |
| String-concatenating a `?page=` URL for a next-page link | `ktor-toolkit:hateoas` |
| `call.respond(HttpStatusCode.BadRequest, mapOf("error" to ...))` | `ktor-toolkit:problem-details` |
| `try { ... } catch (e: Exception) { call.respond(500) }` in a route | `ktor-toolkit:problem-details` |
| `if (request.title.isNullOrBlank()) throw ...` at the top of a handler | `ktor-toolkit:validation` |
| `!!` on a field of a request DTO | `ktor-toolkit:validation` |
| A `ConcurrentHashMap` used as a cache | `ktor-toolkit:cache` |
| A loop that queries per row to fill in a related object | `ktor-toolkit:expand` |
| A repository interface declared in the same file as its Exposed implementation | `ktor-toolkit:architecture` |
| A version number literal in `build.gradle.kts` | `ktor-toolkit:gradle` |

## When nothing matches

Plenty of work has no toolkit opinion — a JSON field rename, a log level, a flaky test. Say the
routing found nothing and get on with it. Padding a small task with skill machinery is its own kind
of noise.

But notice repetition. When the same shape gets solved from scratch a third time, that is a skill
the collection is missing. Name it to the user rather than solving it a fourth time — a new skill is
how this collection is supposed to grow.
