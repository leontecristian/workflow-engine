# Plan — F026 task administration and reassignment

Targets [spec revision 2](spec.md). Revision 1 implementation was authorized on 2026-11-14; its results remain recorded. Revision 2 R07/AC07 implementation was authorized on 2026-11-14. Preserve the completed F025 cancellation behavior and handover.

Revision-2 verification is complete, including the previously unavailable live sandbox wiring check. Actual outcomes are in implementation.md. The follow-up reuses prior unit/build/responsive-fixture evidence and checks only the now-running host in Chrome; no rebuild, service restart or full acceptance rerun is selected.

## P01 — Assignment semantics and core commands

Under io.github.leontecristian.workflow.core:

- dto.runtime: ReassignWorkflowTask, REASSIGN_TASK, administration query/page-item DTOs and detached authorization/target-validation context. Reuse TaskAssignmentTarget and existing task-bearing command result; do not add originalAssignment to every DTO.
- service.runtime: concrete WorkflowExecutionService reassignment and typed replay; host-supplied WorkflowTaskReassignmentAuthorization for business access, action permission and exact destination validity. Integrate WorkflowTaskEligibilityService shared-provider support. These are real host boundaries, not interfaces mirroring concrete services. Recheck task-dependent business/action policy after the early aggregate claim refreshes the task, because the participating host transaction may have cached its former target; successful replay still returns before fresh privilege checks.
- entity.runtime.WorkflowTaskEntity: controlled OPEN-target replacement with USER assignee equality/shared-null reset. No new columns or duplicate original state.
- service.runtime.RuntimeTaskCreationEvidence: remove only target.equals(task.getAssignment()) from creation/current matching. Keep creation/linkage/time/schema/initial-owner and F020/F021 snapshot checks. Do not add latest-event lookup, chain validation or history-derived current state. service.runtime.RuntimeJson: operation-specific request/result codec and schema 8, preserving all earlier supported codecs. exception.runtime: scoped reassignment denial.
- repository.runtime and service.runtime.WorkflowRuntimeQueryService: targeted cross-instance task query/projections and metadata needed by R05; reuse joins and bounded pages. No browser iteration over instances, unbounded history scan, or repository-per-row loop for labels.

Existing submission validation compares a completed USER task with its effective target and completing actor; retain it because closed tasks cannot change. Audit all assignment consumers for the distinction between creation evidence, live eligibility and frozen receipts. Existing claim/release/completion uses current target and needs relevant regression checks, not a new eligibility model.

No migration is planned. Existing assignment columns, bounded event/operation strings and task lifecycle shape support the selected behavior. Inspect constraints before implementation; any discovered migration need must be reconciled with the explicit no-new-columns scope rather than silently adding storage. Preserve applied changesets.

## P02 — Host and shared transport

Under io.github.leontecristian.workflow.web extend controller.runtime, dto.runtime, service.runtime.RuntimeWire/RuntimeErrors and integration.runtime host interfaces for R05/R06. Keep configuration runtime read/command activation independent; optional feature support must not enable permissive defaults. Host-authorized administration pages, availability and target lookup are distinct from core execution authorization. Use detached workflow/standard types.

Under io.github.leontecristian.workflow.testhost.service.runtime extend ConsoleRuntimeReadHost, ConsoleRuntimeCommandHost, ConsoleRuntimeAccess, ConsoleRuntimeCoordinator and ConsoleRuntimeTransaction; add the reassignment authorization adapter and bounded fixture-backed target lookup. Wire through configuration.console. Preserve receipt-first business access, commit-before-return and post-rollback lookup. Do not introduce new identity/membership management or business side effects.

## P03 — Shared administration UI

Add a task-administration component under workflow-ui/projects/workflow-ui/runtime/src/lib and export through runtime/src/public-api.ts. Reuse existing shared task details (task.ts/task.html) for action and original history navigation; add a focused reassignment dialog. Extend src/lib/runtime/models.ts, contracts.ts and command/command-buffer.ts / command-recovery.ts for typed availability, search and frozen reassign envelope. Wire host transport, navigation and page routing under workflow-test-host/frontend/src/app. Follow the frontend style guide and optional command-provider conventions; preserve My tasks and instance-task page meanings.

Show current effective target and assignee. Original assignment is already visible through authorized TASK_CREATED history; do not add a separate reconstruction UI. Keep closed rows inspection-only and instance cancellation on its existing surface. No bulk actions or unrelated table framework.

## P04 — Verification, compatibility and handoff

**Revision 2 follow-up (R07/AC07, authorized):** update only the affected shared task-administration template/component/styles and outlined reassignment-dialog fields after authorization. Follow the Definitions template/styles as a presentation reference, without coupling the runtime package to administration internals or changing the host theme. Add the local visited-cursor trail needed for Previous, preserve existing filter transport/page-size choices and isolate page-specific layout rules from unrelated runtime consumers. No backend change, migration, selector conversion or full pagination-framework extraction is selected.

Run focused UI checks for cursor navigation/reset and empty continuation, the selected frontend build once, and visual inspection at widths covering R07's grid layouts and affected dialog. Compare the footer with Definitions and record focus/hover/disabled, long-text, validation and table/footer separation observations. Existing functional/browser evidence does not establish revision 2 appearance. Reuse unchanged backend/transaction tests; keep T07's separate default-query issue separate from this amendment. Documentation work itself requires only content/link/whitespace checks. For T09, the revision-1 matrices below remain historical/reused evidence, not newly selected checks. Use the database-free NgModule fixture at viewport widths 1200, 850 and 480 (measure the content/grid widths), with 55 rows for continuation at the unchanged page sizes and explicit delayed/empty-page modes; compare the actual Definitions footer in the same host. Inspect the rebuilt sandbox when its user-owned service is available; otherwise record the wiring gap without starting/restarting it or accessing a database.

Changed paths include creation-evidence interpretation, target/claim state, assignment-sensitive reads, new receipts, cross-instance authorization/querying and shared UI. Plan REUSED evidence from F017 revision 3 for unchanged claim-policy/recovery semantics, F020 revision 3 for snapshot encoding, F021 revision 2 for fixed roster and partial submissions, and F015 for separate activation. Cite owning implementation records and assess applicability against the actual revision; none proves reassignment. F025 revision 1 and its live Chrome follow-up are complete (handover at bf82a7a plus evidence update); reuse unchanged cancellation behavior, with fresh race checks only at the new reassignment boundary.

Fresh isolated checks: target transitions and no-op/closed failures; original-history reads including empty inputs and ALL snapshot references; USER completion after reassignment; frozen old receipts and new schema; target/access failure; query filters/continuation and concealed scopes; UI lookup/dialog/unknown outcome and read-only providers (AC01–AC06).

Focused guarded SQL tests in workflow-test-host/src/test/java/io/github/leontecristian/workflow/testhost using DatabaseIntegrationTest against localhost:1433/apolo_workflow_test: persisted identity/inputs/assignment/audit, transactional recorder rollback, same-command duplicate replay and controlled completion/ownership/cancellation races. Serialize database runs with concurrent work and clean only test-owned fixtures. Add no unrelated rollback/concurrency matrix.

Run selected JDK 21 wrapper packaging once, with console-ui when validating the packaged shared frontend; do not repeat its underlying builds. Focus browser checks on cross-instance page/filter/detail navigation, reassignment/claim reset, permissions, stale/unknown response and closed inspection. Use authorized retained/test fixtures; normal dev-host startup needs separate database authorization. Record/stop only processes started for this task.

Deploy compatible core/web/UI/host adapters together; old writers/readers that enforce immutable-target equality cannot coexist once reassignment is used. Preserve published definitions, original history and prior receipt decoding. APOLO policies/integration and production rollout remain separate.

Completion: specified behavior implemented, affected checks resolved or honestly recorded, library integration READMEs updated, tasks/results reconciled, final diff and whitespace checked. Stop after scoped checks pass. No commit/push unless requested.

## T07 — Default administration metadata correction

Use a narrow read-only node-name projection in core.service.definition.WorkflowDefinitionRuntimeReader and cache it per pinned version in service.runtime.RuntimeDefinitionPins / WorkflowRuntimeQueryService. A metadata list must not invoke executable-definition loading merely for labels. Validate retained node keys/names and required node linkage; keep loadPinned, canonicalization/hash checks, commands and operational evidence reads strict. No data rewriting, migrations, fallback suppression or skipped rows.

Fresh checks: isolated retained-metadata validation/caching and guarded ReassignmentIT metadata regression against an unsupported canonicalization marker on a test-owned version, while executable loading still rejects it. Reuse unchanged F026 command/UI evidence. Package corrected Java code, then visibly check the default OPEN list, exact filter/reset and closed inspection in the running Chrome console. Apply/restart the existing user-owned backend only with task-specific authorization; do not stop another session’s process. Record the actual running revision/process state.

## Owner layout follow-up

Split ID/name in the existing task table and use Definitions ID link styling. Change only the wide filter grid to six columns, preserving narrow fallbacks and existing action/footer/navigation behavior. Build shared UI and sandbox once, inspect rebuilt live Chrome for the single filter row, ID link styling/navigation and narrow overflow. Reuse prior functional/pagination evidence; no backend or database checks selected.
## Collapsible filter follow-up

Implement the owner-selected option 2 in the shared task administration component only: Status toolbar, initially collapsed advanced panel, applied snapshot chips and immediate remove/reset reads. Add focused tests for exact applied-vs-draft values, dependent kind/reference removal, failed reads and loading guards. Run focused suite and shared/sandbox builds once, then inspect rebuilt Chrome collapsed/expanded/chip removal/narrow behavior. Reuse unchanged commands/backend/navigation evidence; no database or service restart. Close T11 after focused live host verification of collapse/reopen, chip removal/dependencies and narrow presentation; retain earlier fixture evidence for detailed failure/loading and long-text cases rather than repeating the full matrix.

## Reassignment spacing follow-up

Keep a dialog-local grid with 16 px gaps and use the existing wrapping task action row for Reassign task. Verify the rebuilt controls/alignment, required Reason feedback and narrow rendering in Chrome. Once the owner supplies the running app, repeat only this live wiring/rendering check; reuse prior builds and unchanged functional tests. Actual results are in implementation.md.

## Destination search feedback

Use Material spinner and polite inline status beside Search; count accumulated loaded items only after successful lookup. Verify pending/success/empty/error/reset/continuation with focused existing dialog tests, build shared and sandbox once, and inspect rebuilt Chrome including an opt-in delayed database-free lookup. Reuse unchanged reassignment semantics; no database or service restart. Once the owner provides the current running bundle, verify only live lookup loading/count/reset wiring and close T13; do not repeat successful builds or the full feature matrix.
## Named TypeScript destination contracts

Add WorkflowReassignmentTarget and WorkflowReassignmentTargetPage to src/lib/runtime/models.ts (existing root exports). Replace inline shapes in the shared contract/dialog and sandbox transport; annotate the database-free fixture lookup. Preserve cursor pagination, endpoint and provider registration. Verify the shared declaration build plus sandbox/fixture TypeScript compilation; reuse runtime tests/browser evidence because types are erased and behavior is unchanged. No services or database checks selected.
## Task name text filter

Add optional taskName across core.dto.runtime.WorkflowTaskAdministrationQuery, core.service.runtime.WorkflowRuntimeQueryService, web.controller.runtime.WorkflowRuntimeReadController and UI runtime models/template/chip labels. Filter pinned metadata within the bounded candidate page; no repository/schema/executable loading change. Preserve existing constructor and cursor evidence. Verify focused core/web unit cases, UI filter/chip and transport tests, shared/sandbox build and rebuilt Chrome fixture interaction. Unit-only Java checks use JDK21; no DB checks selected for this metadata predicate. Obtain focused review after integration/checks; preserve earlier live handovers.