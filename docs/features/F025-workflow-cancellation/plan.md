# Plan — F025 workflow cancellation

Targets [specification revision 1](spec.md). Planning only; implement only after a later owner instruction. Existing F024 changes are concurrent work and must be preserved.

## Baseline and approach

The schema already represents cancelled instance/activity/task lifecycles, termination_reason and INSTANCE_CANCELLED task closure. Only ALL sibling cancellation currently executes. Existing result DTO/receipt validation assumes task-bearing commands, so add a separate cancellation result codec rather than relaxing those invariants. The viewer already recognizes CANCELLED but needs action availability, reason display and verified graph/evidence handling.

No migration is planned: existing string event/operation columns and lifecycle constraints accommodate this slice. Verify those mappings statically before coding; if a concrete incompatible constraint is discovered, reconcile this plan and specify an ordered preserving changeset before implementing it. Never edit applied migrations. No definition changes, schema reset or APOLO access are needed.

## P01 — Core contract and aggregate mutation (R01–R05)

Exact package roots under io.github.leontecristian.workflow.core:

- dto.runtime: CancelWorkflow, WorkflowCancellationResult, WorkflowCancellationCommandResult, WorkflowCancellationContext, detached cancellation authorization context and CANCEL_WORKFLOW operation.
- service.runtime: extend concrete WorkflowExecutionService with cancelWorkflow/findCancellationReplay; add WorkflowCancellationAuthorization host SPI (canAccess/canCancel), WorkflowCancellationEffect callback and integrate RuntimeInputs, RuntimeJson, RuntimeViews and RuntimePersistenceErrors as needed. These extension boundaries are justified by host policy/effects, not a service/implementation abstraction. Resolve the authorization adapter optionally for existing consumers, but fail closed when cancellation is invoked without it.
- entity.runtime: bounded cancellation methods on WorkflowInstanceEntity, WorkflowActivityEntity and WorkflowTaskEntity, preserving completed evidence and assignment. repository.runtime: reuse bounded instance/activity/task and receipt queries; add only a concrete missing query.
- exception.runtime: add CANCELLATION_FORBIDDEN and CANCELLATION_EFFECT_FAILED; retain existing sanitized error mapping, with the latter a server-side command failure rather than an invalid-input response.

Use the current transaction/receipt pipeline, with typed cancellation receipt decoding dispatched before existing task-result parsing. Schema 7 is cancellation-only; preserve supported prior codecs and F024 request normalization. Freeze validated command data. Claim the aggregate before fresh effects; close only OPEN tasks in stable order, append R05 evidence, flush and capture actual version, store receipt and hand off events in the same outer transaction. Do not resolve a final ALL output or load service capabilities as a prerequisite to stopping valid waiting work.

Implement the explicit R02 cancellation-effect overload: current execution has no general existing-instance post-claim callback to reuse. Pass detached context and invoke after the early claim and before closures, only on fresh execution. The sandbox uses the no-effect overload. Integration tests supply a participating effect to verify the supported host boundary and rollback.

## P02 — Shared transport and host adapters (R02/R03/R06)

Under io.github.leontecristian.workflow.web, extend controller.runtime, service.runtime (RuntimeWire/RuntimeErrors), integration.runtime (WorkflowRuntimeCommandHost/WorkflowRuntimeReadHost), and dto.runtime where necessary. Keep configuration.WorkflowRuntimeReadConfiguration and WorkflowRuntimeCommandConfiguration independently activated. Add the exact cancellation mutation/replay/availability routes in R06; do not activate them through the viewer configuration alone. New read-host availability support defaults to unavailable for nonparticipating hosts; no permissive command implementation.

In io.github.leontecristian.workflow.testhost.service.runtime extend ConsoleRuntimeCoordinator, ConsoleRuntimeTransaction, ConsoleRuntimeCommandHost and ConsoleRuntimeReadHost; add the core authorization adapter using ConsoleRuntimeAccess. Wire through configuration.console. Authentication/CSRF, instance access, administrator cancellation, transactional commit and fresh-transaction lookup remain host-owned. Preserve request links/status. Document adapter source changes and coordinated core/web/host deployment; old writers must not run against aggregates newly cancelled by F025 without cancellation-aware runtime handling.

## P03 — Shared UI and affected reads (R05/R06)

Edit workflow-ui/projects/workflow-ui/src/lib/instance-viewer.ts, runtime/contracts.ts, runtime/models.ts and runtime/command/command-buffer.ts / command-recovery.ts for optional cancellation transport, typed result, frozen envelope and recovery. Add a focused Material cancellation dialog within that library. Extend models/read-validation/presentation and affected task/activity rendering only where needed. Inspect web service.instance.WorkflowInstanceGraphService and WorkflowInstanceViewerService for new events/stopped position; preserve earlier graph interpretation and exact-task grants.

Wire sandbox transport under workflow-test-host/frontend/src/app using existing host providers. Retain optional command injection for read-only/NgModule consumers. No new global HTTP/theme/security provider, cancellation management page or technical recovery panel in the ordinary user flow. Show reason as text, preserve pending state across uncertain outcomes, and refresh after committed success.

## P04 — Focused verification and delivery

Baseline evidence to reuse at implementation handoff (REUSED, not fresh PASS): F017 revision 3 for unchanged claim/ownership and command-buffer principles; F021 revision 2 for unchanged fixed roster, completed submissions and ACTIVITY_RESOLVED behavior; F015 for unchanged separate activation and host transport ownership. Cite their implementation records and assess actual changed paths. Their checks do not establish new cancellation, cancellation receipt decoding or cancelled-activity rendering. F024's handover changed to IMPLEMENTED revision 1 during this planning session; its recorded checks are not fresh F025 evidence. Coordinate before editing shared RuntimeJson/WorkflowExecutionService files and preserve its delivered family/exact-version receipt codecs.

Fresh checks:

1. Isolated core/web/host/UI suites for reason/authorization/DTO codecs, receipt compatibility, event shaping, partial ALL read invariants, routes, availability and frozen UI command behavior (AC01–AC04/AC07). Use existing suites where they cover affected paths; add only concrete gaps.
2. Guarded SQL integration tests in workflow-test-host/src/test/java/io/github/leontecristian/workflow/testhost using DatabaseIntegrationTest against localhost:1433/apolo_workflow_test, serialized with other agents. Cover committed SINGLE/partial ALL cancellation, maximum roster, race/duplicate ordering and injected rollback with preserved prior contributions/effects (AC01/02/04/05/06). Exercise real host transaction and outbox; clean only test-owned fixtures.
3. One selected Maven packaging command using JDK 21 and the wrapper; include console-ui packaging when verifying the built shared frontend. Do not repeat its builds separately. Keep unit, integration and packaging outcomes distinct.
4. Focused browser check of instance action/dialog, reason rendering, cancelled graph/tasks, stale/uncertain response handling and read-only consumer wiring (AC07). Use retained/test-owned fixtures and an authorized test-target setup. Normal development-host startup requires task-specific authorization because it migrates apolo_workflow_dev; do not redirect its normal URL. Record and stop only verification processes started by this task. If browser setup is blocked, record that gap rather than access an unauthorized database.

Completion: implemented R01–R06, focused checks complete with honest gaps, exact contracts documented in library READMEs, final diff/whitespace reviewed and tasks/implementation reconciled. Stop after relevant checks pass; do not reopen historical acceptance matrices. APOLO remains a separate gate. No commit or push unless requested.
