# F028 implementation and handover

**Current delivery — 2026-11-16:** revision 3 IMPLEMENTED. Focused core/web/host unit checks, three guarded SQL scenarios, isolated packaging and independent review pass. Production APOLO integration remains deferred. Historical preparation observations below are retained.

**2026-11-16:** DRAFT revision 3, documentation preparation only. Runtime code is not implemented and no implementation authorization is claimed. See [specification](spec.md), [plan](plan.md) and [tasks](tasks.md).

## Actual work

Inspected current completion/replay and host transaction paths, F018 validation modes, exact completion command/context types and booking baseline requirements at `50e356395fae2822ea38493ee255c6e8ef9da7c3`. Prepared four process documents and feature-index navigation. F027 staged files and pre-existing roadmap changes were preserved; no application, migration or test file was changed.

The design distinguishes current business access for recovery, fresh approval permission, and business-record validation before acceptance. It retains successful receipt recovery before fresh checks and does not use the optional result validator as a universal permission hook. APOLO-specific rules, business locking and finalization remain integration responsibilities.

## Checks and limitations

Revision 3 makes provider-neutral authorization and the absence of a per-object ACL requirement explicit. Added a non-normative Keycloak resource/scope example using host-owned Resource.BOOKING / Scope.VIEW / Scope.DECIDE names and applicable contextual roles, without prescribing provider API types or APOLO permission rules. The owner clarified that the corresponding test-host fixture is planned only: local permissions/contextual-role data, no Keycloak server or SDK. Planned checks distinguish loss of DECIDE from loss of VIEW during replay. Removed domain-specific approval-policy prescriptions from the feature's business-validation description. F027 implementation work in the shared workspace was left untouched. Documentation link/whitespace/content checks were repeated; no runtime checks or code changes were made by this update.

Revision 2 records the owner's D01 decision: both adapters are required for completion hosts, without opt-in or implicit allow/no-op fallback. Required registration is distinct from invocation: authorization protects completion and recovery; the general validator is resolved/invoked only for fresh acceptance, never successful receipt replay or lookup-only misses. Specification, plan, tasks and feature index were aligned. Focused lead content/link/whitespace checks were repeated; the independent review below covered revision 1.

- Source inspection: completed for planning; not runtime PASS evidence.
- Focused independent evidence/draft review: completed; no actionable issues found in replay ordering, transaction boundaries, result-mode coverage or separation from F027.
- Documentation structure/link/whitespace checks: PASS (four process documents; relative file links resolve; no trailing whitespace); `git diff --check` passed. Final index diff inspected; F027 has no unstaged changes from this task and its existing staged content was preserved.
- Unit/build/integration/browser checks: NOT RUN (planning-only request).
- Processes started: none. Database access: none.
- D01 is CLOSED / AGREED by the owner. Public signatures/error names remain PROPOSED; feature remains DRAFT pending their finalization, not because adapter requirements are undecided.
- Changes are uncommitted; no files staged by this task.

At the end of the planning phase, next work was finalizing engineering contracts and obtaining implementation authorization. The owner subsequently authorized revision 3 below. Existing receipt/transaction evidence is referenced for reuse only where applicable; no APOLO production guarantee follows from this document.

## Authorized implementation — 2026-11-16

The owner approved revision 3 implementation and focused verification. Existing edits to the four feature documents and feature index were retained; the current F027 implementation was used rather than its older planning baseline. Contracts are finalized in spec/plan; explicit business rejection uses the no-argument TaskCompletionRejectedException. Implementation is present; the historical NOT RUN observations above describe preparation only.

Core now requires exactly one authorization adapter at completion/replay entry and checks exact-target business access before receipt lookup. Fresh completion rechecks access on refreshed state and runs structural checks, fresh permission, the required general validator, then optional mode-specific F018 validation before acceptance. Adapter counting does not treat a primary bean as permission to ignore duplicates. SQL locking/version contention from validation retains established conflict translation; other unexpected validation failures are sanitized separately. No receipt format, fingerprint or migration changed.
### Host composition and business protection

The sandbox registers explicit authorization and validation implementations. Resource.BOOKING / Scope.VIEW protects current access and Scope.DECIDE protects fresh decisions. The bounded primary context grants configured reviewers DECIDE and administrator VIEW; the F028-SECONDARY: request-name fixture selects a second context where reviewer has VIEW only and reviewer2 retains DECIDE. Permission roles are separate from workflow GROUP/ROLE membership. These names and markers are local fixture policy, not production booking or Keycloak integration.

The validator locks the linked sandbox request with UPDLOCK,HOLDLOCK in the existing transaction, after core's aggregate write. Business edits in the test touch that same row only. The lock survives validation, acceptance, effects and commit; this is the selected fixture lock order. Existing request_data_json optionally contains completionFixture with approvalBasis and currentBasis JSON values; a missing fixture explicitly accepts, while malformed shape or a differing current basis rejects. The preserved basis is never refreshed by validation. No migration, management endpoint, booking subsystem or production business rule was added. APOLO must select its own protected fields, associations, lock/write guards and finalization policy.

Completion and recovery no longer call the F027 completionTask read precheck. They delegate trusted command context to core through existing transaction proxies. F027 ordinary read policy, My Tasks and advisory completion availability remain unchanged. Shared-web mapping uses the finalized sanitized codes; payloads and successful results are unchanged.

### Fresh verification

All commands use the root wrapper with JAVA_HOME selecting installed Corretto JDK 21.0.13, compiling Java 17 APIs/bytecode.

- PASS — Core/web: `./mvnw.cmd -pl workflow-core,workflow-web -am '-Dtest=RuntimeCommandsUnitTest,RuntimeReceiptUnitTest,SharedAssignmentUnitTest,WorkflowRuntimeWebUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`: 35 core and 17 web checks, no failures/errors. This covers structural/authorization/general/F018 ordering, result modes, missing/duplicate adapters, access before receipts, replay without fresh validator, sanitized failures and locking/version-conflict projection.
- PASS — Focused follow-up: `./mvnw.cmd -pl workflow-core '-Dtest=RuntimeCommandsUnitTest' test`: 22 checks after adding lookup-only miss with missing fresh validator. The repeat was limited to this concrete coverage gap.
- PASS — Host units in selected reactor verify: SandboxCompletionUnitTest, ConsoleRuntimeUnitTest, ConsoleRuntimeAdaptersUnitTest, ConsoleRuntimePolicyUnitTest, ConsoleSharedAssignmentUnitTest: 24 checks, no failures/errors. Covers local contextual grants, identity/exact association, baseline rejection and dispatch/recovery without read prechecks.
- PASS — Build/package phases of `./mvnw.cmd -B -ntp -Pintegration-tests '-Dtest=SandboxCompletionUnitTest,ConsoleRuntimeUnitTest,ConsoleRuntimeAdaptersUnitTest,ConsoleRuntimePolicyUnitTest,ConsoleSharedAssignmentUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=CompletionAuthorizationIT' verify`: all three modules compiled/packaged. No duplicate packaging command was run.
- BLOCKED (initial attempt; resolved below) — The same verify command's two guarded SQL tests failed during context creation because localhost:1433 refused the connection. No migration or test fixture was reached. The existing MSSQL$SQLEXPRESS service was confirmed stopped; starting it was denied by Windows service permissions even outside the sandbox. The user was asked to start the service. This is an environment block, not a SQL acceptance PASS. The authored tests cover rejected-completion rollback including provisional version/history/receipt/outbox/output/effects, preserved prior ALL contributions, fresh local permission denial, receipt recovery after DECIDE/VIEW loss, successful resolving contributions and a protected-row edit race.

### Reused evidence and remaining gates

REUSED — F017 revision 3 for unchanged assignment/ownership semantics; F018 revision 3 for unchanged embedded result schema and exact encoding; F021 revision 2 for unchanged roster/output mechanics. Their source handovers are linked in plan P05. Fresh unit checks above cover the changed callbacks; historical results do not establish F028 SQL behavior.

Browser: NOT RUN; no frontend rendering or interaction implementation changed. Existing generic error presentation remains applicable; focused web/host tests check changed projection and dispatch. APOLO production authorization/provider/business-lock integration remains deferred. The initial SQL environment block was resolved by the user starting the existing service; the completed SQL run is recorded below.

### Completed SQL verification and isolated source boundary

PASS — After the user started SQL Server Express, the three CompletionAuthorizationIT checks passed (0 failures/errors/skips) in `target/f028-verification/source`, using `./mvnw.cmd -B -ntp -Pintegration-tests '-Dtest=F028NoUnitTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=CompletionAuthorizationIT' verify`. All modules compiled/packaged successfully; already-passing unit suites were deliberately not repeated. Liquibase found 16 previously applied changesets and executed none. The suite uses the guarded localhost:1433/apolo_workflow_test datasource, real host adapters, committed transactions, rollback observations outside failed transactions and fixture-owned cleanup. It covers fresh VIEW/no DECIDE denial with version preservation, rejection preserving prior ALL contributions and all attempted persistence, original receipt recovery despite DECIDE loss/material change, VIEW-loss denial, once-per-fresh-contribution/resolution validation, and the real locked-business-row edit race. The test JVM exited and its executors terminated.

The isolated source snapshot is baseline `374bf688d3651abe4876bc9f951495f5d9ba0100` plus F028 files listed in `target/f028-verification/files.txt`. It excludes concurrent unrelated F029 administration edits, which temporarily made the shared checkout uncompilable during their refactor. F028 Java source hashes in the working tree match the successful snapshot. This extra build was justified by newly added test fixtures and the concurrent compilation boundary; it is not a repeat acceptance matrix. Logs and generated artifacts remain ignored under target/. No F029 source was changed or reverted by F028.

Eleven historical direct-engine completion integration suites now explicitly inherit CompletionAdapterIntegrationTest, which replaces the two production completion adapters with declared allow/noop test policy doubles. This keeps their engine/other-operation fixtures independent of console sessions, while F028's own suite exercises the real permission/validation adapters. All migrated sources compiled in the successful snapshot; those unrelated historical suites were NOT RUN because their underlying engine scenarios are unchanged.

Process state: no normal host or HTTP listener started; all F028 Maven/test JVMs exited. SQL Server Express was started by the user and left running. No development database was accessed. No commit, staging or push performed.
### Final review and handover

PASS — Independent fresh GPT-6.1 Sol Medium review after both implementation agents completed final handoffs and the lead integrated/verified the final F028 state. No actionable defects found in access-before-receipt, fresh-only hooks, adapter cardinality, error projection, fixture lock lifetime or historical test-policy migration. No code changes were needed after review.

PASS — Final intended diff/content review, four-document consistency, relative links and git diff --check. New F028 Java files were included in explicit whitespace inspection. All selected F028 checks are complete; no unresolved F028 decision or local acceptance block remains. Shared-tree F029 integration is outside this isolated verification, and migrated historical SQL suites were compiled but not broadly rerun. Changes remain uncommitted and unstaged by this task; unrelated concurrent changes are preserved.

**Commit delivery — 2026-11-16:** the owner requested committing only F028 while preserving concurrent F029 work. The F028 commit includes the implementation, focused tests and handover recorded above; no push is authorized. Earlier uncommitted-state notes describe the pre-commit handover.
