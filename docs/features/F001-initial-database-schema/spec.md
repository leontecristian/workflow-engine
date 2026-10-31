# F001 — Initial database schema with Liquibase

| Attribute | Value |
| --- | --- |
| Status | IMPLEMENTED; local schema, Liquibase bookkeeping and same-artifact restart verified on 2026-10-25 |
| Revision | 1 — 2026-10-25 |
| Owner | Workflow project owner |
| Constitution | [v2.2.0](../../constitution.md) |
| Implementation repository | `workflow-engine` |

## Outcome and authority

An implementing agent creates the initial Liquibase changelog for the nine workflow tables. Starting the existing development host applies it to the local SQL Server database. The agent then inspects the actual schema and Liquibase tracking records and restarts the same application to confirm that the migration is not reapplied.

This feature was initially specified as **documentation only**; the owner subsequently authorized implementation and local verification. Implementation scope includes packaging with tests skipped, starting the host against `localhost:1433/apolo_workflow_dev` using Windows authentication, and performing the read-only database checks in [verification.md](verification.md). Actual results are recorded in [evidence.md](evidence.md). Automated tests remain prohibited by CON-12. No additional approval is needed for those task-authorized checks; normal environment permissions still apply.

The source is [Workflow Engine Database Design v1.4](../../architecture/Workflow_Engine_Database_Design_v1.md), as present at `apolo-docs` commit `c32ffb07cc9f4f2f3d1d7bf087455c1af13acbc3`. Read sections 5–15 and 19 with [design-source precedence](../../design-sources.md). The [schema contract](schema-contract.md) selects the physical baseline for this feature and snapshots the column dictionaries, including the complete definition-version envelope. Later source changes require explicit reconciliation before implementation; do not silently change this baseline.

This feature selects the source's proposed physical types, row constraints, indexes and persisted-document byte limits for the initial schema. It does not finalize command APIs, collective-approval algorithms, codecs or expression policies. Runtime invariants that require application behavior are identified separately in the schema contract.

## Scope

Create schema `wf` and these tables: `workflow_definition`, `workflow_definition_version`, `workflow_task_type`, `workflow_instance`, `workflow_activity`, `workflow_task`, `workflow_variable`, `workflow_history`, and `workflow_command_receipt`. Include all specified columns, identities, primary/unique/foreign keys, row CHECK constraints and indexes.

Core owns the migration resources. The existing host owns execution through its existing datasource and Liquibase configuration. Liquibase's own tracking tables remain in the host-configured tracking schema; they are not part of the nine core tables.

Excluded: entities/repositories/services/endpoints, workflow execution and command handling, seed records and sample workflows, APOLO tables or booking links, approval baselines/guards, outbox infrastructure, dependency projections, task-type versions/outcome tables, separate task-result storage, automated tests, destructive database reset, production deployment and rollback execution. No new module, datasource or migration runner is needed.

## Requirements

| ID | Requirement |
| --- | --- |
| F001-R01 | Add `workflow-core/src/main/resources/db/changelog/workflow/changes/001-initial-schema.xml` and include it once from the stable core `workflow-master.xml`. Preserve the existing host-to-core include. The resource must be packaged in the core JAR. |
| F001-R02 | Use exactly two ordered changesets in that initial file: `f001-001-create-wf-schema` and `f001-002-create-initial-tables`, both with author `workflow-engine`. Set the file's stable logical path to `db/changelog/workflow/changes/001-initial-schema.xml`; keep identity/path stable across later host integration. Do not use `runAlways` or `runOnChange`. |
| F001-R03 | Create the nine tables and their complete column definitions according to [schema-contract.md](schema-contract.md). Use explicit `wf` object qualification, numeric JPA lock storage, and the specified case-sensitive key/reference collations. |
| F001-R04 | Implement all database-enforceable lifecycle, typed-value, JSON, size, ownership and range checks in the schema contract. Use explicit null predicates so SQL CHECK's UNKNOWN result cannot bypass required presence rules. |
| F001-R05 | Create the specified keys and indexes, including the one-active-activity filter, multi-task slot uniqueness, nonunique business correlation, history ownership FKs and authoritative completion-event indexes. Avoid duplicate indexes supplied by keys. |
| F001-R06 | Halt on a non-SQL-Server database or a collision with any of the nine existing target tables before applying pending F001 DDL. An existing `wf` schema alone is allowed and preserved. Collision checks apply to pending changesets, so a normal restart after successful application is allowed. Use failing preconditions for collisions; never treat existing tables as proof of equivalent schema or use `MARK_RAN`/`CONTINUE` to conceal missing work. |
| F001-R07 | Keep host-owned migration execution and `spring.jpa.hibernate.ddl-auto=validate`. Add no schema-generation fallback, database-created lock token, cascade deletion, fixture data or competing infrastructure. |
| F001-R08 | Package with JDK 21 and `./mvnw.cmd -Dmaven.test.skip=true package`, then start the packaged test host against the confirmed local development database. Record successful migration and application startup, or the actual failure/blocker. |
| F001-R09 | Inspect SQL Server catalogs and compare the deployed columns, nullability, identity settings, types/lengths/precision/scale, collations, keys, FKs, CHECKs and indexes with the contract. Counting nine tables or seeing successful startup alone is insufficient. |
| F001-R10 | Inspect the actual host `DATABASECHANGELOG` and `DATABASECHANGELOGLOCK`. Each F001 identity must appear exactly once with `EXECTYPE = EXECUTED`, a non-null checksum, execution order/time and the intended filename. Existing unrelated tracking entries must be preserved. The migration lock must be released. |
| F001-R11 | Stop the first host process, start the same artifact again and confirm no F001 changeset is reapplied: the two tracking records, checksums, execution dates/order and schema remain unchanged; the lock is released. Stop only processes started for this verification and report their final state. |
| F001-R12 | Complete the tasks and evidence records with exact commands, environment, observations and gaps. No unexecuted check may be reported as passed. Keep logs/generated files out of Git and do not claim runtime workflow guarantees from schema verification. |

## Failure, compatibility and ownership

There is no new public API in F001. A migration error prevents successful host startup and must be reported with its changeset identity and cause. Do not disable Liquibase/JPA validation or edit/delete tracking rows to get a green startup. Do not use `clearCheckSums`, changelog synchronization or destructive resets as a substitute for diagnosing a failure.

The migration must fail before creating application objects if target tables already exist without these applied identities. Do not drop or replace those objects. If a prior interrupted run or existing implementation is discovered, inspect its tracked and physical state and report the mismatch before dependent work. Applied migrations are immutable; subsequent changes require new changesets.

F001 uses Liquibase's normal transactional changeset execution on SQL Server. The schema and tables are separate changesets: if the second fails, the schema/first receipt may remain, and the agent must inspect the actual state. This does not establish the later runtime command transaction, concurrency, idempotency or event-delivery guarantees (CON-06/07, ASM-02/03/04).

APOLO owns booking associations and approval material. The optional correlation pair is neither a membership list nor an authorization rule. Generic JSON storage and authoritative completion history support later features without implementing their business semantics (CON-03/10/13/14).

## Acceptance scenarios

| ID | Given / When / Then | Requirements |
| --- | --- | --- |
| F001-AC01 | Given a reachable local SQL Server database with none of the nine target tables, when the built host starts, then all nine tables and the specified schema objects exist and startup completes. | R01–R08 |
| F001-AC02 | Given the applied schema, when its catalog metadata is compared with the schema contract, then every expected column/key/check/index matches and no prohibited storage or uniqueness rule was introduced. | R03–R05, R09 |
| F001-AC03 | Given the initial successful application, when Liquibase metadata is read, then the two expected identities are recorded once, checksummed and ordered, and the lock is released. | R02, R10 |
| F001-AC04 | Given the first successful startup, when the same artifact starts again, then startup succeeds, no F001 changeset is applied again, and schema and tracking evidence remain unchanged. | R11 |
| F001-AC05 | Given a conflicting target table or unsupported database, when the migration is considered, then preconditions halt rather than skip/mark executed/overwrite it. Review this path statically; do not create a collision in the shared development database for this feature. | R06 |
| F001-AC06 | Given an unavailable database, insufficient permission or migration error, when verification cannot complete, then evidence records the blocker/failure and remaining checks accurately, with no reset, invented pass or unauthorized environment change. | R08–R12 |
| F001-AC07 | Given finished temporary verification, then only this feature's host processes are stopped, generated evidence is excluded from Git, and final status identifies outstanding checks. | R11–R12 |

AC02 uses read-only metadata inspection, not fixture inserts or automated constraint tests. It verifies deployed definitions; enforcement under adversarial writes, runtime transactions and workload performance are not proven by this feature.

## Assumptions and deferred decisions

- **ASM-08 / local environment:** the owner starts SQL Server; the existing development database, JDK 21 and Windows authentication are available. The implementing agent checks this before startup. Unavailability blocks runtime evidence, not independent migration authoring.
- **ASM-01/09 / host compatibility:** current POMs and the standalone host are the implementation baseline. Production dependency alignment and the later source move remain unverified; keep classpath identities stable.
- **ASM-10/11:** relational storage for submissions and JSON variables is included. API shape, exact normalization, aggregate/depth limits, mutation safety, retrieval and expression behavior remain later specifications. In particular, F001 adds no arbitrary per-variable JSON byte limit; that limit must be settled before variable write APIs are implemented.
- No open business decision blocks this schema-only slice. If the actual SQL Server version cannot support the specified features, record that incompatibility and resolve it before applying dependent DDL; do not weaken the contract silently.

Implementation order and constitutional checks: [plan.md](plan.md). Work list: [tasks.md](tasks.md). Verification procedure: [verification.md](verification.md). Results: [evidence.md](evidence.md).
