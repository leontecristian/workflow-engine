# Plan — F011

Targets [specification revision 1](spec.md). T02–T06 implementation and guarded test-database verification are authorized by the subsequent owner request. Follow [package conventions](../../package-structure.md), [coding standards](../../coding-standards.md) and the two-module boundary. No dependency, public command format or migration change is planned.

## P01 — Definition model and validation (R01/R08; AC01–04/13)

Under `io.github.leontecristian.workflow.core`, change `dto.definition.document.MinimalWorkflowDefinition` from one Human to an immutable node-keyed collection. Keep exact per-node outcome lookup. In `service.definition`, update RoutingDefinitionModel, ExecutionValueDefinitionModel, MinimalDefinitionLoader, DefinitionValidationService and WorkflowDefinitionRuntimeReader; inspect every singular-human caller and bundled schema under `schemas/workflow-definition`.

Validate each node's contracts and host capabilities, retaining draft validation context/schema digest checks and using the specified validator identity. Generalize human-bypass detection to stop traversal at any human node; retain whole-graph reachability, reverse END reachability and acyclicity checks. Validate outcome routes per source human. All supported parser paths must share these rules, including plain graphs with no variables/gateways. Keep existing node/edge limits rather than introducing a stage-count configuration.

## P02 — Stage entry and progression (R02–04/R08; AC01/02/05–07/10/11)

In `service.runtime`, update RuntimeServiceProgression to return the reached human node or END. Resolve outgoing human edges from the completed node plus submitted outcome; stop at any destination human. Retain existing ServiceActionContext construction and per-command RuntimeProgression budget.

Update WorkflowExecutionService to resolve the active contract by activity.nodeKey and validate instance-position agreement. Share focused stage-entry logic between start and completion: catalog lookup, host resolver call with the reached node key, assignment validation, new activity/task persistence and existing entry/history facts. Keep helpers in `service.runtime`; no mirrored interface or new SPI. Reuse existing `dto.runtime.AssignmentContext`, assignment result and registered resolver boundary.

Replace the fixed first-stage history assumptions with checks against each activity's own activationSequence and retained evidence. Preserve RuntimeDefinitionPins and the early aggregate claim. Close the old activity before persisting the successor, verifying Hibernate flush ordering against the existing active-activity constraints. In `entity.runtime`, extend WorkflowInstanceEntity's guarded waiting transition beyond its current new-instance-only waitAt precondition; preserve separate start and existing-instance invariants. Inspect `repository.runtime` mappings/queries without adding storage. If a migration or public contract change proves necessary, reconcile the specification and plan before dependent implementation.

## P03 — Results, reads and reliability (R03–06/R08; AC04/06–10/13)

Retain `dto.runtime.WorkflowExecutionResult`: completed task/activity snapshots plus resulting instance state for completion; newly entered task/activity for start. Build results only after capturing the final managed version. Keep the existing `exception.runtime` categories and receipt codecs/fingerprints in `service.runtime`; verify receipt integrity still binds completion to the submitted task after later progression.

Audit bounded exact activity/task/submission readers and historical contract lookup. Resolve a historical contract through its own activity node and pinned definition, not the current instance position. Keep same-transaction final-submission visibility and immutable old evidence. Preserve current host authorization, durable recording and replay-before-state-check ordering. Test task keys reused in separate activities to expose accidental instance-wide key lookup.

## P04 — Sandbox and console (R07; AC05/06/08/12)

Under `io.github.leontecristian.workflow.testhost`, inspect/update `controller.runtime`, `service.runtime` and `dto.runtime` only where needed for existing task-contract/read/result/graph behavior. Review ConsoleInstanceGraph and ConsoleRuntimeController for assumptions about one activity or completion always ending an instance. Reuse `service.sandbox.SandboxAssignmentResolver` for the documented example; test adapters can assign distinct users by AssignmentContext.humanNodeKey without introducing a production role API or configurable resolver inputs.

In `workflow-test-host/frontend/src/app`, inspect `pages/tasks`, `pages/runtime/graph`, `pages/commands` and instance views. Distinguish completed task from active workflow, refresh authorized current work, retain the old frozen command and allocate a fresh one only for a new task. Use exact stage contracts for actions/comments and graph links. Preserve safe rendering, CSRF, pending states and explicit unavailable graphs.

Add runnable JSON technical fixtures in this feature folder during implementation, following the spec's demonstration. Include direct sequential, optional-stage/service and three-stage cases; keep the process documents limited to these four files.

## P05 — Planned verification

- **Isolated unit tests:** graph/parser paths and diagnostic pointers, per-node capability validation, exact stage selection, assignment context, result/receipt identities, historical reads, dynamic activation sequences and command budget boundaries. Cover AC01–06/08/11/13 without database/context startup.
- **Guarded SQL Server integration:** use the existing host DatabaseIntegrationTest harness on `localhost:1433/apolo_workflow_test`, serialized against other runs. Verify successive activity insertion/flush ordering, one committed successor, actual managed versions, host and workflow rollback in both directions, replay after further progression, exact submissions/history and durable handoff. Exercise failures after effects and after successor creation, plus a three-stage case retaining earlier commits. Cover AC05–11/13 with real transaction evidence; unit tests are insufficient for these properties.
- **Build/regression:** verify JDK 21, use the root Maven wrapper with Java 17 target, explicitly select known unit suites and use `-Pintegration-tests verify` for guarded integration. Inspect test discovery if changed. Run the existing F010/F009/F008 regressions, frontend unit suite and production build/CSP checks with pinned tooling. Record exact commands and counts separately from runtime acceptance.
- **Browser/HTTP:** exercise AC12 and observable AC04–06/08/13 against a task-authorized host. Demonstrate waiting after first approval, second-stage completion/rejection, different contract/comment rules, old-task read/replay, stage-specific graph, unauthorized access, pending controls and frozen recovery. Use the guarded integration profile for a temporary harness; normal development-host startup is not implied. Record/stop only processes started for this verification after checking identity.
- **Documentation:** review requirement/task/result traceability, local links, examples and whitespace; run `git diff --check` and include added files in checks. Runtime checks use only the guarded test database; retain the historical preparation checks.

Actual outcomes belong in [implementation.md](implementation.md). Earlier feature acceptance reconciliation and APOLO compatibility/production gates remain independent; do not close them from F011 sandbox evidence.

Implementation inspection: receipt validation must permit ACTIVE resulting instances while retaining COMPLETED submitted task/activity snapshots, and later task-created evidence must bind to its own entry command/actor rather than the starter. Host task lists and graph links must apply existing exact-task assignment authorization before disclosing successor work. These implement R03/R05–07 without a new public field or migration.
