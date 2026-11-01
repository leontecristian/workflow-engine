# Plan — F007 Typed bindings and service tasks

**Status:** READY. Applies to [spec revision 2](spec.md), with G01–G04 resolved. This remains the intended approach for revision 2; actual delivery and verification are in [implementation.md](implementation.md). Follow [package conventions](../../package-structure.md), [coding standards](../../coding-standards.md) and [development workflow](../../specification-driven-development.md).

## 1. Implement the selected contracts

Inspect current DefinitionDocumentCodec, MinimalDefinitionLoader, DefinitionValidationService, WorkflowDefinitionRuntimeReader and MinimalWorkflowDefinition/ExecutableDefinition before broadening validation. Runtime and publication must share the same supported structural model, without runtime rechecking authoring selectability or rewriting validation evidence.

Inspect StartWorkflow, WorkflowExecutionResult, RuntimeInputs/RuntimeJson, RuntimeProgression, RuntimeHistoryBatch and the host recovery buffer. Current commands have no input field; the result contains one activity/task, and history/receipts assume the minimal graph. Keep the selected chain to one human node and the existing result fields. Implement G01 overloads and G02 algorithm/result-schema dispatch. The current RuntimeJson verifier hardcodes 1–4/5–8 and activation sequence 3: retain that strict verifier for result schema 1, and add the schema 2 relational/state/range verifier. Do not weaken the legacy reader or reconstruct a receipt from current state.

The initial bindings are direct references, not expressions. Do not add Spring expression dependencies, a fact provider, generic graph framework or arbitrary bean lookup for this feature. Nonempty guards remain rejected until specified separately.

## 2. Packages and responsibility boundaries

Core package root: `io.github.leontecristian.workflow.core`.

| Exact package | Planned responsibility |
| --- | --- |
| `dto.definition.document` | Extended internal immutable scalar declarations, bindings and service-node model; preserve source JSON separately. |
| `dto.definition` / `service.definition` | Action capability descriptions, fresh publication checks, diagnostics and shared supported-model loading. |
| `dto.runtime` | Detached typed input/value/action/context/read contracts and G01 StartInputs/ScalarLiteral/ScalarValue, action descriptor/context/result and variable-page DTOs; result fields stay unchanged. |
| `service.runtime` | Concrete codecs/binding preparation/variable persistence/progression helpers and WorkflowServiceAction SPI/ServiceActionRegistry defined in G01. Keep existing execution/query services as entry points. |
| `entity.runtime` / `repository.runtime` | Controlled WorkflowVariableEntity creation/replacement and bounded variable retrieval under the instance claim. No host dependency on these packages. |
| `exception.runtime` / `exception.definition` | Stable errors and diagnostic mapping finalized in G01. |

Host root: `io.github.leontecristian.workflow.testhost`. Extend `configuration.console` for enabled-only registrations/settings, `controller.runtime`/`service.runtime`/`dto.runtime` for the existing SandboxStart transport and authorized variable inspection, and `service.sandbox`/`repository.sandbox`/`dto.sandbox` for the sample action, effect storage and effect-page DTO. Add effect reads to the existing instance endpoint family in controller.runtime; do not create duplicate controllers for start. Implement sample action as SandboxRecordQuantityAction and store as SandboxServiceEffectStore using the existing transaction-bound JDBC infrastructure. Action interfaces are justified host extension points; ordinary core services remain concrete.

Frontend work belongs in existing `workflow-test-host/frontend/src/app/data`, `pages/sandbox`, `pages/runtime` and `pages/commands`. Preserve exact string-encoded values, frozen command recovery, session/CSRF handling and local graph failure behavior. Do not implement frontend execution or new graphical editing.

## 3. Persistence and command progression

Use existing wf.workflow_variable typed columns and discriminator. Add focused mutation methods that enforce matching columns, type/nullability and immutable identity. Verify absence/typed-null representation against the F001 contract and current migration; G03 confirms no core migration. Implement only the G03 host migration 003-console-service-effects.xml and its ordered include, with the specified table, constraints/index and no seeded rows. Never alter applied changesets or insert startup seed data.

Start: basic bounded input/context validation → authorized receipt resolution → exact published-version lock/pin → decode/default inputs → allocate instance and establish required host links before dependent extensions → prepare/apply initialization → bounded node progression → final managed version/history/receipt/durable handoff. Use G01/G02 ordering and emit the exact history sequence defined there; all effects stay in the host transaction. Basic validation before replay checks only context, envelope/token types and limits; pinned input validation/defaults and capabilities run only after a receipt miss.

Completion preserves receipt-first handling, early checked/flushed instance claim and assignment/current-position checks. After accepting the task submission, progress through the post-review service chain to END. Any failure rolls back the completion and its effects. Each action receives detached validated arguments and trusted context; it returns detached values, never variable mutations. Validate outputs and compute all mapping writes before applying them together.

Use deterministic progression from edges, not array layout. Charge the automatic-step budget before invoking each action. Preserve no-reentry and no-automatic-retry rules. Read-only variable access must be bounded, authorized by the host and consistent with the instance snapshot/token defined in G01.

## 4. Sandbox delivery

Implement G03 sandbox.recordQuantity, its append-only service_effect table, admin inspection endpoint and startup-only failure settings. Exercise actions before and after the human task. The sample must prove rollback and replay rather than merely transform an in-memory value. Keep host credentials, fixtures and business effects outside core. Use explicit manual setup; no remote side effects or startup writers.

Extend start input entry, variable inspection and command export/restore under the existing roles and transport conventions. Keep legacy start callers accepted under G01/G02, including omitted inputs and old recovery envelopes. Preserve raw literal spelling in the recovery buffer and distinguish a typed-null variable from an uninitialized variable in presentation. The graph may remain unavailable for extended definitions; plain instance/task/history inspection must work. Apply the 256 KiB request allowance only to start and start-replay; keep other body limits and the global mapper unchanged. The graph service must recognize unsupported extended shapes explicitly rather than accidentally routing them through the old three-node projection; ordinary detail/history reads must use the extended pinned loader.

## 5. Planned verification

Documentation preparation phase: validate the four files, links, requirement/task/scenario traceability and whitespace; that historical phase performed no build, host, migration or database access.

Later implementation: inspect test discovery and select isolated suites explicitly under AGENTS.md. Unit coverage: codecs/defaults/presence (AC03–05), binding validation/registration/snapshot mapping (AC06–08), budget/progression failure ordering (AC12–13), canonicalization/backward replay fixtures (AC01/AC10), trusted-context/privacy and DTO boundaries (AC06/AC15). Frontend isolated tests cover input precision, frozen recovery, actor changes and graph fallback (AC10/AC14). These tests cannot establish SQL rollback/concurrency.

Use JDK 21 with Java 17 target and package core/host, including the opt-in console build, after relevant implementation. Inspect ordinary core JAR boundaries and packaged frontend assets. Actual commands/counts/results belong in implementation.md.

When runtime verification is authorized, perform AC02/AC09/11/13/15 with independent persisted-state observations, separate actors and genuinely overlapping requests. Inspect host effects, variable rows, task state, history, receipts and outbox across failure/replay; check final managed versions and unchanged applied migrations. Perform AC14 in a browser, preserving evidence for the specific artifact. Database integration tests require explicit authorization; a manual plan does not authorize a database test harness. Record and stop temporary processes unless the user requests otherwise.

## 6. Acceptance matrix and compatibility checks

| Scenarios | Isolated checks | Required implementation acceptance |
| --- | --- | --- |
| AC01/AC10 | Legacy golden v1 fingerprints/results; v2 raw literal identity; schema 2 range/ownership verification; old/new recovery-envelope parsing. | Replay actual old and new commands after deployment; compare receipt, event and effect counts and original snapshots. |
| AC02 | Ordered progression of the complete G04 fixture and exact event allocation. | Real admin start/reviewer completion, two persisted effects, variable rows and history 1–16. |
| AC03/AC06 | Every forbidden source/field, duplicates, type/nullability mismatch, absent or malformed registration and authorization interception. | Publication/runtime failure behavior and two-account API access, including masked unrelated-reviewer reads and admin-only effects. |
| AC04/AC05 | Numeric/date/Unicode boundaries, default application, missing/null distinction and codecs. | SQL Server exact round trips including negative LONG and normalized DECIMAL/DATETIME; rows for null versus absence. |
| AC07/AC08 | Required/optional output validation, uninitialized bound optional argument, prepared mapping order permutations and all-or-nothing validation. | Sample invalid-output mode rolls back its host row; mapped values persist exactly. |
| AC09 | Failure propagation/call order and no successful recorder invocation after rejected data. | AFTER/BEFORE failure and recorder failure with independent host/core/history/receipt/outbox inspection; preserve previous successful command. |
| AC11 | Early instance claim ordering and fresh-transaction replay recovery orchestration. | Genuinely overlapping start/completion requests; one committed effect/result, losing writes rolled back. |
| AC12 | Chains at 50/51 steps, human boundary, disconnected graph/cycle rejection. | No effect from an over-budget command; earlier committed commands remain. |
| AC13 | Per-value/map/event/command budgets, count-before-copy, page continuation, stale page detection. | Oversized required evidence rolls back action writes; no truncated history; concurrent variable read detects changed token. |
| AC14 | Exact scalar entry, actor/session clearing, frozen retries, graph fallback and admin effect-page visibility. | Packaged browser flow and explicit refresh/recovery; inspect actual transmitted values beyond JavaScript safe integers. |
| AC15 | Retired pinned execution, nonselectable catalog and missing/incompatible action behavior. | Retire/disable after publishing and verify existing execution; remove action support in a separate configured host run and observe visible failure with no partial effect. |

Add complete fixtures for two preexisting minimal receipt examples and the G04 extended receipt, with expected canonical JSON bytes and SHA-256 values computed from the specified algorithms during implementation. Test the bytes independently of the production encoder. Include same UUID with changed quantity, explicit default versus absence, decimal spelling, timezone spelling, actor and original token. Never regenerate the legacy expected values from the new implementation as the only assertion.

The structural schema and internal executable model must admit the selected chain while remaining incapable of publishing a gateway/guard/JSON result. Runtime reads, task contract endpoints, replay lookup, outbox/inbox display and graph fallback must handle the new event/result formats. New history is one event per accepted input-set/service success/applied variable mapping as specified; reserve all sequences through the existing aggregate protocol. Bound cumulative event details and batch count; do not add retry, partial flush commits or another transaction manager.

F007 is the explicit successor specification for its additions. Add short cross-references to F004/F005/F006 during implementation where their current supported-contract lists need extending, preserving their original revision/evidence statements. No retrospective acceptance PASS is permitted.

## 7. Handover

Reconcile spec/plan/tasks with actual behavior and evidence before marking implementation complete. Keep prior F005/F006 evidence historical, including newer owner walkthrough observations. Report unperformed acceptance and environment limitations explicitly. No commit/push or APOLO source move is part of this feature preparation.

## Implementation reconciliation - 2026-11-01

The implementation uses the existing minimal schema unchanged and a separate bundled scalar-service schema so legacy validation provenance remains stable. RuntimeScalarBindings prepares mappings, RuntimeVariables controls existing typed columns, RuntimeServiceProgression traverses the chain and ServiceActionRegistry supplies both capability paths. Variable reads use bounded rows, refreshed values and scalar instance-version queries before/after materialization. Receipt result schema 2 preserves the existing result fields; schema 1 retains its strict positions. No core migration, dependency upgrade, expression implementation or additional module was needed. The host-only migration is included but has not been executed. T09 remains subject to the planned authorized provider/browser observations.

## T09 execution reconciliation - 2026-11-01

The owner explicitly requested T09 after the implementation handoff, authorizing the manual host/migration/API/SQL observations in the acceptance matrix. The run used temporary loopback hosts, console HTTP commands and independent read-only SQL inspection; no database integration test suite was created or run. Invalid calendar input exposed an exception-translation defect; the implementation was corrected to the existing R02/G01 INVALID_INPUT contract and verified with an isolated regression and rebuilt-host check. Actual observations, artifact boundary, process cleanup and the browser-tool block are in the implementation record. T09 remains open for browser acceptance and the specifically unperformed provider cases, including an observed commit during variable-page materialization.

## Owner closure reconciliation - 2026-11-01

T09 is CLOSED at the owner's explicit request following the manual API/SQL observations and owner browser walkthrough/replay confirmation. The earlier execution reconciliation describes the state at that handoff. See implementation.md for the actual port-4200 evidence and remaining unverified follow-up coverage; closure does not retrospectively mark those cases PASS or close F005/F006 acceptance.
