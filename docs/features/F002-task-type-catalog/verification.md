# F002 verification scope

This runbook defines F002 implementation verification. Actual executed checks and remaining behavioral gaps are recorded in [evidence.md](evidence.md). Follow [AGENTS.md](../../../AGENTS.md), [README](../../../README.md) and the [no-tests policy](../../specification-driven-development.md#verification-and-current-no-tests-policy).

## Static review

For revision 3, verify the single concrete TaskTypeCatalogService, package-private helpers, removal of the old interface/impl pair, and updated injection/examples. Inspect Spring class-based proxy compatibility and preservation of transaction annotations on the concrete entry points. Startup confirms wiring only; it does not prove transactional rollback or race behavior. Record fresh revision 3 evidence separately from earlier checks.

Compare every public operation with the spec, including input bounds/case preservation, nullable description replacement, disabled-inclusive reads, SQL-bounded cursor queries, immutable snapshots and categorized errors. Inspect required transaction propagation at the proxied public mutation boundary, real @Version mapping, explicit flush and absence of manual version increments or blanket save/merge paths.

Review the unique-insert race path and versioned-update race path independently of advisory checks. Check provider exception translation at the actual flush boundary. A normal successful update path is insufficient evidence that races are handled. Confirm no public rename/delete/upsert, no startup seed runner, no fake history/audit record and no new endpoint.

Inspect the host integration example for trusted authorization, audit in the same transaction and success only after commit. Actual durable audit adapters are outside this feature. Do not report that integration as verified from a pseudocode example.

## Build and local startup

Implementation scope authorizes packaging with tests skipped, one temporary startup of the existing local test host, and read-only SQL/catalog inspection. It does not authorize automated tests or mutating catalog fixtures. Catalog rows are permanently retained; verification must not create throwaway codes in the shared development database.

1. Confirm the effective target is `localhost:1433/apolo_workflow_dev`, Windows authentication, as in F001. Check overrides without exposing credentials. An unavailable database blocks runtime checks, not independent implementation.
2. Capture the existing `wf.workflow_task_type` column/constraint metadata, row count and actual `DATABASECHANGELOG` identities/checksums/lock state using the read-only queries in the [F001 runbook](../F001-initial-database-schema/verification.md). Check a pre-existing F001 installation. Do not alter tracking records; if the baseline schema has not been applied, report the prerequisite rather than silently reset/apply a different database.
3. Select JDK 21 and record Java/Maven versions. From the repository root run `./mvnw.cmd '-Dmaven.test.skip=true' package`. Inspect actual artifact paths from current POMs. Do not run `test`, `verify` or unqualified `package`.
4. Start the packaged host using the existing README native-authentication command. Use hidden Start-Process with captured PID/command identity and separate stdout/stderr under ignored `workflow-test-host/target/f002-verification/`. Confirm completed startup, real catalog entity/repository discovery and Hibernate schema validation; do not disable validation to fix a mapping error.
5. Inspect the existing catalog and Liquibase metadata again. F002 requires no new changesets; verify F001 identities/checksums are unchanged, the lock is released and startup added no catalog rows. Do not assume an unchanged row count establishes functional CRUD behavior.
6. Stop only the process started for this verification, after checking its identity, including on failure. Record final process state and any blockers.

No new script, permanent verification endpoint or startup runner is needed just to perform these checks. Generated logs/build files stay out of Git. Run `git diff --check` and inspect new files too; no commit unless the owner requests it.

## Behavioral evidence still required

Record AC01–AC09 as NOT RUN unless actually observed under separately authorized behavioral verification. Static inspection can support their implementation review, but cannot establish persistence results, commit/rollback, concurrency races or host security/audit behavior.

Before production administration is exposed, arrange concrete host authorization/audit wiring and appropriate execution evidence for create/update/read/filter behavior, duplicate-code races, stale updates, no-op semantics and rollback when audit fails. Use a deliberate isolated environment for fixture-based verification; do not silently expand this task into automated tests or write disposable records to the shared retained catalog. The chosen future verification method must respect the owner's then-current policy.

F002 checks the primitive needed by later publication/execution: nonselectable types remain addressable. Actual execution of a published workflow using such a type is deferred until the execution feature exists. Do not fabricate a passing workflow-execution result here.
