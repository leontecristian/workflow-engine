# Plan — F010

Targets [specification revision 1](spec.md). Implementation authorized by the owner on 2026-11-06. Follow package/coding standards and the existing two-module boundary. No migration or new public command shape is planned.

## P01 — Definition contract and model (R01–03/08)

Under `io.github.leontecristian.workflow.core`, update `dto.definition.document.MinimalWorkflowDefinition.Human` to hold an ordered immutable outcome list. Centralize exact outcome/transition lookup in `service.definition` model construction or a focused helper; never use get(0) for semantics.

Update bundled schemas under `schemas/workflow-definition`, MinimalDefinitionLoader, ExecutionValueDefinitionModel, RoutingDefinitionModel and validation identity/context handling in `service.definition`. The current routing parser requires a gateway and the other parsers assume a linear graph. Consolidate supported graph checks so minimal outcome branching works without routing through an artificial gateway. Enforce the complete outcome-to-edge mapping and universal-human-path/reachability/cycle rules. Keep existing F008/F009 checks and generic budgets. Validate unsupported guards/results/policies explicitly.

## P02 — Runtime selection and evidence (R04–06/08)

In `service.runtime`, change WorkflowExecutionService to validate the requested pinned outcome and its comment flag, then pass that selected outcome explicitly to RuntimeServiceProgression. Extend traversal's internal signature with the selected human outcome (absent during start); resolve human edges by both from-node and outcome. Start/service automatic edges and exclusive gateways retain their current behavior. Do not infer selection from model order, current JSON or a hidden mutable field.

Update internal model callers, including `service.runtime.RuntimeDefinitionPins`, read/graph builders and tests. Keep existing `dto.runtime` commands/results, `exception.runtime` categories, history schemas, exact fingerprints and `entity.runtime`/`repository.runtime` storage. Verify all TASK_COMPLETED/ACTIVITY_COMPLETED/TRANSITION_TAKEN facts use the actual selection. Preserve start pausing, completion-to-END behavior, early aggregate claim, final-version capture and receipt-first ordering. Inspect call sites rather than assuming only WorkflowExecutionService uses the singular model field.

## P03 — Test host and demonstration (R07)

Under `io.github.leontecristian.workflow.testhost`, inspect/update existing `controller.runtime.ConsoleRuntimeController` task-contract/completion endpoints and `service.runtime` graph/read mapping only as needed. The controller already enumerates the definition outcomes. The Angular `frontend/src/app/pages/tasks` form currently has a selector plus generic completion button; replace it with explicit outcome actions using the returned contract, retaining shared comments and existing frozen-command recovery. Check `pages/commands`, `pages/definitions` and `pages/runtime/graph` for singular-outcome assumptions. Do not add endpoint types or APOLO-specific screens.

Use the spec's complete example and derived service/JSON/gateway fixtures in the existing sandbox. Reuse existing registered transactional sample actions where possible; distinct branch evidence should prove unselected effects do not occur. Do not introduce production business services for the demonstration.

## P04 — Planned verification

- Isolated unit suites: declaration boundaries and diagnostics; all parser-dispatch combinations (plain, service/value, gateway); route totality/uniqueness; multiple ENDs without gateway; shared destinations; order independence; selected comments/outcomes; unchanged exact fingerprints, JSON semantics and history fields. Cover AC01–06/08/10/12.
- Guarded host database integration on localhost:1433/apolo_workflow_test: successful distinct outcomes, same-command replay/conflict, competing distinct commands, full rollback after selected-path host/JSON effects, actual final instance version and exact history/pin/submission reads. Cover AC04–10/12. Serialize database runs; preserve unrelated fixtures and never reset databases.
- Build using root Maven wrapper with selected JDK 21 and Java 17 target; frontend production build/CSP checks using project-pinned tooling. Run relevant existing unit/regression suites; separate these results from SQL behavior.
- Browser acceptance: two instances for approve/reject, required reason errors, correct ordered buttons and pending states, unauthorized/wrong-user access, refresh/history/graph, frozen recovery/replay and selected/unselected service effects. Cover AC11 and observable parts of AC04/05/08/10/12. Use only an authorized host/database; starting the normal dev host is not implied by test-database authorization.

Record actual commands/results/gaps in implementation.md. Implementation verification is authorized under the repository test-database rules. Use an isolated frontend copy if another process holds the shared toolchain open. A temporary browser harness must use IntegrationDatabaseInitializer and the integration-test profile; never start the normal development configuration.
