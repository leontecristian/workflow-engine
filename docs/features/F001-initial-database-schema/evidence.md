# F001 evidence

Specification: [revision 1](spec.md). Status: **documentation prepared; implementation NOT RUN**.

## Baseline

- Database design: v1.4 at `apolo-docs` commit `c32ffb07cc9f4f2f3d1d7bf087455c1af13acbc3`.
- Project inspected: `workflow-engine` commit `85fc1b1f892bc622be141e1f2b53faf6d27d20b1`, before this documentation change.
- Observed configuration: empty core master, host master includes core master, Windows-integrated local SQL Server connection, Hibernate `ddl-auto=validate`.
- This documentation task has not authored a migration, built the application, started it or accessed the database.

## Documentation preparation — 2026-10-25

PASS: reviewed existing master/host wiring and configuration; copied the source column dictionaries and checked table column counts (including all 26 definition-version fields); checked relative file links in all six feature documents with PowerShell (no missing targets); checked new-document trailing whitespace and ran `git diff --check` (no whitespace errors). Reviewed the documentation-index changes. These checks concern documentation only and do not satisfy implementation or runtime acceptance.

## Implementation record — fill during implementation

Record implementation commit/working-tree state, date, Windows/JDK/Maven/Liquibase/JDBC/SQL Server versions, effective target host/database/login, tracking schema, artifact path and build time. Record exact commands actually executed; redact credentials and private runtime data. Keep detailed logs/catalog exports under ignored `target/` and summarize durable findings here.

| Check | Scenario / tasks | Result | Evidence / reason |
| --- | --- | --- | --- |
| Migration contract and precondition review | AC01/02/05; T01–T03 | NOT RUN | No implementation exists in this feature task. |
| JDK 21 package with tests skipped; resources in JAR | AC01; T04 | NOT RUN | Documentation-only task. |
| Local DB identity, prior tables/tracking baseline | AC01/06; T05 | NOT RUN | Deferred to implementation. |
| First migration and completed app startup | AC01; T05 | NOT RUN | Deferred to implementation. |
| Every column, identity and collation | AC02; T06 | NOT RUN | Compare against schema-contract dictionaries. |
| All PK/UQ/FK/CHECK/index definitions and status | AC02; T06 | NOT RUN | Compare semantics, key order, filters, includes and trust/enabled flags. |
| DATABASECHANGELOG identities/checksums/order | AC03; T07 | NOT RUN | Expect two F001 identities once each. |
| DATABASECHANGELOGLOCK released | AC03/04; T07/T08 | NOT RUN | Inspect after each startup. |
| Same-artifact second startup; metadata unchanged | AC04; T08 | NOT RUN | Capture and compare both runs. |
| Process cleanup and final state | AC07; T08/T09 | NOT RUN | No processes started by documentation work. |

## Required result details

For T06, record per-table expected/actual column counts and discrepancies, plus the named keys/checks/indexes or a concise manifest of their comparison. Call out composite history ownership, optional paired correlation, numeric locks, JSON/typed-null handling, completion uniqueness, multiple tasks per activity and absence of prohibited columns/tables.

For T07/T08, record each changeset's ID, author, filename, EXECTYPE, MD5SUM, DATEEXECUTED and ORDEREXECUTED before/after restart, plus lock state. Record the host PIDs and observed final state. Describe any existing unrelated tracking rows without exposing business data.

## Limits

No automated tests are authorized. Static review of rejection paths and read-only metadata verification do not prove live constraint rejection, runtime rollback/concurrency/replay, authorization, expression safety, task-result retrieval, JSON-variable mutation semantics, retention or workload performance. Production/APOLO integration remains unverified. If the database is unavailable, retain NOT RUN/BLOCKED entries and explain the missing prerequisite; do not label F001 fully verified.
