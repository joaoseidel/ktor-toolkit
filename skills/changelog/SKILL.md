---
name: changelog
description: >-
    Keeping a CHANGELOG.md an upgrader can act on — the Keep a Changelog layout, the
    Breaking/Fixed/Added/Changed sections, what an entry has to say, and the rule that it lands in
    the same commit as the change. Use after any change a client or another team would notice,
    whenever the HTTP contract or a default moves, before every release, and when a bug fix is about
    to be committed.
---

# The changelog

## Who it is for

`CHANGELOG.md` is read by someone whose integration just broke, at the moment it broke. They are not reading it to admire the work; they are looking
for the line that explains what happened to them.

For a service, that reader is whoever calls your API — another team, a mobile client, a partner — plus whoever is on call at 3am wondering what
shipped. For a published library it is whoever bumped your version. Same file, same discipline, different blast radius.

That single fact settles most questions about it. **Write the entry from the caller's side**, not from the diff's side. "Refactored the search
adapter" is written from inside. "`GET /books?sortBy=` silently ignored an unknown field instead of rejecting it, so a typo returned unsorted results
that looked plausible" is written from outside — and it is the one that saves someone an afternoon.

The commit message and the changelog entry answer different questions and are not interchangeable. The commit explains the change to whoever maintains
the code; the changelog explains the consequence to whoever depends on it. Load the `ktor-toolkit:commit` skill for the other half.

## When there is no CHANGELOG.md

Plenty of services have never had one, and its absence is not a reason to skip the record — it is a thing to raise once.

When a change earns an entry and the file does not exist, **say so and offer to create it**: a `# Changelog` header, the Keep a Changelog and SemVer
links, and the current version's heading with this change under it. Then wait for a yes. Some teams keep release notes in the pull request, in a wiki,
or in the deploy tool, and a stray `CHANGELOG.md` competing with that is worse than none. Ask which they have before adding a second one.

## The format

[Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/), [SemVer](https://semver.org/spec/v2.0.0.html), wrapped at the same column as the rest
of the tree.

```markdown
## [1.4.0] — unreleased

### Breaking

- **`GET /books` returns a paged envelope.** The response is now an object with `content` and `metadata` rather than a bare array. Clients reading the
  array directly must read `content`.

### Fixed

- `first` and `last` page links pointed one page past the end, because the link builder read `totalPages` as an index while it held a count.

### Added

- `?sortBy=-publishedAt` on `/books`, alongside the existing `title` and `author`. Unknown fields are rejected with a 400 rather than ignored.

### Changed

- A validation failure now answers `application/problem+json` instead of an HTML error page.
```

**Version heading:** `## [x.y.z] — unreleased` while the version is open, with an em dash. On release the word `unreleased` becomes the date. The
newest version is at the top; nothing below it is ever edited again.

**Sections, in this order:** `Breaking`, `Fixed`, `Added`, `Changed`. `Removed`, `Deprecated` and `Security` exist in the Keep a Changelog vocabulary
and are fair to use, but a removal is almost always a breaking change and belongs under `Breaking` where an upgrader looks first. Omit any section
with no entries — an empty heading is noise.

`Breaking` leads because it is the only section that can stop something working.

**Version a service by what its API promises**, not by its deploy count. A removed response field is a major bump even though nobody compiled against
it. Where the team versions by date or by sprint instead, keep their scheme and keep the sections — the sections are the part doing the work.

## What an entry says

**A fix names what was wrong.** The behaviour the caller saw, and — when it is not obvious — the reason, because that is what tells them whether their
workaround can now be deleted:

```markdown
- `POST /books` accepted a blank `title`, because the rule was declared on a nullable property and never ran when the field was absent — records
  created before this fix keep their blank titles and need backfilling.
```

Not "fixed null handling in validation". That tells nobody whether it was their bug.

**A breaking entry opens with a bold sentence stating the new reality, then says what to do.** The bold line is what someone scanning the section
actually reads:

```markdown
- **`metadata.totalPages` now counts pages.** It previously held the *index* of the last page — 25 elements over a page size of 10 reported `2`. It
  now reports `3`; the last page index is `totalPages - 1`. Any client doing arithmetic on this field must be updated.
```

The worked example is doing real work there. A sentence about off-by-one is ambiguous; `2` becoming `3` is not.

**An addition names the route or the field and says what it is for**, in one line where one line is enough. Reach for a short snippet only when the
shape is not obvious from the name.

**Backtick every symbol, path, parameter and query string.** `?pageSize=0`, `GET /books/{id}`, `metadata.totalElements`. Prose that names a field
without backticks reads as prose about a concept, which is a different claim.

**Present tense, caller-facing subject.** "`GET /books` accepts `?expand=author`" — not "added support for expanding the author".

**Required actions are stated, not implied.** A change that needs a config value set, a migration run, or a client updated says so in the entry. The
reader is deciding whether they can deploy this; "see the PR" is not an answer.

## What gets an entry

The test is: **would somebody outside the commit want to know?**

| Change                                        | Entry?                                            |
|-----------------------------------------------|---------------------------------------------------|
| A route added, removed, renamed or re-pathed  | Yes — `Added` or `Breaking`                       |
| A request or response field added or removed  | Yes — `Added` or `Breaking`                       |
| A status code or error shape changed          | Yes — clients branch on these                     |
| A default value, a clamp or a page size moved | Yes — it changes responses with no error anywhere |
| A bug a caller could have hit                 | Yes — `Fixed`                                     |
| A new required config value or env var        | Yes — it breaks the deploy, not the build         |
| A migration that needs running out of band    | Yes, with the instruction                         |
| An auth or rate-limit rule that got stricter  | Yes — somebody's integration stops working        |
| Internal refactor, no observable difference   | No — the commit message is the record             |
| Tests, formatting, CI plumbing, doc typos     | No                                                |

When unsure, ask whether the line would help someone whose integration just broke, or whoever is paged when it does. If not, leave it out — a
changelog padded with internal churn stops being read, and then the breaking entries stop being read too.

## When to write it

**In the same commit as the change.** Not a sweep before the release, not a follow-up commit.

The reason is not tidiness. A changelog written weeks later is written from the diff, by someone reconstructing intent — which is exactly how
"refactor the expander" entries get produced. Written alongside the change, the *why* is still in your head, and it is the only part a consumer cannot
recover.

For a breaking change this is mandatory and doubled up: the `!` and the `BREAKING CHANGE:` footer in the commit, and the entry under `Breaking` here —
both in one commit. Load the `ktor-toolkit:commit` skill.

## Releasing

Cutting a version turns `unreleased` into the date and opens a new heading.

Before a release, read the accumulated entries once as a caller would. Three separate `Fixed` lines that are really one bug should be one line. A
`Changed` entry that turns out to break somebody belongs under `Breaking`, and its absence there is the failure this read-through catches.

If the project has a documented release procedure — a `RELEASING.md`, a runbook, a pipeline that cuts the tag — follow it rather than improvising one
here. If it has none and the user is cutting releases by hand each time, that is worth offering to write down once; ask before creating the file.

## Before you commit the entry, check

| Mistake                                        | What it costs                                                  |
|------------------------------------------------|----------------------------------------------------------------|
| A wire-format change with no entry             | Nothing in the build fails; the client finds out in production |
| A breaking change filed under `Changed`        | Upgraders read `Breaking` first and miss it entirely           |
| An entry describing the diff, not the effect   | The reader cannot tell whether it affects them                 |
| A behaviour change with no upgrade instruction | Names the problem, leaves the reader to guess the fix          |
| The entry written at release time              | Reconstructed from the diff, so the *why* is already gone      |
| Editing an already-released section            | Rewrites history someone has read and acted on                 |
| Every internal refactor listed                 | Volume that buries the entries that matter                     |
