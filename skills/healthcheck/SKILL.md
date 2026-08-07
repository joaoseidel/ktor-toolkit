---
name: healthcheck
description: >-
  Liveness and readiness endpoints with Cohort — separate registries, checks that cannot lie, what a
  probe must never depend on, the 200/503 contract, Kubernetes probe settings, and keeping the
  diagnostic endpoints off the public internet. Use whenever a service needs a health, readiness or
  liveness endpoint, when a deploy restarts healthy instances or a dependency blip takes the whole
  service down, and before anyone writes `get("/health") { call.respond("OK") }` by hand.
---

# Health checks

## When the service has no probes

If a task needs a probe and none exists, **say what you would add and wait**: the Cohort dependency, a `-app` file registering the two registries, and
the two paths. Cohort is a third-party dependency, so taking it is the user's call. Where they would rather not, two hand-written routes reading a
background-refreshed state still beat one route that pings the database per request — say that is the trade you made.

**Flag a single path serving both liveness and readiness on sight**, even when the task is something else. The next section is why.

## What a probe is for

An orchestrator makes two different decisions:

- **Liveness** — is this process wedged beyond recovery? Failing it **kills and restarts the container**.
- **Readiness** — can this instance serve traffic *right now*? Failing it **removes the instance from the load balancer**, running.

Almost every health-check bug is these two answered by one endpoint. Put a database check behind liveness and a thirty-second blip restarts every
instance you have — a partial outage becomes total, then a cold-start stampede hits the database that was already struggling. This is the single most
common way a health check makes an incident worse.

**Two registries at two paths, always** — even when liveness starts with one check.

## Cohort, not a hand-written route

```kotlin
get("/health") { call.respond(HttpStatusCode.OK) }        // says nothing
get("/health") { db.ping(); call.respond(…) }             // worse
```

The second pings the dependency **per request**: probe latency becomes dependency latency, every replica's kubelet adds fixed-interval load to the
database, and a slow dependency times the probe out — which reads as "the process is dead".

Cohort inverts it. Checks run **on a schedule, in the background**, and each one's last result is cached in the registry; the endpoint reads that map
and returns. The probe costs the same whether the database is healthy, slow or gone, and the *check* interval — not the probe interval — decides how
often the dependency is touched.

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

**The interval is a load decision.** Every registered check runs on its own timer forever, whether or not anyone probes. Ten seconds against a
database is cheap; ten seconds against a paid third-party API is a bill. Cohort skips a tick if the previous run of that check is still in flight, so
a slow check degrades to "as often as it can" rather than piling up.

**`limitedParallelism(1)`** is Cohort's own recommendation for the dispatcher: checks are scheduled work, not throughput work, and the built-in checks
that do IO shift to `Dispatchers.IO` themselves. Do not hand it `Dispatchers.IO`.

**`by dependencies` at the top.** The registry is built once at startup; nothing here runs per request. Load the `ktor-toolkit:di` skill for how the
`DataSource` and the Redis connection got there.

**Do not install Cohort in `-core` or `-adapters`.** `cohort-ktor` is a deployment concern, so only `-app` depends on it — load the
`ktor-toolkit:architecture` skill. A custom `HealthCheck` class lives beside this file in `-app`; it may call into an adapter, but a port interface in
`-core` must never mention `HealthCheck`.

## What goes on which probe

| Probe           | Register                                                       | Never register                                       |
|-----------------|----------------------------------------------------------------|------------------------------------------------------|
| `/health/live`  | `ThreadDeadlockHealthCheck`, and little else                   | Anything that leaves the process — DB, cache, HTTP   |
| `/health/ready` | Dependencies the service **cannot serve core traffic without** | Optional dependencies you already degrade gracefully |

**Liveness must be process-local** — deadlocked threads, or nothing at all. If you cannot name a failure that only a restart fixes, a liveness check
that merely proves the event loop still accepts connections is the honest answer.

Handle the JVM's other unrecoverable state, heap exhaustion, with `-XX:+ExitOnOutOfMemoryError` rather than a memory check: a JVM in OOM churn can
still answer a probe. Load the `ktor-toolkit:container` skill.

**Readiness is about *core* traffic**, and the cache is where this bites. The `ktor-toolkit:cache` skill treats a cache failure as a warning, not a
request failure — Redis down means serve from the origin. So a dependency you deliberately degrade around must **not** be on readiness: putting it
there pulls every instance from the load balancer over a failure the code already handles. Redis belongs on readiness only when a miss cannot be
served at all — a rate limiter, a session store, a pub/sub fan-out the feature needs.

**A check that cannot fail is worse than no check**, because it is believed. On Cohort 2.9.x an empty registry reports unhealthy; older versions
returned a green 200 with an empty body forever.

## Built-in checks

Register these rather than writing your own — they are already correct about timeouts, dispatchers, and turning a throw into an unhealthy result.

| Check                                               | Module           | Use for                                                              |
|-----------------------------------------------------|------------------|----------------------------------------------------------------------|
| `ThreadDeadlockHealthCheck()`                       | `cohort-api`     | Liveness. Default `maxDeadlocks = 0`; any deadlock is a problem.     |
| `DatabaseConnectionHealthCheck(ds, timeout, query)` | `cohort-api`     | Readiness. JDBC4 `isValid` plus an optional query.                   |
| `HikariConnectionsHealthCheck(ds, minConnections)`  | `cohort-hikari`  | Readiness during startup — the pool has actually opened connections. |
| `RedisClusterHealthCheck(conn)`                     | `cohort-lettuce` | Readiness, when Redis is load-bearing.                               |
| `TcpHealthCheck(host, port)`                        | `cohort-api`     | A dependency with no client library worth booting.                   |
| `EndpointStartupHealthCheck(client) { … }`          | `cohort-ktor`    | Smoke-testing an upstream **once** at startup.                       |
| `EndpointHealthCheck(client) { … }`                 | `cohort-ktor`    | Rarely. Read the warning below first.                                |
| `DiskSpaceHealthCheck.root(minFreeSpacePercentage)` | `cohort-api`     | Services that write to disk. Most do not.                            |
| `FreememHealthCheck.mb(n)`                          | `cohort-api`     | Almost never — see above.                                            |

**`EndpointHealthCheck` against another service is how a cascade starts.** A marks itself unready because B is unready because C is restarting, and
one leaf outage takes down the graph. Use `EndpointStartupHealthCheck` instead: it probes once and then reports healthy forever without touching the
network again — a configuration smoke test, not a liveness signal for someone else's service. Let a real upstream failure surface as a 502/503 on the
request path, where `problem-details` can describe it.

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

**Bound the check yourself.** Cohort 2.9.x applies a `checkTimeout` (10 seconds, settable on the registry); 2.8 applies none, so a check hanging on a
socket read never completes and its status silently stays at whatever it last was. Use a `withTimeout` *much* shorter than the check interval — a
2-second budget on a 10-second interval.

**Catch `Exception`, not `Throwable`, and rethrow `CancellationException`.** `runCatching` catches cancellation too, so a shutting-down registry — or
a `checkTimeout` firing — is reported as a dependency failure instead of propagating. Catch the timeout you asked for; rethrow the cancellation you
did not.

**The `name` is the JSON key and must be unique per registry** — a duplicate throws at startup. Use `snake_case` and name the dependency, not the
class. Two checks over one connection are fine when named differently, which is what the `register(name, check, …)` overload is for.

**A check is read-only and cheap.** No writes, no `SELECT` over a real table, nothing that takes a lock. It runs forever, on every replica.

`HealthCheck` is a `fun interface`, so a trivial one can be a lambda — but a named class is testable on its own.

## The HTTP contract

| Registry state      | Status | Body (`verboseHealthCheckResponse = true`) | Body (`false`)        |
|---------------------|--------|--------------------------------------------|-----------------------|
| Every check healthy | `200`  | JSON array, one object per check           | `OK`, `text/plain`    |
| Any check unhealthy | `503`  | Same array, failing entries included       | `Service Unavailable` |

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

`consecutiveFailures` is why you do **not** debounce inside a check. One failed tick flips the endpoint to 503 immediately, and the orchestrator's own
`failureThreshold` decides whether that matters.

Two surprises in this response:

**`cause` is a full stack trace.** Verbose responses are an internal diagnostic, not a public status page. Keep verbose on for an internal service;
turn it off, or keep the path off the internet, for anything reachable from outside.

**Cohort writes the body itself**, through its own Jackson mapper and `respondText` — bypassing `ContentNegotiation`, your `Json` configuration and
the toolkit's `problem-details` mapping. A 503 from here is deliberately *not* `application/problem+json`. Do not try to make it one; it is a
machine-to-machine signal, not an API response.

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

**The startup probe stops a slow boot becoming a restart loop.** While it is failing, liveness is suspended entirely, so class loading, pool warm-up
and the first Flyway run cannot be mistaken for a wedged process. Better than a long `initialDelaySeconds` on liveness, which stays long forever. If
the number has to be large, the AOT and CDS sections of the `ktor-toolkit:container` skill are the real fix.

**Readiness must fail *before* shutdown finishes.** During a rolling deploy the endpoint keeps returning 200 while Ktor drains — correct, since
in-flight requests must complete — but the load balancer needs seconds to stop sending new ones. `shutdownGracePeriod` in `application.yaml` covers
that window; load the `ktor-toolkit:container` skill.

**Never point a liveness probe at `/health/ready`.** It is the one-endpoint mistake, written across two YAML keys.

## The other Cohort endpoints

Cohort also serves diagnostics under `endpointPrefix` (default `/cohort`), each behind its own flag, each **off** unless enabled:

| Flag              | Path                                | Exposes                                             |
|-------------------|-------------------------------------|-----------------------------------------------------|
| `jvmInfo`         | `/cohort/jvm`                       | JVM version, flags, uptime                          |
| `gc`              | `/cohort/gc`                        | Collector counts and times                          |
| `memory`          | `/cohort/memory`                    | Heap and buffer pool usage                          |
| `threadDump`      | `/cohort/threaddump`                | Full thread dump                                    |
| `dataSources`     | `/cohort/datasources`               | Pool sizes and waits (`HikariDataSourceManager`)    |
| `migrations`      | `/cohort/dbmigration`               | Applied migrations (`FlywayMigrations(dataSource)`) |
| `logManager`      | `/cohort/logging`, `PUT …/{n}/{lv}` | Reads **and changes** log levels (`LogbackManager`) |
| `sysprops`        | `/cohort/sysprops`                  | Every system property                               |
| `heapDump`        | `/cohort/heapdump`                  | A full `.hprof` of the running heap                 |
| `operatingSystem` | `/cohort/os`                        | Host OS details                                     |

`/cohort/datasources` and `/cohort/dbmigration` pay for themselves — "is the pool exhausted?" and "did this deployment run the migration?" are real
incident questions. Load the `ktor-toolkit:migrations` skill for the Flyway side.

**These are not public endpoints.** `heapdump` streams every token, password and personal record currently in memory, in a format that opens in a GUI.
`sysprops` prints anything passed as `-D`, credentials included. `PUT /cohort/logging/{name}/{level}` lets an unauthenticated caller switch a package
to `TRACE` — both a denial-of-service and a way to make the application log the request bodies the `ktor-toolkit:logging` skill forbids.

Enable them deliberately and put them behind something. If the ingress exposes only the probe paths, `install(Cohort)` is fine. Where the diagnostics
need authentication, mount them yourself instead of installing the plugin:

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

It emits `cohort.healthcheck` tagged with `name`, `type` and `status`. It needs the registry as a value, so build the registries before
`install(Cohort)` rather than inline.

## Testing

**Unit-test the custom checks.** A `HealthCheck` is one suspend function and a mockable collaborator, so its failure paths — timeout, exception, wrong
response — are cheap to pin down, and they are the paths that only run during an incident:

```kotlin
test("reports unhealthy when the index is unreachable") {
    val client = mockk<SearchClient> { coEvery { ping() } throws SocketTimeoutException() }

    SearchIndexHealthCheck(client).check().status shouldBe HealthStatus.Unhealthy
}
```

**Do not assert `200` on the endpoint straight after boot.** A fresh application is unhealthy by design until every check has run once, so 503 is
correct there. Assert the endpoint is reachable, or register with a very short `initialDelay` in the test module — never a sleep. Load the
`ktor-toolkit:tests` skill.

**Each registry starts a scheduler thread and a JVM shutdown hook.** A `testApplication` booting the full `module()` creates one per test: tolerable
for a handful of acceptance tests, a leak across hundreds. Narrow route tests should not install Cohort at all.

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

## Mistakes that turn an incident into a bigger one

The first three are the ones that make a partial outage total. Check those on any probe you touch, even when the task was something else.

| Mistake                                                     | What it does                                                              |
|-------------------------------------------------------------|---------------------------------------------------------------------------|
| One endpoint serving liveness and readiness                 | Every dependency blip restarts every instance you have                    |
| A database or cache check on **liveness**                   | The same, with a specific cause                                           |
| Readiness failing on a dependency the code degrades without | Pulls the fleet from the load balancer over a handled failure             |
| `EndpointHealthCheck` against an upstream service           | One leaf outage cascades through the graph; use the startup variant       |
| An empty registry                                           | A probe that can never fail, and is believed                              |
| `runCatching` inside `check()`                              | Swallows cancellation; shutdown and `checkTimeout` read as failures       |
| No `withTimeout` in a custom check                          | It can hang forever and the status silently goes stale                    |
| Throwing out of `check()`                                   | The operator reads Cohort's generic message instead of yours              |
| `heapDump`, `sysprops` or `logManager` reachable publicly   | Ships the heap and every `-D` credential, or lets anyone force `TRACE`    |
| A long `initialDelay`, or a 1-second interval               | That many seconds of 503 per deploy; or constant load, from every replica |
| Asserting `200` immediately after boot in a test            | Checks have not run yet, so unhealthy is the correct answer               |
| Expecting `problem+json` from a 503 here                    | Cohort writes its own body; `ContentNegotiation` is bypassed              |
