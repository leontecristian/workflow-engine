# Plan — F022 Previous-decision comments

Revision 1 — 2026-11-13; implements [specification revision 1](spec.md). T02–T06 authorized by the owner implementation request on 2026-11-13. Baseline inspected: `010fe82`, including implemented F021 despite stale project summaries.

## P01 — Definition contract (R01)

Extend `io.github.leontecristian.workflow.core.dto.definition.document` (`MinimalWorkflowDefinition` and focused immutable policy value types). Update parsing/validation in `io.github.leontecristian.workflow.core.service.definition`, including MinimalDefinitionLoader, RoutingDefinitionModel and ExecutionValueDefinitionModel where their paths apply. Cover all three strict JSON schemas in `workflow-core/src/main/resources/schemas/workflow-definition/`. Resolve node overrides against the pinned root, validate graph predecessor selectors and update validation identity. Preserve omitted-policy published loading; do not rewrite stored definitions or broaden supported graph shapes.

## P02 — Narrow retained-evidence query (R02–R04)

Add `PreviousDecision` and `PreviousDecisionPage` in `io.github.leontecristian.workflow.core.dto.runtime`; expose a documented query through WorkflowRuntimeQueryService in `io.github.leontecristian.workflow.core.service.runtime`. Keep selection helpers package-private in that service package if needed. No mirrored service interface or generic context framework.

Use the receiving activity's ACTIVITY_ENTERED sequence as the immutable cutoff. Extend bounded queries/projections in `io.github.leontecristian.workflow.core.repository.runtime` (WorkflowHistoryRepository/WorkflowActivityRepository and task repository as needed) to select eligible completed source activities/tasks and their exact completion facts. Include integrity checks for eligible completed tasks lacking facts; a history-only query that silently loses them is insufficient. Preserve existing strict evidence validation and avoid constructing F021 result arrays or decoding unrelated input snapshots. Load only bounded evidence batches; if strict decoding needs a complete history envelope, process one bounded batch under existing evidence capacities rather than retaining all source results.

Order/paginate by history sequence, validate exact IDs, map pinned labels and narrow fields, enforce R04 page capacities. Read-only host transaction participation suffices; no early write/receipt mutation. No new snapshot or migration: stable cutoff plus immutable retained facts supplies stable membership/content, and authorization remains current. Retention failure is an explicit error, not fallback to mutable variables.

## P03 — Shared read context and transport (R03/R04)

Extend WorkflowTaskContract in `io.github.leontecristian.workflow.web.dto.runtime` with resolved previousDecisions metadata; extend WorkflowRuntimeReadController in `io.github.leontecristian.workflow.web.controller.runtime` with the paginated route and existing strict wire/error conventions. Use the existing exact receiving-task access path in `io.github.leontecristian.workflow.web.integration.runtime` before each read. Do not require source-task grants or widen existing source/history routes. Review serialized page bounds after wire encoding; no new API activation group or required host policy SPI.

Document the neutral core read and optional shared HTTP path so APOLO can use either through its own authorized facade. The task context contains metadata, while entries load separately; neither list responses nor completion commands grow with approval-chain length.

## P04 — Shared presentation and sandbox (R05)

Update shared runtime contracts/models and the read adapter in `workflow-ui/projects/workflow-ui`, with task presentation in `runtime/src/lib/task.ts` and `task.html`. Add safe text, stage/outcome attribution, pagination and read-state handling; use existing task/session lifecycle protection. No duplicate sandbox/APOLO widget. Extend a maintained sequential sandbox example to show a workflow default and a selected-stage override, plus a prepared resolved-ALL fixture for targeted verification. Keep examples separate from unchanged published definitions. Authoring is JSON/import based; designer controls are deferred.

## P05 — Focused verification and stopping condition

Changed boundaries are definition interpretation, history-derived read selection, receiving-task disclosure and shared task rendering. Select existing definition/runtime-query/web/UI suites and add only uncovered AC01–AC07 cases. Exercise inheritance/invalid selectors, branched skipped sources, cutoff stability, SINGLE/ALL individual outcomes, no-result/null-comment evidence, corrupt/missing facts, exact identifiers, byte/count continuation and receiving-only versus source-only access. Include unsafe-text rendering and stale-response protection in UI checks.

Use guarded SQL Server `localhost:1433/apolo_workflow_test` integration checks for the new bounded joins/cutoff/paging, real evidence reload and receiving-task authorization. Use test-owned seeded states for later-stage and resolved-ALL views. No fresh full rollback, command replay or concurrency campaign is required because completion writes, claim logic, fingerprints and receipts do not change. If implementation changes those paths, reconcile this plan before broadening checks.

Candidate evidence for REUSED classification at implementation: [F010 revision 1](../F010-human-task-outcomes/implementation.md) for comment acceptance/fingerprint behavior; [F020 revision 3](../F020-human-task-input-bindings/implementation.md) for unchanged task-input snapshots; [F021 revision 2 delivery](../F021-all-collective-review/implementation.md) for unchanged ALL submission/resolution storage. Confirm applicability to the eventual diff. None proves this new projection or sharing boundary, and none is a fresh PASS here.

Run focused isolated units, the selected guarded read integration tests, one applicable JDK 21/Java 17 packaging command and shared UI build/tests. Use a prepared task for one browser check of default/override display and load-more/error rendering; rely on automated checks for policy combinations. Do not replay entire approval workflows merely to obtain UI state. Record actual results separately in implementation.md and stop only temporary processes started for verification after checking identity.

Complete when R01–R05/AC01–AC07 pass, the diff is reviewed and records are reconciled. Broaden only for a concrete changed dependency/failure. APOLO verification and historical unrelated gaps remain outside this feature. The earlier documentation-only pass is retained as historical evidence; this implementation executes the focused checks above.
