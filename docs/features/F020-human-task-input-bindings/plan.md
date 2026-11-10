# Plan — F020

Revision 3 — 2026-11-10; implements [specification revision 3](spec.md). The owner authorized implementation on 2026-11-10. No dependency or module changes are planned.

## P01 — Definition and value contract (R01)

Extend `MinimalWorkflowDefinition.Human` in core `io.github.leontecristian.workflow.core.dto.definition.document` with an inputs binding map and source-derived type metadata. Update `service.definition` parsing/validation and all applicable strict schemas under `workflow-core/src/main/resources/schemas/workflow-definition/`. Remove human-node inputContract from authoring schemas/templates and maintained examples; keep resultContract as the reviewer-answer contract. Preserve startPolicy.inputContract. Separate strict new-authoring validation from runtime loading of published legacy documents, accepting only the historical empty array on that legacy path without rewriting stored bytes/hashes. Report an actionable removal diagnostic for draft/import documents containing the obsolete field. Do not add reviewInputContract or enable scalar submission fields. Reuse existing key/type rules and F019 binding metadata; ensure minimal human-only flows are recognized without requiring a service/gateway to activate parsing. No new generic node framework.

## P02 — Entry-time snapshot and retained evidence (R02/R04)

In core `io.github.leontecristian.workflow.core.service.runtime`, resolve bindings in `WorkflowExecutionService.enterStage` using RuntimeVariables/RuntimeValueBindings and the F019 submission resolver. Extract a focused shared resolver only if needed to avoid duplicating service logic. Validate resolved source values and snapshot bounds before recording successful task creation.

Store the immutable snapshot in existing TASK_CREATED details, using new details schema version 5: retain version 4's taskKey, assignment and assigneeRef and add `inputs`, a map of input names to `{type, value}` using existing exact runtime token conventions. The pinned definition supplies bindings, source-derived types and source provenance. Write version 5 for configured review inputs; keep version 4 for unchanged tasks. No new table/column or migration is planned. Keep full values out of the durable TASK_CREATED payload and leave its delivery schema unchanged.

Update RuntimeTaskCreationEvidence and affected completion/claim readers for strict version 5 decoding while retaining versions 2–4. Required retained evidence must fail explicitly if absent or corrupt; do not reload source data. Keep existing host transaction, early instance write and receipt-first replay ordering. This new evidence is retained for as long as the task's operational reads require it.

## P03 — Authorized reads and shared display (R03/R04)

In core `service.runtime.WorkflowRuntimeQueryService`, expose detached input names, source-derived types and snapshot values via `taskInputs(instanceId, taskId)`, reusing `dto.runtime.WorkflowValue`. Reuse existing `repository.runtime` history reads rather than querying sources on display. Protect version 5 creation details with a host callback on the history read; the ordinary instance-only detail call does not return them. The web viewer supplies the exact-task grant it uses for F018 completion results. Keep list queries lightweight.

Extend the existing task-contract transport in `io.github.leontecristian.workflow.web.dto.runtime` and mapping/controller code in `controller.runtime`. Preserve optional host API activation. Extend the shared runtime read model and task view with a read-only section, and use the sandbox frontend's exact token extraction for values. Keep completion payloads and result editing unchanged. Use a sequential review fixture in the guarded host test and prepared task states in the shared UI verification host; no APOLO code changes.

## P04 — Focused verification and stopping condition

Select existing definition, runtime command, service-binding, web and UI suites; add only cases needed for AC01–AC05. Test binding-only authoring, rejection of human inputContract in new authoring, legacy published empty-field loading, unchanged start inputContract, source-derived scalar/null/type handling, source distinction, exact JSON, snapshot bounds, legacy evidence and the new history authorization boundary. Exercise valid bindings in a minimal human-only flow as well as a sequential review.

Use the guarded host SQL integration harness on localhost:1433/apolo_workflow_test for persistence/reload, same-transaction source visibility, failed-entry rollback and successful replay with one snapshot. These fresh checks are needed because entry/evidence persistence changes. Serialize database runs and preserve unrelated fixtures. Do not rerun the full claim/concurrency matrix; focused claim/read coverage is needed only for changed creation-evidence decoding.

Use one prepared task fixture for browser verification of open/completed read-only inputs, exact values and unchanged result editing. Use automated web checks for permitted/denied task and history access. Run one applicable JDK 21 package/build and relevant shared UI checks; do not repeat builds included by packaging. Record actual commands/results and process cleanup in implementation.md.

Candidate REUSED evidence: [F019 revision 2](../F019-named-task-output-variables/implementation.md) for unchanged variable/submission resolution and exact copying; [F018 revision 3](../F018-structured-task-submissions/implementation.md) for unchanged result validation/editing. Confirm applicability against implementation changes before labeling evidence REUSED. Neither establishes human snapshots or receiving-task snapshot authorization.

Finish when R01–R04 and AC01–AC05 pass, with scoped diff review and reconciled records. Broaden checks only for changed dependencies or a concrete failure. Record any blocked check and its practical impact in the handover.
