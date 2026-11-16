# Implementation — F029

## Delivered scope — 2026-11-16

The owner explicitly authorized implementation of approved revision 6 and its plan on 2026-11-16. T02–T04 are complete. Shared web calls existing core administration services and the synchronous host audit recorder under managed REQUIRED transactions. Separate identity/authorization/audit interfaces, host Clock, the specified audit record, identity/correlation errors and 100-nanosecond timestamp truncation are implemented. Sandbox command forwarding and ConsoleManagement are removed. No core, schema, UI or runtime command behavior changed; web already declares spring-tx, so no dependency was added.

WorkflowAdministrationService is internal web coordination despite Java-public methods for controller access/proxying. Hosts own infrastructure, annotation interception, manager selection and business orchestration. Outer transactions retain final commit ownership. The sandbox preserves schemaVersion 1, UUID storage, evidence bounds and failure injection using the supplied context.

## Historical work and reversal

The initial request was mistakenly interpreted as implementation authorization. On 2026-11-16 the owner required design discussion first and reversal of only this task's code changes. The interface edit and two added Java files were reverted; concurrent F028/runtime and documentation work was preserved. Subsequent work was documentation only. Revision 6 was approved READY without implementation authorization at that time. Historical readiness checks passed document consistency, relative links and whitespace; unit/build/database checks were NOT RUN and no F029 process remained. Explicit authorization in the current task starts a new implementation attempt; it does not recast the reverted attempt as authorized.

## Verification — authorized revision 6 working tree

Environment: Windows, Corretto JDK 21.0.13 x64 (java -version inspected), Java release 17, root Maven wrapper. Helpers edited disjoint web/host test files and ran no builds/database checks. The lead serialized all Maven/database runs. No other Java process was listed before integration. Database checks used only the guarded localhost:1433/apolo_workflow_test profile; no development database startup/access occurred.

- **PASS — isolated unit tests:** root wrapper -B -ntp with -Dtest=WorkflowAdministrationUnitTest,WorkflowAdministrationServiceUnitTest,AdministrationWireUnitTest,WorkflowRuntimeWebUnitTest,ConsoleAdministrationUnitTest,ConsoleDefinitionManagementUnitTest and -Dsurefire.failIfNoSpecifiedTests=false test: 46 tests, zero failures/errors/skips (41 web, 5 host). Covers transport/security/CSRF, missing each dependency, identity/correlation failures, exact forwarding, one truncated Clock capture, audit mappings/no-ops/deletion, serialization/bound and failure propagation. Initial sandbox invocation failed on Maven cache locks before compilation; approved cache-access retry passed. Ignored log: .local/f029-unit.log.
- **PASS — guarded SQL:** root wrapper -B -ntp -Pintegration-tests -Dtest=NoF029UnitRerun -Dsurefire.failIfNoSpecifiedTests=false with the selected -Dit.test below, then verify: four tests, zero failures/errors/skips. Successful shared HTTP write committed audit with trusted actor/UUID correlation. Actual audit insertion followed by injected failure rolled back creation/allocation and deletion. A successful managed service return did not prevent outer rollback; caught audit failure marked the joined transaction rollback-only and produced UnexpectedRollbackException at completion. State observed after owner completion confirmed rollback. Test hooks cleaned owned fixtures. Liquibase reported 16 previously applied changesets, zero new. Ignored log: .local/f029-integration-focused.log.
- **PASS — packaging:** selected integration verify packaged all three modules and executable host. An earlier verify with -DskipTests packaged successfully but also skipped Failsafe; it is build evidence only. Repeat verify was justified to execute accidentally skipped required integration checks, using a nonmatching Surefire selector to avoid repeating passed unit suites.
- **REUSED — unchanged UI/domain behavior:** [F014 revision 3 final verification](../F014-definition-catalog-administration/implementation.md) and [F023 revision 3 completed SQL/browser follow-up](../F023-create-next-definition-version/implementation.md), because UI, HTTP routes/payloads and core lifecycle/counter code are unchanged. Their limitations remain; new transaction behavior is established only by the fresh checks above. Browser rerun NOT RUN, as planned for unchanged rendering/routes.

Selected integration suites/methods:

```text
SharedAdministrationIT,DefinitionVersionCommandsIT#rollbackRestoresDeletionAndRemovesCreationAndReservationWithActualHostAudit+outerRollbackAfterSuccessfulSharedServiceReturnRemovesMutationAndAudit+joinedAuditFailureMarksOuterTransactionRollbackOnly
```

After checks, formatting-only whitespace cleanup and explicit test imports were applied; executable behavior is unchanged. Source/dependency review confirms host → web → core, unchanged core, no web-installed transaction manager/global interception, and proxy calls from controllers.

## Remaining work and process state

PASS — fresh independent GPT-6.1 Sol Medium review found no actionable defects against revision 6. Review was read-only; it did not repeat tests or database access. Final intended diff and new files reviewed; git diff --check passes. Four feature documents, 251 relative link targets and all 10 new files passed link/whitespace/final-newline checks. Packaged JAR inspection confirms new coordinator/interfaces are present and both removed forwarding classes are absent. APOLO effective dependencies and production transaction/security/audit acceptance remain separate under ASM-01/02; no local implementation blocker. No broader core/browser suite selected.

Failsafe JVM PID 25700 (including temporary embedded server on port 60379) exited normally. Post-run lookup found no process with that PID. No separate host was started and none is left running. The owner subsequently authorized committing the intended F029 changes using the repository commit format. Push remains unauthorized.
