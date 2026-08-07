---
name: commit
description: >-
  Conventional Commits as this project writes them — the type/scope/subject line, the body that
  says what was wrong and why this change is the fix, breaking-change markers, and how to split
  work so each commit is exactly one logical change. Consult this before every commit and before
  writing any commit message, when staging a change that mixes a refactor with a feature or
  formatting with behaviour, when a change breaks a public signature, and when deciding whether
  something belongs in one commit or several.
---

# Commits

## Why the bar is high here

A commit message is the only explanation that travels with the code. Six months on, `git blame` on a puzzling line is the fastest route to why it is
that way — but only if the message says why. "Fix validation" answers nothing that the diff did not already show.

So the rule this project holds to: **the subject says what changed; the body says what was wrong and why this change is the fix.** The diff already
shows how.

## The format

```
type(scope)!: subject

Body: what was wrong, then why this is the fix.

BREAKING CHANGE: what consumers must do differently.
```

### Type

| Type       | For                                          |
|------------|----------------------------------------------|
| `feat`     | New behaviour a consumer can use             |
| `fix`      | Behaviour that was wrong and now is not      |
| `refactor` | Same behaviour, different shape              |
| `test`     | Tests only                                   |
| `docs`     | Documentation only — README, KDoc, CHANGELOG |
| `chore`    | Build, tooling, IDE files, dependencies      |
| `ci`       | Workflow files                               |
| `perf`     | Faster, same behaviour                       |

If two types both fit, the commit is doing two things. Split it.

### Scope

The area the change belongs to, in parentheses. In this repository that is the module without its prefix — `validator`, `cache`, `expander`,
`hateoas`, `paginator`, `problem-details` — or `build` for the Gradle layer. In a service it is the feature: `books`, `auth`, `search`.

Several scopes are allowed when one change genuinely lands across them, comma-separated:

```
feat(hateoas,paginator,problem-details)!: round out the remaining DSLs
```

Omit the scope when the change is repository-wide (`docs: describe the reworked DSLs`). Do not invent one to look thorough.

### Subject

Imperative mood, lower case, no trailing period, under about 70 characters. It should complete "this commit will…":

```
refactor(validator)!: split rules into rulesFor and rulesFrom
ci: move off actions still running on Node 20
test: reach full line and branch coverage
fix: correct pagination math, query parsing, null validation and cancellation
```

Not `Fixed the thing.`, not `updates`, not `WIP`.

## The body

This is where the project differs from the average Conventional Commits repository, and it is the part worth taking seriously.

**Open with what was wrong.** Not what you did — what the state of the world was that made this necessary:

```
ci: add GitHub Actions build and release workflows

The build was only ever verified on a contributor's machine, so nothing stopped
a branch from landing with a lint, coverage or API-dump regression.

CI runs on pull requests and pushes to main through the Makefile targets a
contributor already uses, one target per step so a failure names itself in the
step list. …
```

That first paragraph is what a reader needs before the diff means anything.

**Then why this change is the fix**, including the reasoning behind choices someone might otherwise undo. "one target per step so a failure names
itself in the step list" is a decision with a reason; without it the next person consolidates the steps and loses the property.

**Show the API when it changed.** An indented snippet in the body is worth a paragraph of prose:

```
refactor(validator)!: split rules into rulesFor and rulesFrom

One shared name papered over two different relationships to the argument. The
block form's type parameter is what the rules are *for*; the validator form's
argument is where the rules come *from*…

    install(RequestValidation) {
        rulesFor<CreateBookRequest> {
            property(CreateBookRequest::title) { should notBe blank() }
        }

        rulesFrom(CreateBookValidator())
    }
```

**Call out incidental changes and say they are incidental.** A reviewer who sees an unexplained simplification inside a test commit has to work out
whether behaviour moved:

```
Several safe-call chains are simplified along the way. Each had a second null
check that could not fail once the first had passed… Behaviour is unchanged.
```

**A body is optional for genuinely mechanical commits.** `chore(build): rename publish target to
publish_local` needs nothing more. But if you cannot write a body for a `feat` or a `fix`, the subject is probably hiding something.

Wrap the body at 72–80 columns and use bullets for a list of independent points.

## Breaking changes

Mark them twice: a `!` before the colon, and a `BREAKING CHANGE:` footer saying what a consumer must now do.

```
refactor(validator)!: split rules into rulesFor and rulesFrom

…

BREAKING CHANGE: `RequestValidationConfig.rules` is now `rulesFor` for the
block form and `rulesFrom` for the `RequestValidator` form.
```

The footer is for the person whose build just broke, so name the old thing and the new one. In this repository a breaking change also goes in
`CHANGELOG.md` under the current version, and the refreshed API dump belongs in the same commit — see below.

## One logical change

A commit should be revertable on its own without taking anything unrelated with it. That is the test to apply, and it is stricter than "the change
works".

**Separate a refactor from a feature.** Moving `InMemoryCache` into its own file and adding a Redis cache are two commits, in that order:

```
feat(cache): add a Lettuce-backed Redis KeyValueCache
refactor(cache): move InMemoryCache into its own file
```

Mixed together, a reviewer cannot tell which lines are the new behaviour and which are the same code in a new place — and a diff of 400 lines gets the
review a diff of 40 deserved.

**Separate formatting from behaviour.** A `make format` run touching thirty files, committed alongside a fix, buries the fix. Reformat first, commit
that alone, then change behaviour.

**Separate mechanical renames.** A rename across forty files is its own commit, always. It is easy to review as a rename and impossible to review when
interleaved.

**Prefer smaller.** Two commits that each make sense beat one that needs a paragraph to justify its shape. The upper bound is not lines changed; it is
whether a single sentence describes the commit honestly without an "and".

If a subject needs "and", check whether it is one change described in parts — `fix: correct
pagination math, query parsing, null validation and cancellation` is a defensible list of related corrections in one area — or two changes wearing one
hat. The former is fine; the latter is the usual case.

## Before you commit

In this repository, run what CI runs:

```bash
make lint
make build
```

**A public signature change carries its API dump.** `make api` regenerates the files under `api/`, and that diff belongs in the same commit as the
change that caused it — it is the clearest statement of what a consumer will feel, and a reviewer should see the two together. A commit that changes a
signature without its dump fails `apiCheck` on CI anyway.

Coverage is gated at 100% line and branch, so a new branch of logic arrives with its test. A bug fix arrives with the test that fails without it (load
the `ktor-toolkit:tests` skill).

In a service, the equivalent gates are whatever `make build` runs there — load the `ktor-toolkit:makefile` skill.

## Common mistakes

| Mistake                                  | Why it hurts                                               |
|------------------------------------------|------------------------------------------------------------|
| `fix: bug` / `update code` / `wip`       | Says nothing the diff did not; `git blame` becomes useless |
| A body describing *what* the diff does   | The diff already shows that; say what was wrong            |
| Formatting mixed into a behaviour change | The real change hides inside a reformat                    |
| A refactor and a feature in one commit   | Neither can be reviewed or reverted on its own             |
| Breaking change without `!` or a footer  | Consumers find out from a compile error                    |
| An API dump in a follow-up commit        | The signature change and its consequence are separated     |
| Past tense (`Added support for…`)        | Inconsistent with every generated changelog convention     |
| A scope invented per commit              | Scopes stop grouping anything                              |
| Several unrelated fixes under one `fix:` | Reverting one means reverting all                          |
| `chore: address review comments`         | Names the process, not the change                          |
