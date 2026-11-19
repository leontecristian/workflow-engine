# Implementation record — F032 host contract review

Revision 4 · IMPLEMENTED · 2026-11-19. Integrated working-tree changes based on `f83c101`.

Core discovers task capabilities by task-type code and resolver beans by identifier within each policy. Validation consumes metadata without resolving assignments. Fresh start checks every pinned human stage, fresh completion rechecks task and service capabilities, and stage entry validates the result from the registered resolver.

Core snapshots supported command operations during wiring and dispatches typed task, completion and cancellation requests. Current access protects receipt lookup. Fresh execution retains permission and validation checks, with reassignment destination validation supplied by its own host bean. Sandbox registrations, policies and fixtures and the web read wiring are migrated. The [core guide](../../../workflow-core/README.md#migrating-to-f032) and [web guide](../../../workflow-web/README.md#f032-java-adapter-migration) explain the coordinated Java rebuild. Stored definitions, receipts and execution evidence keep their existing formats.

## Unit and Spring wiring verification

**PASS** on Amazon Corretto 21.0.13 and Maven wrapper 3.10.0, compiling for Java 17. The isolated reactor at `target/f032-verification` used byte-matched module sources and POMs from the integrated checkout and the repository's definition fixtures. Ran the wrapper's `test` goal with `-B -ntp -o`, `-Dsurefire.failIfNoSpecifiedTests=false` and `-Dtest` restricted to the suites below. Used the Resolver's local lock factory and GAV mapper for the offline cache and supplied the cached Mockito 5.14.2 JAR as a startup Java agent through Surefire's `argLine`.

- **Core, 118 tests.** `HumanOutcomesUnitTest`, `SequentialDefinitionUnitTest`, `RuntimeHostSupportUnitTest`, `SharedAssignmentUnitTest`, `AllAssignmentContractUnitTest`, `FamilyStartUnitTest`, `WorkflowAuthorizationUnitTest`, `RuntimeCommandsUnitTest`, `AuthorizedRuntimeReadUnitTest`, `CancellationCommandUnitTest`, `ReassignmentUnitTest`.
- **Web, 32 tests.** `WorkflowRuntimeReadBoundaryUnitTest`, `WorkflowRuntimeWebUnitTest`, `WorkflowReassignmentWebUnitTest`.
- **Sandbox, 20 tests.** `ConsoleCapabilityDiscoveryUnitTest`, `SandboxCompletionUnitTest`, `ConsoleRuntimePolicyUnitTest`, `ConsoleRuntimeAdaptersUnitTest`, `ConsoleReassignmentUnitTest`, `ConsoleSharedAssignmentUnitTest`.

- **A01–A04 PASS.** SINGLE-only discovery and stage resolution, adding ALL under the same logical identifier, roster and shared-target validation, empty and malformed registries, task catalog diagnostics, and equivalent provenance without invoking a resolver during validation. The sandbox condition was tested with sample registrations enabled and disabled.
- **A05–A08 PASS.** Tested the completion-only permission sequence, denial and authorized receipt recovery, exact support for the ownership operations, typed request constraints, and policy failure categories. Reassignment tests covered picker authorization and the exact destination check on fresh execution. Cancellation used both instance scopes. Sandbox fixtures exercised VIEW/DECIDE, linked business access, administrator grants and allowed destinations.
- **A09–A10 PASS.** Checked a missing later-stage resolver for fresh start and a missing pinned outcome for completion, with receipt recovery bypassing fresh checks. Spring wiring accepted absent and empty authorization and rejected ambiguous and invalid declarations. Destination configuration was required on fresh reassignment only. A partial-host read hint stayed positive while the unsupported completion command failed before calling policy.

The initial Maven invocation could not open the shared cache lock. After switching to process-local locking in offline mode, the first compile exposed a missing import. Mockito's attempted dynamic attach was interrupted after a Windows pipe error and restarted with the startup agent. The first completed core run exposed missing definition fixtures in the snapshot and a fresh-start test lacking the host-link intent required by the command. After correcting the fixture setup, reran the two affected core suites and the previously unrun web and sandbox suites. The nine other core suites passed on the same production sources. Final total across the selected suites was **170 passed, zero skipped**.

## Packaging and remaining verification

**PASS** for all three Maven modules. Ran `./mvnw.cmd -B -ntp -o '-Daether.syncContext.named.factory=rwlock-local' '-Daether.syncContext.named.nameMapper=gav' '-Dmaven.test.skip=true' package` once on the verified integrated snapshot after both code changes.

At the initial implementation handover, SQL Server integration was **NOT RUN** and the migrated fixture sources had compiled. Live browser checks and APOLO acceptance remain **NOT RUN**. Prior [F021 assignment and roster evidence](../F021-all-collective-review/implementation.md) and [F028 transaction evidence](../F028-completion-authorization-validation/implementation.md) remain applicable to unchanged persistence and transaction behavior (**REUSED**).

The independent review found no actionable defects. The final diff, changed Markdown links and whitespace passed inspection. The packaged JARs contain no retired adapter classes. The two existing task context files were preserved.

## SQL Server integration follow-up — 2026-11-19

**PASS** against `localhost:1433/apolo_workflow_test` using the guarded integration-test profile with Windows authentication. Maven wrapper 3.10.0 on Amazon Corretto 21.0.13 reported **9 integration invocations passed, zero failures, errors or skips**. The separate `IntegrationDatabaseGuardUnitTest` suite passed all 10 cases.

- `SequentialHumanStagesIT`, 2 invocations of `optionalStageResolverRunsOnlyForReachedBranch`. Published and ran both routing branches with discovered resolver and task-type beans.
- `CompletionAuthorizationIT`, 2 cases. Tested booking VIEW without DECIDE, then fresh validation and authorized receipt recovery on an ALL roster.
- `SharedAssignmentIT`, 1 case, `requiredOwnershipDiscoveryReleaseReplayAndRollback`. Exercised shared task discovery, claim and release policy, and receipt access.
- `ReassignmentIT`, 2 cases. Exercised permission and destination denial, receipt recovery after closure, and the permission recheck against a refreshed assignment.
- `CancellationIT`, 2 cases. Exercised cancellation of a claimed SINGLE task and current access versus fresh cancellation privilege on receipt recovery.

Ran `./mvnw.cmd -B -ntp -o '-Daether.syncContext.named.factory=rwlock-local' '-Daether.syncContext.named.nameMapper=gav' -pl workflow-test-host -am -Pintegration-tests '-Dtest=IntegrationDatabaseGuardUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=<focused method selectors>' verify` in the existing `target/f032-verification` reactor snapshot. The method selection and results are captured in its `workflow-test-host/target/failsafe-reports`. Mockito 5.14.2 was loaded at JVM startup via `JAVA_TOOL_OPTIONS`.

The initial sandboxed run passed the guard suite and stopped all nine integration invocations at context startup with a localhost socket permission error. The retry with local connection access completed at 10:16 Europe/Bucharest. Liquibase found 16 previously applied changesets and ran none. The test JVM, including the ephemeral HTTP listener used by `SharedAssignmentIT`, exited. The broader SQL Server suite was outside this selection.
