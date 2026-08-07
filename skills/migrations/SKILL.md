---
name: migrations
description: >-
    Versioned schema migrations with Flyway — where the SQL lives in the -core / -adapters / -app
    split, running them on startup, immutability of applied migrations, and the expand/contract
    sequence that ships a schema change without a deploy window. Use whenever a table, column, index
    or constraint changes, or SchemaUtils.create is about to be called outside a test.
---

# Schema migrations

## When the project has no migrations yet

A service whose schema was created by hand, by `SchemaUtils.create`, or by a DBA running SQL out of band has no migration history — and the first
versioned migration you add has to account for what is already there.

**Do not write `V1__create_books.sql` against a database where those tables already exist.** Flyway will try to run it and fail on the first
`CREATE TABLE`. The correct move is a baseline: capture the current schema as `V1`, then tell Flyway to treat existing databases as already at that
version (`baselineOnMigrate = true`, `baselineVersion = "1"`). New environments run `V1` and get the schema; existing ones record it and skip it.

Introducing Flyway is a change to how the service starts and how it is deployed, so **say what you would add and wait for a yes**: the dependency, the
`db/migration` directory, the baseline file, and the startup call. Ask specifically whether any environment already has the schema — the answer
decides whether you baseline or not, and getting it wrong fails the next deploy rather than the build in front of you.

## The one rule

**An applied migration is immutable.** Once a file has run anywhere you do not control — a colleague's machine counts — it is history, and history is
appended to, never edited.

It is also the rule people break first, because editing yesterday's file is easier than writing a new one and the cost lands on someone else's
checkout rather than yours.

The reason a toolkit service needs a position on this at all: `-core` does not know a database exists, so the schema is not visible from the domain,
and nothing in the type system notices when an Exposed table and the real table drift apart. The migration files are the only place the schema is
stated, which makes them the schema.

## Flyway, versioned SQL

**Use Flyway with plain `.sql` files.** Not Liquibase XML, not `SchemaUtils.createMissingTablesAndColumns`, not a hand-run script.

The reason is reviewability. A migration is the most dangerous kind of change in the repository — it is applied once, to real data, often without a
tested rollback — so the thing a reviewer reads should be exactly the thing the database executes. SQL in a diff is that. A changelog abstraction is
one translation away from it, and the translation is where the surprise lives.

Liquibase earns its place on one specific problem: several databases of different vendors from one changelog. Take it there and nowhere else.

**Never `SchemaUtils.create` outside a test.** It creates what is missing and is silent about what is different — a column whose type changed, a
constraint that was dropped, an index that never existed in production. A service that starts this way has no schema history, so nothing can tell you
what a given deployment is actually running against.

## Where the files live

```
catalog-adapters/src/main/resources/db/migration/
├── V1__create_books.sql
├── V2__add_isbn_unique_index.sql
└── V3__add_books_published_at.sql
```

**The SQL belongs to `-adapters`, and `-app` runs it.** The schema is a persistence concern, so it sits beside `ExposedBookRepository` and `Books` —
the three change together and a reviewer sees them in one diff. `-app` depends on `-adapters` at runtime, so the resources are on its classpath
without anything extra; load the `ktor-toolkit:architecture` skill for why the deployable owns the runtime and the adapter owns the concern.

Nothing about the schema goes in `-core`. A domain that knows its own column names has acquired a database.

`db/migration` is Flyway's default location, so it needs no configuration. Keep it.

## Naming

```
V<version>__<description>.sql        V7__add_books_published_at.sql
R__<description>.sql                 R__book_search_view.sql
```

**Two underscores** between version and description — one is a file Flyway silently ignores, which reads as a migration that did not run.

Version numbers are integers, incremented by one. Timestamps (`V20260806123000__`) are the alternative, and they trade a merge conflict you resolve in
ten seconds for an ordering nobody can read at a glance. Take the conflict: it is a genuine signal that two people changed the schema in the same
window.

Describe the change, not the ticket: `add_isbn_unique_index`, not `jira_1234` or `fix`. The filename is what a reader sees in `git log` and in
`flyway_schema_history` for the rest of the service's life.

**`R__` repeatable migrations** re-run whenever their checksum changes, after all versioned ones. They are right for views, functions and seed data
that is defined rather than accumulated — anything you would rather state once than diff by hand.

## One migration, one change

Same test as a commit (load the `ktor-toolkit:commit` skill): it should be describable in one sentence without an "and".

```sql
-- V3__add_books_published_at.sql
ALTER TABLE books
    ADD COLUMN published_at DATE;
CREATE INDEX idx_books_published_at ON books (published_at);
```

The index belongs with the column that needs it — that is one change. Adding a `books` column and a `reviews` table in one file is two, and when the
second half fails on a database without transactional DDL you are left half-applied with no file to re-run.

Every column arrives with an explicit decision about `NULL` and a default. `ADD COLUMN … NOT NULL` with no default fails on a non-empty table, and
finding that out in staging is the good case.

## Running them

**On startup, in `-app`, before the server binds.** A service that is listening on a schema it has not migrated will serve errors for the seconds it
takes.

```kotlin
// -app/plugin/Database.kt
internal fun Application.migrateDatabase(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
}
```

Flyway takes a lock on the history table, so several instances starting at once queue rather than collide — rolling deploys do not need coordination
for this.

Split it out into its own step — a `make migrate`, an init container, a deploy job — when one of these is true, and say which:

- The migration takes long enough that a health check would fail while it runs.
- More than one service shares the database, so "who migrates" is a question rather than an answer.
- The application's database user deliberately cannot run DDL, which is a good posture and worth keeping.

**Flyway 10 split vendor support into separate artefacts.** `org.flywaydb:flyway-core` alone finds no database and fails at startup with an
unsupported-URL message that reads like a bad JDBC string:

```kotlin
implementation(libs.flyway.core)
implementation(libs.flyway.database.postgresql)   // or flyway-mysql, and so on
```

Both go in the catalog, never inline — load the `ktor-toolkit:gradle` skill.

## When it refuses to start

| Symptom                                                  | Cause                                                                    |
|----------------------------------------------------------|--------------------------------------------------------------------------|
| `Migration checksum mismatch for version 3`              | An applied file was edited. Revert the edit; write `V4` instead.         |
| `Found non-empty schema(s) without schema history table` | An existing database predates Flyway — `baselineOnMigrate = true`, once. |
| `Unsupported Database: PostgreSQL` (or similar)          | The vendor module is missing (above).                                    |
| A migration is skipped and nothing is logged             | One underscore instead of two, or the wrong directory.                   |
| `Detected resolved migration not applied to database: 5` | A branch added `V5` and was not merged; or `outOfOrder` is needed, once. |

The checksum row is the one that matters, because the tempting fix — `flyway repair` — makes the error go away without making the databases agree. Two
environments then differ in a way nothing will report again. Repair is for a migration that failed halfway and left a bad history row, not for a file
somebody changed.

## Changing a schema without a deploy window

During a rolling deploy the old and new versions of the service run at the same time, against one database. A migration that only suits the new code
breaks the old one for the length of the rollout — and breaks the new one if you roll back.

So anything destructive is **expand, then contract**, across two releases:

| Renaming `title` to `name`   | Ships in                                  |
|------------------------------|-------------------------------------------|
| Add `name`, nullable         | Release 1, with the code that writes both |
| Backfill `name` from `title` | Release 1, same or a following migration  |
| Read from `name`             | Release 2                                 |
| Drop `title`                 | Release 3, once nothing reads it          |

Three releases for a rename is the price of never taking the service down for one. Fold them together only when the table is genuinely new or
genuinely empty, and say so in the commit body.

The same shape covers a narrowing type change, a new `NOT NULL`, and a column split. **A single migration that drops or renames anything in use is the
one to push back on**, whoever wrote it.

## Testing

Run the real migrations against a real database — that is the whole point of them existing.

```kotlin
private val postgres = PostgreSQLContainer("postgres:17-alpine")

// Migrate the container the same way production is migrated, so the schema under
// test is the schema that ships.
Flyway.configure().dataSource(postgres.jdbcUrl, postgres.username, postgres.password).load().migrate()
```

A repository test whose schema came from `SchemaUtils.create` proves the query works against a table Flyway never built. That gap is exactly where
production bugs come from, and closing it costs one line. Load the `ktor-toolkit:tests` skill for the container setup.

Worth a test of its own when the migration carries data: a backfill is code, it runs once, and it is the only code in the repository with no second
chance.

## Common mistakes

| Mistake                                         | Why it hurts                                                                  |
|-------------------------------------------------|-------------------------------------------------------------------------------|
| Editing a migration that already ran            | Checksum mismatch for everyone else; environments silently diverge            |
| `flyway repair` to clear a mismatch             | Hides the divergence instead of resolving it                                  |
| `SchemaUtils.create` outside a test             | No history, and silent drift when a type or constraint changes                |
| Tests migrating differently from production     | The schema under test is not the schema that ships                            |
| A rename or drop in one release                 | The old instances break for the length of the rollout, and so does rollback   |
| `ADD COLUMN … NOT NULL` with no default         | Fails on any table with rows in it                                            |
| Several unrelated changes in one file           | Half-applies on a database without transactional DDL, and cannot be re-run    |
| One underscore in the filename                  | Flyway ignores the file; it reads as a migration that did nothing             |
| `flyway-core` without the vendor module         | Fails at startup with what looks like a JDBC URL problem                      |
| Migration SQL in `-core` or `-app`              | The schema leaves the module that owns persistence                            |
| A ticket number as the description              | `git log` and the history table say nothing about what changed                |
| Timestamps as versions to dodge merge conflicts | Loses the conflict that was telling you two people changed the schema at once |
