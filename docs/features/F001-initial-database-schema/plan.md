# F001 implementation plan

Applies to [specification revision 1](spec.md). Implementation and local verification are complete; actual results are in [evidence.md](evidence.md).

## Changes

1. Read repository instructions, the pinned source and [schema contract](schema-contract.md). Inspect current POMs, core master, host master and datasource configuration. Confirm F001 is not already implemented or applied.
2. Add the single initial XML changelog at `workflow-core/src/main/resources/db/changelog/workflow/changes/001-initial-schema.xml`. Assert SQL Server compatibility. Put no-target-table preconditions on each of the two changesets, evaluated only when that changeset is pending: the first prevents collisions before schema DDL and the second protects table creation after a partial earlier run. Do not put an unconditional table-absence precondition at file scope; it would reject an ordinary second startup after success. The first changeset conditionally creates `wf` if absent; it must not replace an existing schema or change its ownership. The second creates all tables, constraints and indexes in dependency order.
3. Use standard Liquibase XML where it faithfully represents SQL Server semantics. Use explicitly SQL-Server-scoped SQL for CHECK constraints, filtered/include indexes or conditional schema creation as necessary. Keep SQL batches compatible with JDBC; `GO` is a client separator and must not be passed to SQL Server as ordinary SQL. Keep transactional changesets enabled. Use stable, descriptive constraint/index names.
4. Create definitions and task catalog first, then definition versions, instances, activities, tasks, variables, history and receipts. Referenced composite unique keys must exist before history ownership FKs. All FKs use NO ACTION for update/delete. Defaults are not needed; application/provider initialization remains explicit. Do not add triggers, sequences or computed columns.
5. Add one relative include from the core master to `changes/001-initial-schema.xml`. Preserve the host include of the stable core master and its single runner/datasource. Do not add Java classes merely to make Hibernate appear to validate the new tables.
6. Review the exact generated migration against every schema-contract entry. Build with tests skipped and verify the resource is packaged. Follow [verification.md](verification.md) for first startup, schema inspection, changelog inspection and second startup.
7. Record findings, actual object names and tracking identities in [evidence.md](evidence.md), update task completion and reconcile documentation. Report evidence gaps without broadening the feature into execution logic or automated tests.

No rollback execution or destructive rollback/reset helper is required. Future applied-schema corrections use forward changesets; this feature does not authorize dropping retained workflow records.

Implementation choice: the initial tables use explicitly SQL Server-scoped native DDL in the XML changeset. This keeps BIN2 column collations, named clustered/nonclustered keys, null-safe CHECKs and filtered/covering indexes together in one reviewable transactional definition. Liquibase XML retains changeset identity, pending preconditions, transaction defaults and host-owned execution. The migration was applied successfully without changing the selected contract.

## Constitution check

These are design checks, not claims that implementation/runtime checks passed.

| Constraints / assumptions | Design assessment |
| --- | --- |
| CON-01/02; ASM-01/09 | PASS: two existing modules, ordinary core resource JAR, existing dependency versions and Java 17 target/JDK 21 build; no reparenting. Production host alignment remains deferred. |
| CON-03/04/10 | PASS: `wf` is core-owned; the host executes migrations with its datasource. No host business tables, authorization rules or approval-baseline semantics. |
| CON-05 | PASS: bounded first persistence feature, without optional projections or later orchestration features. |
| CON-06/07; ASM-02/03/04 | PASS for schema scope: storage supports pinned instances, history and receipts without FK cycles. Runtime atomicity, claims, replay and durable handoff are outside this implementation and remain unverified. |
| CON-08; ASM-05 | PASS for schema scope: simple retained catalog and pinned definition version; no one-draft/one-published limit or task-type version table. Immutability/host capabilities require later services. |
| CON-09/14; ASM-11 | PASS for schema scope: scalar types and object/array JSON storage, typed nulls, syntax checks. Codec bounds, read-only navigation and expression restrictions remain future verification gates. |
| CON-13; ASM-10 | PASS for schema scope: one authoritative TASK_COMPLETED event per task at most, required task reference and exact-activity retrieval index. Existence, content agreement and authorized retrieval need runtime implementation. |
| CON-11 | NOT APPLICABLE: no designer or preview implementation. |
| CON-12; ASM-08 | PASS: no automated tests. Owner-authorized local build/startup and read-only metadata inspection; accurate NOT RUN/BLOCKED evidence and process cleanup. |

Multiple tasks per activity remain possible. The selected SINGLE/ALL storage constraints are a physical representation of the source proposal, not implementation or verification of either completion algorithm. No command service, public result envelope or booking aggregation policy is introduced.
