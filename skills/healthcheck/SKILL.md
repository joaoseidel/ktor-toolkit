---
name: healthcheck
description: >-
  Health checks and operational endpoints with Cohort (com.sksamuel.cohort) — separate liveness and
  readiness registries, the built-in checks worth registering, writing a custom HealthCheck that
  cannot lie, what a probe must never depend on, the 200/503 contract and JSON body, Kubernetes
  probe settings, and keeping the diagnostic endpoints (heapdump, sysprops, threaddump, logging)
  off the public internet. Use whenever a service needs a health, readiness or liveness endpoint,
  when adding a dependency that a probe might have to know about, when a deploy restarts healthy
  instances or a dependency blip takes the whole service down, when writing Kubernetes probes for a
  Ktor service, and before anyone writes `get("/health") { call.respond("OK") }` by hand.
---

# Health checks

## What a probe is for

A health endpoint exists so an orchestrator can make two decisions, and they are not the same decision:

- **Liveness** — is this process wedged beyond recovery? Failing it **kills and restarts the container**.
- **Readiness** — can this instance serve traffic *right now*? Failing it **removes the instance from the load balancer** and leaves it running.

Almost every health-check bug is these two being answered by one endpoint. Put a database check behind liveness and a thirty-second database blip
restarts every instance you have — turning a partial outage into a total one, then a cold-start stampede against the database that is already
struggling. That failure is not hypothetical; it is the single most common way a health check makes an incident worse.

So: **two registries at two paths, always.** Even when the liveness one starts with a single check.

## Cohort, not a hand-written route

```kotlin
get("/health") { call.respond(HttpStatusCode.OK) }        // says nothing
get("/health") { db.ping(); call.respond(…) }             // worse
```

The second is the one worth arguing about. It pings the dependency **per request**, so probe latency is dependency latency, every replica's kubelet
adds load to the database on a fixed interval, and a slow dependency makes the probe time out — which is read as "the process is dead".

Cohort inverts it. Checks run **on a schedule, in the background**, and each one's last result is cached in the registry; the endpoint reads that map
and returns. The probe therefore costs the same whether the database is healthy, slow or gone, and the check interval — not the probe interval —
decides how often the dependency is touched.

That is the whole reason for the dependency. Everything below is how to configure it.

## The wiring

One file in `-app`, called from `module()`, dependencies taken from the container:

```kotlin
// -app/config/HealthChecks.kt
internal fun Application.installHealthChecks() {
    val dataSource: DataSource by dependencies
    val searchClient: SearchClient by dependencies

    install(Cohort) {
        verboseHealthCheckResponse = true

        healthcheck(
            "/health/live",
            HealthCheckRegistry(Dispatchers.Default.limitedParallelism(1)) {
                register(ThreadDeadlockHealthCheck(), 10.seconds, 30.seconds)
            },
        )

        healthcheck(
            "/health/ready",
            HealthCheckRegistry(Dispatchers.Default.limitedParallelism(1)) {
                register(DatabaseConnectionHealthCheck(dataSource), 5.seconds, 10.seconds)
                register(SearchIndexHealthCheck(searchClient), 5.seconds, 10.seconds)
            },
        )
    }
}
```

Every part of that signature earns its place:

**`register(check, initialDelay, checkInterval)`.** The two-`Duration` form is the one to use; the single-`delay` overloads are deprecated and the
no-duration form silently uses `DEFAULT_INTERVAL` (5 seconds) for both. `initialDelay` is time the service spends **unready** after boot, because
until a check has run its status is `unhealthy("Not yet executed")` — so 5 seconds is a reasonable initial delay and 60 is a minute of 503s on every
deploy.

**The interval is a load decision.** Every registered check runs on its own timer forever, whether or not anyone probes. Ten seconds against a database
is cheap; ten seconds against a paid third-party API is a bill. Cohort skips a tick if the previous run of that check is still in flight, so a slow
check degrades to "as often as it can" rather than piling up.

**`limitedParallelism(1)`** is Cohort's own recommendation for the dispatcher: checks are scheduled work, not throughput work, and the built-in checks
that do IO shift to `Dispatchers.IO` themselves. Do not hand it `Dispatchers.IO`.

**`by dependencies` at the top.** The registry is built once at startup; nothing here runs per request. Load the `ktor-toolkit:di` skill for how the
`DataSource` and the Redis connection got there.

**Do not install Cohort in `-core` or `-adapters`.** `cohort-ktor` is a deployment concern, so only `-app` depends on it — load the
`ktor-toolkit:architecture` skill. A custom `HealthCheck` class lives beside this file in `-app`; it may call into an adapter, but a port interface in
`-core` must never mention `HealthCheck`.

## What goes on which probe

| Probe                | Register                                                             | Never register                                       |
|----------------------|----------------------------------------------------------------------|------------------------------------------------------|
| `/health/live`       | `ThreadDeadlockHealthCheck`, and little else                        | Anything that leaves the process — DB, cache, HTTP   |
| `/health/ready`      | Dependencies the service **cannot serve core traffic without**       | Optional dependencies you already degrade gracefully |

**Liveness must be process-local.** A wedged JVM is what it detects: deadlocked threads, or nothing at all. If you cannot name a failure that only a
restart fixes, an empty-ish liveness check that simply proves the event loop still accepts connections is the honest answer.

The JVM's other unrecoverable state — heap exhaustion — is better handled by `-XX:+ExitOnOutOfMemoryError` than by a memory check, because a JVM in
OOM churn may still answer a probe. Load the `ktor-toolkit:container` skill.

**Readiness is about *core* traffic, and the distinction matters most for the cache.** The `ktor-toolkit:cache` skill's position is that a cache
failure is not a request failure — Redis down means log a warning and serve from the origin. A dependency you deliberately degrade around must
therefore **not** be on readiness: putting it there takes every instance out of the load balancer over a failure the code already handles, which is a
self-inflicted outage. Redis belongs on readiness only when a miss cannot be served at all — a rate limiter, a session store, a pub/sub fan-out the
feature depends on.

**A check that cannot fail is worse than no check**, because it is believed. An empty registry has nothing to report; on Cohort 2.9.x it reports
unhealthy, on older versions it returned a green 200 with an empty body forever.

## Built-in checks

Registering these beats writing your own — they are already correct about timeouts, dispatchers and turning a throw into an unhealthy result.

| Check                                              | Module            | Use for                                                              |
|----------------------------------------------------|-------------------|----------------------------------------------------------------------|
| `ThreadDeadlockHealthCheck()`                      | `cohort-api`      | Liveness. Default `maxDeadlocks = 0`; any deadlock is a problem.     |
| `DatabaseConnectionHealthCheck(ds, timeout, query)`| `cohort-api`      | Readiness. JDBC4 `isValid` plus an optional query.                   |
| `HikariConnectionsHealthCheck(ds, minConnections)` | `cohort-hikari`   | Readiness during startup — the pool has actually opened connections. |
| `RedisClusterHealthCheck(conn)`                    | `cohort-lettuce`  | Readiness, when Redis is load-bearing.                               |
| `TcpHealthCheck(host, port)`                       | `cohort-api`      | A dependency with no client library worth booting.                   |
| `EndpointStartupHealthCheck(client) { … }`         | `cohort-ktor`     | Smoke-testing an upstream **once** at startup.                       |
| `EndpointHealthCheck(client) { … }`                | `cohort-ktor`     | Rarely. Read the warning below first.                                |
| `DiskSpaceHealthCheck.root(minFreeSpacePercentage)`| `cohort-api`      | Services that write to disk. Most do not.                            |
| `FreememHealthCheck.mb(n)`                         | `cohort-api`      | Almost never — see above.                                            |

**`EndpointHealthCheck` against another service is how a cascade starts.** Service A marks itself unready because B is unready because C is
restarting, and an outage in one leaf takes down the graph. `EndpointStartupHealthCheck` exists precisely to avoid that: it probes once, and once it
has succeeded it reports healthy forever without touching the network again — a configuration smoke test, not a liveness signal for someone else's
service. Prefer it, and let a real upstream failure surface as a 502/503 on the request path where `problem-details` can describe it.

`DatabaseConnectionHealthCheck` defaults `timeout` to 1 second and rounds up to a whole second internally, so sub-second values are not honoured.

## A custom check

For anything without a module — an adapter with a proprietary client, a background consumer whose subscription can silently die:

```kotlin
// -app/config/SearchIndexHealthCheck.kt
internal class SearchIndexHealthCheck(
    private val client: SearchClient,
) : HealthCheck {
    override val name: String = "search_index"

    override suspend fun check(): HealthCheckResult =
        try {
            withTimeout(2.seconds) {
                client.ping()
                HealthCheckResult.healthy("Search index reachable")
            }
        } catch (e: TimeoutCancellationException) {
            HealthCheckResult.unhealthy("Search index did not respond within 2s", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HealthCheckResult.unhealthy("Search index unreachable", e)
        }
}
```

**Return `unhealthy`, do not throw.** The registry does catch a throw, but the result it invents —
`"search_index failed due to java.net.SocketTimeoutException"` — is the message an operator reads at 03:00. Yours is better.

**Bound the check yourself.** Cohort 2.9.x applies a `checkTimeout` (10 seconds by default, settable on the registry); 2.8 applies none at all, so a
check that hangs on a socket read never completes and its status silently stays at whatever it was last. A `withTimeout` shorter than the check
interval is the fix, and it wants to be *much* shorter — a 2-second budget on a 10-second interval.

**Catch `Exception`, not `Throwable`, and rethrow `CancellationException`.** This is the subtle one: `runCatching` catches everything including
cancellation, so a registry shutting down — or a `checkTimeout` firing — gets reported as a dependency failure instead of propagating. Catch the
timeout you asked for, rethrow the cancellation you did not.

**The `name` is the JSON key and it must be unique per registry** — registering a duplicate name throws at startup. Built-in checks have sensible
defaults (`thread_deadlocks`, `database_connection`); use `snake_case` and name the dependency, not the class. Two checks over the same connection are
fine as long as they are named differently, which is what the `register(name, check, …)` overload is for.

**A check is read-only and cheap.** No writes, no `SELECT` over a real table, nothing that takes a lock. It runs forever, on every replica.

`HealthCheck` is a `fun interface`, so a trivial one can be a lambda — but a named class is testable on its own, which the next section relies on.

## The HTTP contract

| Registry state          | Status | Body (`verboseHealthCheckResponse = true`) | Body (`false`)              |
|-------------------------|--------|--------------------------------------------|------------------------------|
| Every check healthy     | `200`  | JSON array, one object per check           | `OK`, `text/plain`           |
| Any check unhealthy     | `503`  | Same array, failing entries included       | `Service Unavailable`        |

```json
[
  {
    "name": "database_connection",
    "status": "Healthy",
    "lastCheck": "2026-08-07T13:22:04.118Z",
    "message": "Connected to database successfully",
    "cause": null,
    "consecutiveSuccesses": 412,
    "consecutiveFailures": 0
  }
]
```

`consecutiveFailures` is the field worth knowing about: it is why you do **not** need `failureThreshold`-style debouncing inside a check. One failed
tick flips the endpoint to 503 immediately, and the orchestrator's own `failureThreshold` decides whether that matters.

Two things about this response that surprise people:

**`cause` is a full stack trace.** Verbose responses are an internal diagnostic, not a public status page. Keep verbose on for an internal service;
turn it off, or keep the path off the internet, for anything reachable from outside.

**Cohort writes the body itself.** It uses its own Jackson mapper and `respondText`, so `ContentNegotiation`, your `Json` configuration and the
toolkit's `problem-details` mapping are all bypassed. A 503 from here is deliberately *not* `application/problem+json` — do not try to make it one, and
do not let the `ktor-toolkit:problem-details` skill's rules chase it. It is a machine-to-machine signal, not an API response.

## Kubernetes

```yaml
startupProbe:
  httpGet: { path: /health/live, port: 8080 }
  periodSeconds: 5
  failureThreshold: 30        # 150s of grace for a cold JVM
livenessProbe:
  httpGet: { path: /health/live, port: 8080 }
  periodSeconds: 10
  failureThreshold: 3
readinessProbe:
  httpGet: { path: /health/ready, port: 8080 }
  periodSeconds: 5
  failureThreshold: 3
```

**The startup probe is what stops a slow boot becoming a restart loop.** While it is failing, the liveness probe is suspended entirely, so JVM startup
time — class loading, pool warm-up, the first Flyway run — cannot be mistaken for a wedged process. It is a better answer than a long
`initialDelaySeconds` on liveness, which stays long forever. If the number has to be large, the `ktor-toolkit:container` skill's AOT and CDS sections
are the actual fix.

**Readiness should fail *before* shutdown finishes.** During a rolling deploy the endpoint keeps returning 200 while Ktor drains, which is correct —
in-flight requests must complete — but the load balancer needs a few seconds to stop sending new ones. `shutdownGracePeriod` in `application.yaml`
covers that window; load the `ktor-toolkit:container` skill.

**Do not point a liveness probe at `/health/ready`.** It is the same mistake as one endpoint, written across two YAML keys.

## The other Cohort endpoints

Cohort also serves diagnostics under `endpointPrefix` (default `/cohort`), each behind its own flag, each **off** unless enabled:

| Flag                        | Path                                | Exposes                                             |
|-----------------------------|-------------------------------------|-----------------------------------------------------|
| `jvmInfo`                   | `/cohort/jvm`                       | JVM version, flags, uptime                          |
| `gc`                        | `/cohort/gc`                        | Collector counts and times                          |
| `memory`                    | `/cohort/memory`                    | Heap and buffer pool usage                          |
| `threadDump`                | `/cohort/threaddump`                | Full thread dump                                    |
| `dataSources`               | `/cohort/datasources`               | Pool sizes and waits (`HikariDataSourceManager`)    |
| `migrations`                | `/cohort/dbmigration`               | Applied migrations (`FlywayMigrations(dataSource)`) |
| `logManager`                | `/cohort/logging`, `PUT …/{n}/{lv}` | Reads **and changes** log levels (`LogbackManager`) |
| `sysprops`                  | `/cohort/sysprops`                  | Every system property                               |
| `heapDump`                  | `/cohort/heapdump`                  | A full `.hprof` of the running heap                 |
| `operatingSystem`           | `/cohort/os`                        | Host OS details                                     |

`/cohort/datasources` and `/cohort/dbmigration` are the two that pay for themselves — "is the pool exhausted?" and "did this deployment run the
migration?" are the questions asked during real incidents. Load the `ktor-toolkit:migrations` skill for the Flyway side.

**These are not public endpoints.** `heapdump` streams the entire heap — every token, password and personal record currently in memory, in a format
that opens in a GUI. `sysprops` prints anything passed as `-D`, credentials included. `PUT /cohort/logging/{name}/{level}` lets an unauthenticated
caller switch a package to `TRACE`, which is both a denial-of-service and a way to make the application log the request bodies the
`ktor-toolkit:logging` skill forbids.

So enable them deliberately, and put them behind something. If the probes' paths are the only thing the ingress exposes, `install(Cohort)` is fine.
Where the diagnostics need authentication, mount them yourself instead of installing the plugin:

```kotlin
routing {
    authenticate("internal") {
        cohort {
            jvmInfo = true
            threadDump = true
            dataSources = listOf(HikariDataSourceManager(dataSource))
        }
    }
}
```

`Route.cohort()` is the same route builder the plugin calls, so it takes the same configuration block — but **use one or the other, never both**. With
the plugin installed, a second `cohort { }` mutates the same shared configuration and re-registers every route, and a duplicate `healthcheck()` path
throws.

## Metrics from the same checks

`cohort-micrometer` turns check results into counters, so "the search index flapped four times last night" is a graph rather than a memory:

```kotlin
CohortMetrics(readinessRegistry).bindTo(meterRegistry)
```

It emits `cohort.healthcheck` tagged with `name`, `type` and `status`. It needs a reference to the registry, which means building the registries as
values before `install(Cohort)` rather than inline — worth doing the moment more than one thing needs them.

## Testing

**Unit-test the custom checks.** A `HealthCheck` is a class with one suspend function and a mockable collaborator, so the failure paths — timeout,
exception, wrong response — are cheap to pin down, and they are the paths that only ever run during an incident:

```kotlin
test("reports unhealthy when the index is unreachable") {
    val client = mockk<SearchClient> { coEvery { ping() } throws SocketTimeoutException() }

    SearchIndexHealthCheck(client).check().status shouldBe HealthStatus.Unhealthy
}
```

**Be careful asserting `200` on the endpoint in an acceptance test.** A freshly started application is unhealthy by design until every check has run
once, so a probe fired immediately after boot correctly returns 503. Assert the endpoint exists and is reachable, or register with a very short
`initialDelay` in the test module — do not add a sleep and hope. Load the `ktor-toolkit:tests` skill.

**Each registry starts a scheduler thread and adds a JVM shutdown hook.** A `testApplication` that boots the full `module()` in every test creates one
per test. It is tolerable for a handful of acceptance tests and a leak in a suite of hundreds; narrow route tests should not install Cohort at all.

## Gradle

```toml
[versions]
cohort = "2.9.10"

[libraries]
cohort-ktor = { module = "com.sksamuel.cohort:cohort-ktor", version.ref = "cohort" }
cohort-hikari = { module = "com.sksamuel.cohort:cohort-hikari", version.ref = "cohort" }
cohort-flyway = { module = "com.sksamuel.cohort:cohort-flyway", version.ref = "cohort" }
```

`implementation` in `-app` only — nothing else should be able to import `HealthCheck`. Load the `ktor-toolkit:gradle` skill. Integration modules exist
per technology (`hikari`, `dbcp`, `flyway`, `liquibase`, `lettuce`, `jedis`, `redis`, `kafka`, `pulsar`, `mongo`, `cassandra`, `elastic`, `rabbit`,
`aws-*`, `micrometer`, `logback`, `log4j2`); take only the ones you register something from.

**Version differences that change behaviour.** This skill describes 2.9.x. On 2.8: there is no `checkTimeout`, so an unbounded check can hang forever;
a duplicate `healthcheck()` path silently overwrites the earlier registry; and a registry with no checks reports **healthy**.

## Common mistakes

| Mistake                                                | Why it hurts                                                                |
|--------------------------------------------------------|------------------------------------------------------------------------------|
| One endpoint for liveness and readiness                | Every dependency blip becomes a restart of every instance                   |
| A database or cache check on liveness                  | The same thing, with a specific cause                                       |
| A hand-written `get("/health")` that pings the database| Probe latency becomes dependency latency, and every replica adds load       |
| Redis on readiness when the code degrades without it   | Takes the fleet out of the load balancer over a handled failure             |
| `EndpointHealthCheck` against an upstream service      | One leaf outage cascades through the graph; use the startup variant         |
| An empty registry                                      | A probe that can never fail, and is believed                                |
| `runCatching` inside `check()`                         | Swallows cancellation; shutdown and `checkTimeout` look like failures       |
| Throwing out of `check()`                              | The operator reads Cohort's generic message instead of yours                |
| No `withTimeout` in a custom check                     | On 2.8 it can hang forever and the status silently goes stale               |
| A long `initialDelay`                                  | Exactly that many seconds of 503 on every deploy                            |
| A 1-second check interval                              | Constant load on a dependency, forever, from every replica                  |
| `heapDump` or `sysprops` reachable from the internet   | Ships the heap — tokens, PII — or every `-D` credential                     |
| `logManager` without authentication                    | Anyone can switch the app to `TRACE` and make it log request bodies         |
| Verbose responses on a public path                     | `cause` is a full stack trace                                               |
| Expecting `problem+json` from a 503 here               | Cohort writes its own body; `ContentNegotiation` is bypassed                |
| Asserting `200` immediately after boot in a test       | Checks have not run yet, so unhealthy is the correct answer                 |
| Duplicate check names in one registry                  | Throws at startup                                                           |
| `cohort` imported in `-core` or `-adapters`            | A deployment concern leaking into the domain                                |
