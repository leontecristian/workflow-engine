# F002 implementation plan

Applies to [specification revision 3](spec.md). Revisions 1 and 2 implementation/evidence are retained. Revision 3 concrete-service simplification and fresh verification are complete, with separate results in [implementation.md](implementation.md#4-verification).

## Implementation sequence

1. Read AGENTS, constitution/constraints/assumptions, coding standards and F001/F002. Confirm the F001 catalog schema and changelog checksums are unchanged. Inspect current scanning/POMs rather than adding duplicate infrastructure.
2. Follow [exact F002 assignments](../../package-structure.md#exact-f002-assignments): consolidate TaskTypeCatalog/JpaTaskTypeCatalog into concrete TaskTypeCatalogService in `core.service.tasktype`, with the two package-private helpers beside it. Remove the interface and obsolete `impl` package. Keep DTO/exception/entity/repository assignments unchanged. Update declarations, imports, injection sites, Javadoc and README usage together. Preserve all method/transaction behavior, root scanning and Spring proxy compatibility; avoid final service classes/methods that prevent class-based transaction interception. Do not instantiate the service directly at call sites or introduce self-invocation that bypasses transactional entry points.
3. Map only `wf.workflow_task_type`: six columns, generated BIGINT IDENTITY, explicit VARCHAR code, Unicode name/description mapping, BIT selectable, BIGINT/Long @Version. Match F001 lengths/nullability/schema. Hibernate must validate NVARCHAR correctly; follow existing provider capabilities without changing host global settings. No F001 rewrite, new migration, default, trigger, entity relationships or task/definition entities are needed.
4. Limit repository operations to lookup, bounded query, persistence and managed updates. Do not expose an inherited delete/save API as the supported catalog API. Keep the entity free of unrestricted ID/code/version setters, blanket Lombok @Data, mutable-state equality and repository leakage.
5. Implement the exact validation, read/query and mutation contracts. Use database-level bounded filtering/order/cursor predicates, immutable copied collections and limit+1 lookahead. Update a loaded managed entity; capture the before snapshot before changes and after snapshot after flush. Compare full mutable values for no-op detection.
6. Enforce host-owned transactions for mutation, including proxied entry points; reads join an existing transaction or run read-only. Flush inside the mutation boundary to surface ordinary uniqueness/optimistic conflicts and obtain generated/provider values. Translate recognized failures without querying or retrying inside an aborted transaction. Inspect effective provider exception wrapping. Do not catch arbitrary errors and turn them into DUPLICATE_CODE.
7. Document the host integration pattern in public API documentation and a concise repository usage example: authorize trusted actor, host transaction, core call, durable before/after management audit, host commit, external response. The example may be conceptual and must not invent an existing audit API. No development security bypass, new endpoint, audit table or log-only audit sink.
8. Follow [verification procedure](plan.md#verification-procedure), reconcile tasks/evidence and report the actual scope. Build/startup validates wiring/mapping only; leave unexecuted mutation/race/audit scenarios visible.

## Transaction integration shape

```text
Trusted host administration boundary
  authenticate + authorize
  begin host transaction
    core catalog.create/update (requires transaction)
      validate -> persist/versioned update -> flush -> immutable change
    persist host audit when changed=true
  commit host transaction
  return success

Any failure -> rollback; no independently committed core write
```

This does not supply production host authorization/audit. Failed transaction translation/commit handling belongs to the outer host coordinator. A returned change is not evidence of commit. Catalog management does not borrow workflow instance receipts/history.

## Constitution check

| Reference | Assessment for this plan |
| --- | --- |
| CON-01/02; ASM-01/09 | PASS: same two modules, Java 17 target/JDK 21 runtime and current dependency baseline. No reparenting or production compatibility claim. |
| CON-03/10 | PASS: trusted Java boundary, neutral DTOs, host identity/authorization/audit, no core endpoint or security-provider dependency. Actual host integration remains deferred. |
| CON-04 | PASS: consume F001 table; preserve all applied migrations, one host runner/datasource and JPA validate. |
| CON-05/08; ASM-05 | PASS: bounded catalog slice, permanent codes, editable metadata and authoring-only selectability. No business versions, outcomes or runtime capability claims. |
| CON-06/07; ASM-02/03 | PASS for catalog scope: host transaction and audit handoff, version-checked writes, no auto retry or runtime-receipt misuse. Host audit atomicity and provider race behavior need separate execution evidence. |
| CON-09/11/13/14 | NOT APPLICABLE to implementation: no definition/expression/designer/submission/variable behavior; their existing boundaries remain intact. |
| CON-12; ASM-08 | PASS: authorized build/startup/read-only inspection recorded in evidence, no automated tests or catalog fixtures; behavioral execution remains NOT RUN. |

## Completion boundary

The specified catalog API and host preconditions define code completion. Production authorization, audit and compatibility remain host integration gates. Actual implementation decisions, revision history and verification results are recorded in [implementation.md](implementation.md).

## Verification procedure

The procedure below is retained from the former verification document. It describes checks, not new execution evidence or authorization beyond the feature/task scope. Actual results are in [implementation.md](implementation.md#4-verification).

This runbook defines F002 implementation verification. Actual executed checks and remaining behavioral gaps are recorded in [implementation.md](implementation.md#4-verification). Follow [AGENTS.md](../../../AGENTS.md), [README](../../../README.md) and the [no-tests policy](../../specification-driven-development.md#verification-and-current-no-tests-policy).

### Static review

For revision 3, verify the single concrete TaskTypeCatalogService, package-private helpers, removal of the old interface/impl pair, and updated injection/examples. Inspect Spring class-based proxy compatibility and preservation of transaction annotations on the concrete entry points. Startup confirms wiring only; it does not prove transactional rollback or race behavior. Record fresh revision 3 evidence separately from earlier checks.

Compare every public operation with the spec, including input bounds/case preservation, nullable description replacement, disabled-inclusive reads, SQL-bounded cursor queries, immutable snapshots and categorized errors. Inspect required transaction propagation at the proxied public mutation boundary, real @Version mapping, explicit flush and absence of manual version increments or blanket save/merge paths.

Review the unique-insert race path and versioned-update race path independently of advisory checks. Check provider exception translation at the actual flush boundary. A normal successful update path is insufficient evidence that races are handled. Confirm no public rename/delete/upsert, no startup seed runner, no fake history/audit record and no new endpoint.

Inspect the host integration example for trusted authorization, audit in the same transaction and success only after commit. Actual durable audit adapters are outside this feature. Do not report that integration as verified from a pseudocode example.

### Build and local startup

Implementation scope authorizes packaging with tests skipped, one temporary startup of the existing local test host, and read-only SQL/catalog inspection. It does not authorize automated tests or mutating catalog fixtures. Catalog rows are permanently retained; verification must not create throwaway codes in the shared development database.

1. Confirm the effective target is `localhost:1433/apolo_workflow_dev`, Windows authentication, as in F001. Check overrides without exposing credentials. An unavailable database blocks runtime checks, not independent implementation.
2. Capture the existing `wf.workflow_task_type` column/constraint metadata, row count and actual `DATABASECHANGELOG` identities/checksums/lock state using the read-only queries in the [F001 runbook](../F001-initial-database-schema/plan.md#verification-procedure). Check a pre-existing F001 installation. Do not alter tracking records; if the baseline schema has not been applied, report the prerequisite rather than silently reset/apply a different database.
3. Select JDK 21 and record Java/Maven versions. From the repository root run `./mvnw.cmd '-Dmaven.test.skip=true' package`. Inspect actual artifact paths from current POMs. Do not run `test`, `verify` or unqualified `package`.
4. Start the packaged host using the existing README native-authentication command. Use hidden Start-Process with captured PID/command identity and separate stdout/stderr under ignored `workflow-test-host/target/f002-verification/`. Confirm completed startup, real catalog entity/repository discovery and Hibernate schema validation; do not disable validation to fix a mapping error.
5. Inspect the existing catalog and Liquibase metadata again. F002 requires no new changesets; verify F001 identities/checksums are unchanged, the lock is released and startup added no catalog rows. Do not assume an unchanged row count establishes functional CRUD behavior.
6. Stop only the process started for this verification, after checking its identity, including on failure. Record final process state and any blockers.

No new script, permanent verification endpoint or startup runner is needed just to perform these checks. Generated logs/build files stay out of Git. Run `git diff --check` and inspect new files too; no commit unless the owner requests it.

### Behavioral evidence still required

Record AC01–AC09 as NOT RUN unless actually observed under separately authorized behavioral verification. Static inspection can support their implementation review, but cannot establish persistence results, commit/rollback, concurrency races or host security/audit behavior.

Before production administration is exposed, arrange concrete host authorization/audit wiring and appropriate execution evidence for create/update/read/filter behavior, duplicate-code races, stale updates, no-op semantics and rollback when audit fails. Use a deliberate isolated environment for fixture-based verification; do not silently expand this task into automated tests or write disposable records to the shared retained catalog. The chosen future verification method must respect the owner's then-current policy.

F002 checks the primitive needed by later publication/execution: nonselectable types remain addressable. Actual execution of a published workflow using such a type is deferred until the execution feature exists. Do not fabricate a passing workflow-execution result here.
