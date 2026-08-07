---
name: makefile
description: >-
  A Makefile as the one list of things you can do to a service — the canonical target set (build,
  test, coverage, lint, format, run, image, clean), a portable preamble, and a self-documenting help
  target. Use when creating a Makefile, adding or renaming a target, wiring CI to the same commands
  a developer runs, or when "how do I build / test / run this?" has no one-command answer.
---

# The Makefile

## What it is for

Nobody should have to know Gradle's task names to work on a service, and CI should not know a different set of commands from the ones a human runs.
The Makefile is the single list of things you can do to the repository, and it is the answer to "how do I run the tests?" for both audiences.

The second half is what pays. When CI calls `make lint` and `make build` rather than assembling its own Gradle invocations, a green local build is a
green CI build — and when it is not, the failure reproduces with one command instead of a reading of the workflow file.

## When the project has no Makefile

Most projects do not start with one, and its absence is not a problem to fix in passing. The trigger to raise it is a **second** hand-assembled
command: the moment you find yourself telling the user to run `./gradlew :report:koverHtmlReport :report:koverVerify`, or reconstructing a Gradle
invocation a CI workflow already spells out, the file has earned its place.

**Offer it, then wait.** Say which targets you would add and that CI would call the same ones. Do not create a Makefile as a side effect of an
unrelated task — it is a change to how everyone runs the project, and a team using `just`, npm scripts, or plain Gradle aliases has already made this
decision differently.

Where the project uses a different runner, adopt it: the value here is one documented entry point per action, not the word `make`.

## The preamble

Start it the same way every time, and each line earns its place:

```make
# Arguments: use 'th' to define the number of workers in Gradle execution.
## Sample: $ make build th=4
th = 10

# Determine the OS and set the Gradle command accordingly
ifeq ($(OS),Windows_NT)
  gradle_cmd := .\gradlew.bat
else
  gradle_cmd := ./gradlew
endif

.DEFAULT_GOAL := help
.PHONY: help setup clean build test test_acceptance coverage lint format verify run up down image image_run
```

**`th`** lets someone cap parallelism without editing the file — useful on a laptop that is also running a container or two.

**The OS branch** is why a Windows developer does not need a different set of instructions.

**`.PHONY`** for every target. None of them produce a file of their own name, and without it a directory called `test` or `build` — which Gradle
creates — silently stops the target from running. This is the classic Makefile bug and it appears only after the first successful build.

**`.DEFAULT_GOAL := help`** so a bare `make` prints the menu instead of running the first target by accident.

## A self-documenting help

Discoverability is the point of the file, so let it list itself. Annotate each target with `##` and generate the menu:

```make
help: ## List the available targets
	@grep -E '^[a-zA-Z_]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}'
```

The alternative — a README section listing the targets — is a second copy that goes stale. A target whose description lives on the target itself
cannot.

## The target set

Use these names. The value of a shared Makefile is that `make build` means the same thing in every repository someone works in this week.

```make
setup: ## Make the Gradle wrapper executable
	chmod +x ./gradlew

clean: setup ## Remove all build output
	$(gradle_cmd) clean

# ─── Build and test ────────────────────────────────────────────────────────

build: setup ## Compile, test and lint every module
	$(gradle_cmd) build --parallel --max-workers=$(th)

test: setup ## Run the unit and integration tests
	$(gradle_cmd) test --parallel --max-workers=$(th) -x :acceptance-tests:test

test_acceptance: setup ## Run the acceptance tests only
	$(gradle_cmd) :acceptance-tests:test

coverage: setup ## Aggregate coverage; report at report/build/reports/kover/html/index.html
	$(gradle_cmd) :report:koverHtmlReport :report:koverVerify

# ─── Quality ───────────────────────────────────────────────────────────────

lint: setup ## Check formatting and style
	$(gradle_cmd) ktlintCheck

format: setup ## Apply ktlint formatting
	$(gradle_cmd) ktlintFormat

verify: lint build coverage ## Run every check CI runs, cheapest failure first

# ─── Local development ─────────────────────────────────────────────────────

run: setup ## Run the application
	$(gradle_cmd) :catalog-app:run

up: ## Start the local dependencies (database, cache)
	docker compose up -d

down: ## Stop the local dependencies
	docker compose down

# ─── Container ─────────────────────────────────────────────────────────────

image: ## Build the runtime image
	docker build -t catalog:local .

image_run: image ## Run the image against the local dependencies
	docker run --rm -p 8080:8080 --env-file .env catalog:local
```

Adjust the module names; keep the target names.

**`verify` is what Phase 4 of the `ktor-toolkit:start` skill runs**, and what CI's steps add up to. Ordering it `lint`, `build`, `coverage` means the
cheapest failure surfaces first — a formatting slip should not cost a full test run to discover.

**A project that publishes a library adds two more**, and a service adds neither:

```make
api: setup ## Refresh the public API dumps after an intentional change
	$(gradle_cmd) apiDump

api_check: setup ## Fail when a public signature drifts from the committed dumps
	$(gradle_cmd) apiCheck

publish_local: setup ## Install into the local Maven repository
	$(gradle_cmd) publishToMavenLocal
```

Where those exist, `api_check` belongs in `verify` too, before `build`.

## Naming

**snake_case, and name the action.** `api_check`, `test_acceptance`, `image_run`. Not `apiCheck`, not `test-acceptance`.

**Say what it does, not what it is.** `build` compiles and tests; `install` does not install anything and is a name inherited from other ecosystems —
prefer `build`. `format` applies formatting; `lint_format` reads as a variety of linting rather than the action it performs. Where you meet the older
names in an existing repository, leave them rather than breaking everyone's muscle memory, but start new files from the set above.

**Qualify with a suffix, not a prefix.** `test_acceptance` sorts next to `test` in `make help`;
`acceptance_test` does not. The same reasoning gives `publish_local` and `image_run`.

## Rules that keep it useful

**Every target that touches Gradle depends on `setup`.** A fresh clone on a machine where the wrapper lost its executable bit works on the first try,
which is exactly when someone new to the project is least willing to debug it.

**CI calls make targets, not Gradle.** One target per CI step, so a failure names itself in the step list rather than sending someone to read a shell
script. That is also what keeps the two in step:
there is nothing to keep in step.

**Do not wrap what nobody runs.** A target per Gradle task turns the menu into noise. Add one when someone has typed the underlying command twice.

**Group with section comments**, in the order someone meets them: setup, build and test, quality, local development, container, distribution.
`make help` reads top to bottom, so the order is the documentation.

**Document the non-obvious in a comment above the target**, beyond the `##` one-liner — where a report lands, what a flag is protecting:

```make
# Fails when a public signature drifts from the dumps committed under */api.
api_check: setup ## Verify the public API against the committed dumps
```

## Docker targets

Keep two concerns apart, because they fail for different reasons:

**`up` / `down`** manage the *dependencies* — Postgres, Redis — through `docker compose`. These are what someone runs once in the morning, and they do
not depend on `setup`.

**`image` / `image_run`** build and run *this service's* image. The `ktor-toolkit:container` skill owns the Dockerfile — load it; the Makefile only
needs to name the tag and pass the environment. If there is no Dockerfile yet, that skill covers offering one rather than writing a target that cannot
run.

Keeping `run` (on the JVM, with a debugger and hot reload available) separate from `image_run` (in the container, as production sees it) matters —
they answer different questions, and collapsing them costs a fast feedback loop.

## Common mistakes

| Mistake                                  | Why it hurts                                                             |
|------------------------------------------|--------------------------------------------------------------------------|
| No `.PHONY`                              | A `build` or `test` directory silently disables the target               |
| CI running Gradle directly               | Local and CI drift, and a CI failure does not reproduce with one command |
| Target names differing per repository    | The shared vocabulary is the whole value                                 |
| A Makefile added as a task's side effect | Changes how everyone runs the project, undiscussed                       |
| A target for a Dockerfile that is absent | `make image` fails on a file nobody was told to write                    |
| A README list of commands                | A second copy that goes stale; use `##` and `make help`                  |
| A target per Gradle task                 | The menu becomes noise and nobody reads it                               |
| Targets not depending on `setup`         | A fresh clone fails on a permission bit                                  |
| `run` that also builds an image          | Slow feedback for the loop people use most                               |
| Hard-coded `--max-workers=10`            | Unusable on a small machine; keep `th`                                   |
| Secrets inline in an `image_run` command | They land in shell history and in the file                               |
