---
name: commit
description: >-
    Conventional Commits with a body that says what was wrong and why this change is the fix — the
    type/scope/subject line, breaking-change markers, and how to split work so each commit is exactly
    one logical change. Use before writing any commit message, when staging a change that mixes a
    refactor with a feature or formatting with behaviour, and when a change breaks a public signature.
---

# Commits

## Why the bar is high

A commit message is the only explanation that travels with the code. Six months on, `git blame` on a puzzling line is the fastest route to why it is
that way — but only if the message says why. "Fix validation" answers nothing that the diff did not already show.

So the rule: **the subject says what changed; the body says what was wrong and why this change is the fix.** The diff already shows how.

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

The area the change belongs to, in parentheses. **In a service the scope is the feature** — `books`, `auth`, `search`, `orders` — because that is the
axis someone reads history along. `build` covers the Gradle layer, `deploy` the container and pipeline. Whatever set you pick, keep using it: a scope
vocabulary that grows one term per commit has stopped grouping anything.

Several scopes are allowed when one change genuinely lands across them, comma-separated:

```
feat(books,search)!: return search hits through the same paged envelope
```

Omit the scope when the change is repository-wide (`docs: describe the reworked DSLs`). Do not invent one to look thorough.

### Subject

Imperative mood, lower case, no trailing period, under about 70 characters. It should complete "this commit will…":

```
refactor(books)!: take Pagination at the repository port
ci: move off actions still running on Node 20
test: cover the paging boundaries the search route exposes
fix: correct page arithmetic, sort parsing and null handling in the catalogue
```

Not `Fixed the thing.`, not `updates`, not `WIP`.

## The body

**Open with what was wrong.** Not what you did — what the state of the world was that made this necessary:

```
ci: add GitHub Actions build and release workflows

The build was only ever verified on a developer's machine, so nothing stopped a
branch from landing with a lint or coverage regression.

CI runs on pull requests and pushes to main through the Makefile targets a
developer already uses, one target per step so a failure names itself in the
step list. …
```

That first paragraph is what a reader needs before the diff means anything.

**Then why this change is the fix**, including the reasoning behind choices someone might otherwise undo. "one target per step so a failure names
itself in the step list" is a decision with a reason; without it the next person consolidates the steps and loses the property.

**Show the API when it changed.** An indented snippet in the body is worth a paragraph of prose:

```
refactor(books)!: take Pagination at the repository port

`findAll(page, size)` threw the sort model away at the boundary, so every caller
that wanted an order sorted in memory afterwards — correct for one page, wrong
across pages, and quietly O(n) on the whole table…

    interface BookRepository {
        suspend fun findAll(pagination: Pagination): List<Book>
        suspend fun count(): Long
    }
```

**Call out incidental changes and say they are incidental.** A reviewer who sees an unexplained simplification inside a test commit has to work out
whether behaviour moved:

```
Several safe-call chains are simplified along the way. Each had a second null
check that could not fail once the first had passed… Behaviour is unchanged.
```

**A body is optional for genuinely mechanical commits.** `chore(build): bump Ktor to 3.4.1` needs nothing more. But if you cannot write a body for a
`feat` or a `fix`, the subject is probably hiding something.

Wrap the body at 72–80 columns and use bullets for a list of independent points.

## Breaking changes

Mark them twice: a `!` before the colon, and a `BREAKING CHANGE:` footer saying what the other side must now do.

**In a service, "breaking" almost always means the HTTP contract**, not a Kotlin signature. A removed field, a renamed query parameter, a status code
that moved, a response that grew an envelope — those break somebody's client, and nothing in your build will catch it:

```
feat(books)!: page the collection response

…

BREAKING CHANGE: `GET /books` returns an object with `content` and `metadata`
rather than a bare JSON array. Clients reading the array directly must read
`content` instead.
```

The footer is for the person whose client just broke, so name the old shape and the new one. A breaking change also goes in `CHANGELOG.md` under the
current version, in this same commit — load the `ktor-toolkit:changelog` skill.

An internal Kotlin signature that only your own modules call is not a breaking change; it is a `refactor`.

## One logical change

A commit should be revertable on its own without taking anything unrelated with it. That is the test to apply, and it is stricter than "the change
works".

**Separate a refactor from a feature.** Extracting the search filter out of the route body and adding a new filter field are two commits, in that
order:

```
refactor(search): lift the filter parsing out of the route body
feat(search): filter by publication year
```

Mixed together, a reviewer cannot tell which lines are the new behaviour and which are the same code in a new place — and a diff of 400 lines gets the
review a diff of 40 deserved.

**Separate formatting from behaviour.** A `make format` run touching thirty files, committed alongside a fix, buries the fix. Reformat first, commit
that alone, then change behaviour.

**Separate mechanical renames.** A rename across forty files is its own commit, always. It is easy to review as a rename and impossible to review when
interleaved.

**Prefer smaller.** Two commits that each make sense beat one that needs a paragraph to justify its shape. The upper bound is not lines changed; it is
whether a single sentence describes the commit honestly without an "and".

If a subject needs "and", check whether it is one change described in parts — `fix: correct page arithmetic, sort parsing and null handling in the
catalogue` is a defensible list of related corrections in one area — or two changes wearing one hat. The former is fine; the latter is the usual case.

## Before you commit

**Run whatever this project's gate is, and run it before the commit, not after.** Where the project follows the `ktor-toolkit:makefile` convention
that is:

```bash
make lint
make build
```

Elsewhere it is `./gradlew build`, or whatever the CI workflow calls. Load the `ktor-toolkit:makefile` skill if the project has no single command for
this and you find yourself assembling one by hand — that absence is worth offering to fix, once, rather than working around on every commit.

**A bug fix arrives with the test that fails without it**, and a new branch of logic arrives with the test that reaches it. Load the
`ktor-toolkit:tests` skill.

**The changelog entry is part of the commit, not a follow-up.** Load the `ktor-toolkit:changelog` skill for what earns one.

**If this project publishes a library**, a changed public signature also carries its refreshed `api/` dump in the same commit — it is the clearest
statement of what a consumer will feel, and `apiCheck` fails on CI without it. Services publish nothing and have no `api/` directory; skip this unless
the tree has one.

## Before you write the message, check

| Mistake                                                                         | What it costs                                      |
|---------------------------------------------------------------------------------|----------------------------------------------------|
| Breaking change without `!` or a footer                                         | Clients find out in production, from a parse error |
| A subject naming the process, not the change (`wip`, `address review comments`) | `git blame` answers nothing                        |
| A body describing *what* the diff does                                          | The diff already showed that; say what was wrong   |
| A refactor and a feature in one commit                                          | Neither can be reviewed or reverted on its own     |
| Formatting mixed into a behaviour change                                        | The real change hides inside a reformat            |
| Several unrelated fixes under one `fix:`                                        | Reverting one means reverting all                  |
| A scope invented per commit                                                     | Scopes stop grouping anything                      |
