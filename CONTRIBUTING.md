# Contributing

## Getting set up

You need a JDK 21. [mise](https://mise.jdx.dev) will install the right one:

```bash
mise install
```

Gradle otherwise provisions a Java 21 toolchain itself, so a plain `./gradlew build` works too.

## The loop

```bash
make build
```

That compiles every module, runs the tests and runs ktlint — the same thing a reviewer will run.
Narrower targets while iterating:

```bash
make test
make lint
make format
make coverage
```

## What the build enforces

**ktlint.** Style is pinned in `.editorconfig` (`ktlint_official`, 150 columns). `make format` fixes
almost everything; the rest the check will name.

**Coverage.** Kover gates the aggregate at 85% line and 65% branch. `make coverage` writes
`report/build/reports/kover/html/index.html`. A new module must be registered in
`report/build.gradle.kts` or its code will not be counted.

**Binary compatibility.** Every module has a committed API dump under `api/`. Changing a public
signature fails `apiCheck` until you refresh it:

```bash
make api
```

Review that diff before committing — it is the clearest statement of what a change does to consumers,
and it belongs in the same commit as the change.

## Conventions

- Public declarations carry KDoc. Say what the thing is for and what it does when the input is
  absent or wrong, not what the signature already says.
- Tests use Kotest `ShouldSpec` with MockK. Name the behaviour, not the method:
  `should("clamp values that are out of range")`.
- A bug fix comes with a test that fails without it.
- Commit messages explain what was wrong and why the change is the fix. Breaking changes go in
  `CHANGELOG.md` under the current version.

## Dependencies

Modules declare a dependency as `api` only when it appears in a public signature — that is what puts
it in the published POM for consumers. Anything a module merely uses internally is `implementation`.

Optional integrations (Exposed, gel-query-dsl) are `compileOnly`, so consumers who do not use them
do not pay for them. Add the matching `testImplementation` so the code is still compiled and tested
here, and document the requirement in the README.

## Releasing

No remote repository is configured. `make publish` installs into your local `~/.m2` repository:

```bash
make publish
```

Before releasing: update `version` in `gradle.properties`, move the `unreleased` heading in
`CHANGELOG.md`, and make sure `make build` and `make api` are both clean.
