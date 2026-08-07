---
name: container
description: >-
    Production Docker images for a Ktor service — a multi-stage jdeps/jlink Dockerfile, choosing a
    runtime base, container-aware JVM flags (MaxRAMPercentage, CPU quotas, GC), non-root users,
    graceful SIGTERM shutdown and layer caching. Use when writing or reviewing a Dockerfile, when an
    image is too large or slow to start, when the JVM gets OOM-killed, or when a deploy drops
    in-flight requests.
---

# Containers

## When there is no Dockerfile

A service with no Dockerfile is either deployed some other way — a fat jar on a VM, a buildpack, Fly or Heroku building it for you — or not deployed
yet. Both are fine, and neither is something to change while doing an unrelated task.

**Ask before writing one.** How the service is packaged and shipped is a platform decision with a team behind it, and a Dockerfile that appears in a
feature branch will be reviewed by someone who did not expect it. When it is genuinely wanted, say what you would produce — the jlink multi-stage
build below, roughly this size, this base image — and wait.

Two things worth writing whenever you do create one: a `.dockerignore` (without it the build context is the whole tree including `build/` and `.git`,
which is slow and leaks more than you think) and the `make image` / `make image_run` targets from the `ktor-toolkit:makefile` skill, so the image is
buildable the same way by everyone.

## What we are optimising, in order

1. **Correctness under a memory limit.** A JVM that ignores the cgroup limit gets OOM-killed by the kernel with no stack trace and no heap dump.
   Everything else is secondary to this.
2. **Graceful shutdown.** A rolling deploy that drops in-flight requests is a user-visible outage caused by a Dockerfile.
3. **Startup time.** It sets how fast you can scale out and how long a rollback takes.
4. **Attack surface.** Fewer packages, no shell, not root.
5. **Image size and rebuild speed.** Real, and last — a 200 MB image that behaves correctly beats a 90 MB one that does not.

Teams usually optimise these in reverse. Size is the most visible and the least important.

## Choosing the runtime base

**Match the image tag to the project's `jvmToolchain`.** A jar built for 25 will not start on a 21 runtime, and the error —
`UnsupportedClassVersionError` — names bytecode versions rather than the mismatch. The tags below say `25`; substitute whatever the build targets, and
keep the two in step when either moves. Ktor Toolkit needs 21 or newer, so that is the floor, not the target.

| Base                                                     | Size       | When                                                                                                                           |
|----------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------------------------------------|
| jlink runtime on `debian:bookworm-slim` / `ubuntu:noble` | ~90–120 MB | **Start here.** Smallest practical while keeping glibc, and you choose exactly what ships.                                     |
| `eclipse-temurin:25-jre-noble`                           | ~180 MB    | When nobody wants to maintain a module list. glibc, `apt` available, easy to debug.                                            |
| `gcr.io/distroless/java25-*`                             | ~190 MB    | Hard security requirement. No shell, no package manager — and no way to debug inside the container.                            |
| `eclipse-temurin:25-jre-alpine`                          | ~130 MB    | Only when you have tested it. musl is not glibc: DNS resolution, locale handling and some native libraries behave differently. |
| Liberica Runtime Container                               | varies     | Good pre-built middle ground, and the practical route to CRaC.                                                                 |
| Any `-jdk-` image                                        | ~400 MB+   | Never in the runtime stage. It ships a compiler you do not run.                                                                |

**Recommendation: build a jlink runtime and put it on a slim glibc base.** It is the smallest image you can produce without changing libc, the module
list is an explicit, reviewable statement of what the service depends on, and when it is wrong it fails loudly at startup rather than subtly at
runtime.

Alpine is the usual trap. The saving is ~50 MB and the cost is a class of bug that only appears in production, in the DNS and TLS paths.

## The Dockerfile

```dockerfile
# ---------------------------------------------------------------------------
# Stage 1 – jdeps: discover the Java platform modules the jar actually uses
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk-noble AS jdeps

COPY catalog-app/build/libs/catalog-app-all.jar /tmp/app.jar

# --multi-release base  avoids errors from multi-release jars
# --ignore-missing-deps skips optional/compile-only deps that are not shipped
RUN jdeps --print-module-deps --ignore-missing-deps --multi-release base \
      /tmp/app.jar > /tmp/modules.txt

# ---------------------------------------------------------------------------
# Stage 2 – jlink: build a minimal custom runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk-noble AS jlink

COPY --from=jdeps /tmp/modules.txt /tmp/modules.txt

# jdeps only sees compile-time bytecode references. These are loaded reflectively,
# through an SPI or via JNI, so it cannot find them:
#   java.logging      – SLF4J→JUL bridge and Logback internals
#   java.xml          – Logback's logback.xml parser
#   java.management   – JMX beans (health checks, Netty metrics)
#   jdk.naming.dns    – JNDI DNS provider; container DNS resolution
#   jdk.crypto.ec     – TLS ECDHE cipher suites
#   jdk.zipfs         – ZipFileSystem provider, for reading nested jars
#   jdk.localedata    – CLDR locale and timezone data
ENV EXTRA_MODULES="java.logging,java.xml,java.management,jdk.naming.dns,jdk.crypto.ec,jdk.zipfs,jdk.localedata"

RUN jlink \
      --add-modules "$(cat /tmp/modules.txt),${EXTRA_MODULES}" \
      --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
      --output /javaruntime

# ---------------------------------------------------------------------------
# Stage 3 – runtime
# ---------------------------------------------------------------------------
FROM debian:bookworm-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --no-create-home app

COPY --from=jlink /javaruntime /opt/java
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /app
COPY --chown=1001:1001 catalog-app/build/libs/catalog-app-all.jar app.jar

USER 1001
EXPOSE 8080

# Container-aware defaults. Override JAVA_OPTS per environment; do not edit this line.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 \
-XX:+ExitOnOutOfMemoryError \
--enable-native-access=ALL-UNNAMED"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
```

Points that are not decoration:

**`USER 1001`, numeric.** Kubernetes' `runAsNonRoot` check reads the numeric uid; a `USER app` that resolves to a name fails that admission check even
though the user is unprivileged. The `--chown` on the `COPY` matters too — a root-owned jar is readable, but anything the app writes will not be.

**`exec java`.** The `exec` replaces the shell so the JVM becomes PID 1 and receives SIGTERM directly. Without it the shell is PID 1, signals stop
there, and every deploy waits out the 10-second kill timeout. If you do not need `$JAVA_OPTS` expansion, prefer the plain exec form
`ENTRYPOINT ["java", "-jar", "app.jar"]`.

**`ca-certificates`** is the one package almost every service genuinely needs; without it every outbound HTTPS call fails certificate validation. Add
`fontconfig` and `libfreetype6` only if something actually pulls in `java.desktop`.

## JVM configuration in a container

**Set the heap as a percentage, never as `-Xmx`.** A hard-coded `-Xmx512m` is wrong the moment the container limit changes, in both directions:

```
-XX:MaxRAMPercentage=75
-XX:InitialRAMPercentage=50
```

The default is **25%** — right for a machine running many processes, badly wrong for a container running one. Left alone, three quarters of the memory
you pay for goes unused and the JVM still gets OOM-killed under load, because heap is not the only thing it allocates. That is also why 75 and not 90:
metaspace, thread stacks, code cache, direct byte buffers and GC structures live outside the heap. Leave them a quarter.

**`-XX:+ExitOnOutOfMemoryError` is not optional.** A JVM that cannot allocate otherwise keeps running degraded — failing some requests, serving
others — while a liveness probe on a trivial endpoint keeps passing. Exiting turns an ambiguous failure into a restart the orchestrator understands.

**CPU.** Modern JVMs read the cgroup quota and size GC and compiler threads from it. Override only for a fractional quota: a limit of `0.5` floors to
one processor, silently selecting SerialGC and a single compiler thread. Set `-XX:ActiveProcessorCount=2` explicitly rather than hoping.

**GC: let the JVM choose.** It picks SerialGC below two processors or a small heap, G1 otherwise, and that is right for a service container. Forcing
G1 onto a one-CPU container is a common, measurable pessimisation. Override only for a latency target ZGC meets and G1 does not, with the cores to
make it worthwhile.

## Flags to delete

Every one of these is either the default now or was a workaround for a JVM you are not running. They survive by being copied between projects.

| Flag                                    | Why it goes                                                         |
|-----------------------------------------|---------------------------------------------------------------------|
| `-XX:+UseContainerSupport`              | Default since JDK 10                                                |
| `-Djava.security.egd=file:/dev/urandom` | A workaround for a blocking-entropy problem modern JDKs do not have |
| `-Dfile.encoding=UTF-8`                 | UTF-8 is the default since JDK 18 (JEP 400)                         |
| `-XX:+UseCGroupMemoryLimitForHeap`      | Removed in JDK 11; the JVM will refuse to start                     |
| `-XX:+UnlockExperimentalVMOptions`      | Almost always present only to enable one of the above               |
| `-XX:+UseConcMarkSweepGC`               | CMS was removed in JDK 14                                           |
| `-Xmx` with a literal                   | Ignores the container limit; use `MaxRAMPercentage`                 |
| `-Djava.awt.headless=true`              | Nothing in a JSON API touches AWT                                   |
| `--compress=2` (jlink)                  | Deprecated; use `--compress=zip-6`                                  |

Worth keeping on JDK 24+: `--enable-native-access=ALL-UNNAMED`, which silences the restricted native-access warnings that Netty and similar libraries
otherwise produce on every start.

**Set the percentage, set the OOM behaviour, and stop.** The JVM knows more about its environment than a flag copied from a 2016 blog post, and every
flag you keep is one to re-justify at the next upgrade.

## Startup

Class loading and linking dominate JVM startup, and both have a modern answer.

**AOT cache (JDK 24+, simplest on 25).** A training run records what the application loads; the cache is replayed on every subsequent start:

```dockerfile
RUN java -XX:AOTCacheOutput=/app/app.aot -jar app.jar --training-exit
```

```
-XX:AOTCache=/app/app.aot
```

Typically 30–50% off startup. The training run must exercise a realistic path — a JVM that started and exited immediately caches nothing worth
having — and it needs the same JDK version and roughly the same flags at runtime. Regenerate it in the image build, never by hand.

**AppCDS (JDK 19+)** is the fallback where AOT is unavailable, and it is one flag:

```
-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=/tmp/app.jsa
```

Created on first run, reused after, and regenerated when the classpath changes. On a read-only filesystem, point it at a writable volume or generate
it at build time.

**CRaC** — checkpoint a warmed process, restore in milliseconds — is a large win but needs a CRaC-enabled JDK (Liberica, Azul), privileged `criu` at
checkpoint time, and application code handling the callbacks for anything holding a connection. Reach for it when startup is genuinely the constraint.

## Graceful shutdown

Three things must all be true, and the failure looks identical if any one is missing.

**The JVM must be PID 1** — the exec form, or `exec` inside a shell entrypoint. Otherwise SIGTERM goes to the shell and the JVM is killed on the
timeout.

**Ktor must be told how long to drain**, in `application.yaml`:

```yaml
ktor:
    deployment:
        shutdownGracePeriod: 5000
        shutdownTimeout: 15000
```

`shutdownGracePeriod` is how long the server keeps serving after being asked to stop; `shutdownTimeout` is when it stops waiting. Both must fit inside
the orchestrator's grace period — Kubernetes' `terminationGracePeriodSeconds` defaults to 30, so a 15-second Ktor timeout is comfortable and 45 is
pointless.

**Resources must close on the way out.** Connection pools and HTTP clients need a `cleanup` in the DI registration, or shutdown drops connections it
was supposed to return — load the `ktor-toolkit:di` skill.

If the service spawns child processes, add `--init` (or `tini`) so PID 1 reaps them. A plain Ktor service does not need it.

## Health checks

**Use the orchestrator's probes, not `HEALTHCHECK`.** Kubernetes ignores `HEALTHCHECK` entirely, and Docker's cannot distinguish liveness from
readiness — the distinction that matters. Which check belongs on which probe is the whole subject of the `ktor-toolkit:healthcheck` skill; load it
before writing either.

Where a `HEALTHCHECK` is genuinely required, remember a jlink or distroless image has no `curl`. Use the JDK's own client rather than adding a package
for it:

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
  CMD java -e 'java.net.http.HttpClient.newHttpClient().send(...)' || exit 1
```

Expose the endpoint, let the orchestrator call it, and omit `HEALTHCHECK`.

## Layers and build speed

Docker caches by layer, so order from least to most likely to change: base and packages, then the runtime, then dependencies, then your code.

**A fat jar defeats this.** Your classes and every dependency share one 60 MB layer, so a one-line change rebuilds and re-pushes all of it. Where push
time matters, use `installDist` and split them:

```dockerfile
COPY catalog-app/build/install/catalog-app/lib/ /app/lib/     # dependencies, change rarely
COPY catalog-app/build/libs/catalog-app.jar /app/lib/         # your code, changes constantly
```

**Build the jar outside the image.** CI already ran `make build`; rebuilding inside Docker discards the Gradle cache and doubles the pipeline. `COPY`
what `make build` produced.

**Write a `.dockerignore`.** Without one, `build/`, `.git/` and `.gradle/` go to the daemon on every build — often hundreds of megabytes, and enough
to invalidate the cache by themselves.

## Mistakes that surface only in production

| Mistake                                 | What it does                                                        |
|-----------------------------------------|---------------------------------------------------------------------|
| No `MaxRAMPercentage`                   | The JVM uses 25% of the limit, then gets OOM-killed anyway          |
| No `ExitOnOutOfMemoryError`             | A degraded process keeps passing liveness and failing requests      |
| Shell-form `ENTRYPOINT` without `exec`  | SIGTERM stops at the shell; every deploy waits out the kill timeout |
| `USER app` instead of `USER 1001`       | Kubernetes cannot verify a non-numeric user, and admission fails    |
| Alpine adopted untested                 | musl breaks DNS, locales and some native libraries                  |
| An image tag below the build's target   | `UnsupportedClassVersionError` at start, naming bytecode versions   |
| A liveness probe that checks a database | One blip restarts every instance                                    |
| Legacy flags copied forward             | No-ops, or for removed ones a JVM that will not start               |
| No `.dockerignore`                      | `build/` and `.git/` uploaded on every build                        |
