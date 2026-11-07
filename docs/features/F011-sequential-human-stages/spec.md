# F011 — Multiple sequential human stages

**Status:** IMPLEMENTED, revision 1 — 2026-11-07. **Owner:** Workflow project owner. **Constitution:** [5.0.0](../../constitution.md). **Authorization:** the subsequent 2026-11-07 implementation request authorizes T02–T06 and verification on the guarded test database. The earlier documentation-only preparation is retained in implementation.md.

## Purpose and authority

Extend [F010](../F010-human-task-outcomes/spec.md) from one human stage to successive SINGLE stages in the same pinned instance. A completion may now pause at another human node instead of reaching END. Each stage has its own assignment, task type and outcome contract; only one stage is active at a time.

Authority: constitution C03/C06–C09; [CON-03–10/12](../../constraints.md), with CON-14 preserving existing JSON behavior; [ASM-02–06/08/11](../../assumptions.md); [design-source precedence](../../design-sources.md); [language design](../../architecture/workflow-definition-language-design.md) sections 8, 9, 12.1, 14.2 and 15. The language already describes successive stages and rollback when entry into a later stage fails. This bounded increment implements that direction without amending constitutional policy or selecting APOLO business rules.

## Scope and boundaries

**Selected:** one or more SINGLE human nodes in an acyclic graph; direct human-to-human transitions; automatic services/gateways between stages; optional stages selected by existing routing; early END after an outcome; per-stage pinned contracts; stage-specific reads, console display and command recovery. Existing one-stage definitions remain supported.

**Deferred:** ALL/multiple tasks per activity, loops/revisits/resubmission, parallel execution, cancellation/reassignment, nonempty guards, assignment input bindings, scalar task submissions, structured task results and new expression roots. Assignment inputs and human inputContract remain empty; resultContract remains FORBIDDEN. Existing scalar/JSON start and service bindings continue unchanged. F011 does not grant prior reviewers access to later tasks, freeze business data automatically or establish APOLO approver/no-self-approval policy.

The selected graph subset continues to require at least one human stage on every START-to-END path. It does not require every path to visit every human node, or one common human node. Automatic-only workflows remain outside this slice. No separate stage-count setting is introduced: existing definition node/edge/size limits apply. All cycles remain rejected, even though the broader language allows future human review loops.

## Requirements

### R01 — Validate all stages and paths

Retain schemaVersion 1, exactly one START, at least one HUMAN_TASK and at least one END. Every node must be reachable from START and able to reach an END. Reject cycles and paths reaching an END without visiting any human node, regardless of whether a condition appears constant. Preserve START/SERVICE_TASK/END and ordered EXCLUSIVE_GATEWAY connection rules.

Apply F010's 1–16 outcomes, exact-key routes and per-outcome comment rules independently to every human node. Outcome keys are local to a node: APPROVE may occur on several nodes, but a route must match its source node's declaration. Node keys and transition keys retain their respective definition-wide uniqueness. Shared destinations and mutually exclusive first/later human stages are allowed; graph merging does not create a join or concurrent work. Array order does not determine stage order.

Validate catalog references, resolver declarations and host support for every human contract, including conditional stages. Publication must not accept a bad later stage merely because a sample route skips it. Retain exact draft/schema/capability validation context; use validator identifier `wf-sequential-human-validator-v1` for this supported baseline. Revalidate drafts before publication after deployment; preserve published content and pinned execution.

### R02 — Enter only the reached stage

Start progresses from START to the first reached human node. Successful completion follows the submitted outcome from the current human node and progresses to the next reached human node or END. Stop immediately on human entry; never follow its outgoing route until its task is completed by a separate command. Resolve assignment only for a reached stage, at entry time, using its own resolver and the existing AssignmentContext with that node's key and pinned definition identity. Do not pre-create future tasks or invoke resolvers on unselected branches.

Every reached stage creates a new activity and exactly one directly assigned OPEN task atomically. Retain the existing SINGLE assignment result validation, reviewRevisionRef and task-type catalog reference on the activity. Different stages may use the same task type, resolver, task key or assignee; task keys are scoped to an activity and cannot identify a stage globally. Distinct stage records retain distinct IDs. No requirement for different people or inherited assignment is introduced. Disabling catalog selectability must not prevent entry into a published later stage or future starts of that published workflow.

### R03 — Complete against the exact active contract

Retain CompleteWorkflowTask and its existing fingerprint fields. After authorized receipt handling, validate that the requested task belongs to this instance's active activity, and that instance position, activity node key and pinned human contract agree. Use that node's task type, outcomes and comment requirements, not the first human node or another node sharing its task type. Preserve trusted actor, host authorization, assignment, state, expected-version and evidence checks.

A new command cannot complete an earlier completed task while a later stage is active. A later stage's assignee does not acquire permission to complete the earlier task, and an earlier assignee does not acquire permission over the later stage. Repeated outcome names across stages never select another stage's transition.

### R04 — Preserve one atomic progression

For an existing instance, retain the early checked instance write before participating effects. In one host transaction: accept the current submission; complete its task/activity; execute the selected automatic path; enter the next stage or complete at END; retain history, successful receipt and durable event handoff. When waiting, instance status stays ACTIVE with currentNodeKey set to the reached human node and no terminal result. There is exactly one active activity/OPEN task at a successful waiting boundary. END produces COMPLETED with its configured result and no active activity.

Assignment, service, expression, output, persistence, evidence-budget or recorder failure rolls back the entire triggering command, including task completion and any newly created stage. Earlier commands' committed stages/submissions remain intact. Later rejection does not rewrite or cancel earlier approvals. It is normal completion if routed to END, not an engine failure. No automatic retry or business-status side effect is introduced.

Retain 50 automatic visits per new command: START, SERVICE_TASK, EXCLUSIVE_GATEWAY and END each cost one; HUMAN_TASK entry and transitions cost zero. Allow the 50th visit and reject before executing the 51st. Human entry ends progression, so the next completion receives a fresh allowance. Preserve all existing F008/F009 expression, value and evidence bounds.

### R05 — Keep result and replay identities explicit

Retain WorkflowExecutionResult and existing receipt/wire shapes. For START_WORKFLOW, activity/task identify the first created stage. For COMPLETE_TASK, activity/task identify the completed stage and remain COMPLETED even when the command creates a successor. The instance snapshot describes the resulting waiting or terminal position; its actual final managed lock version and complete command history range include successor creation. Clients obtain successor IDs through existing authorized activity/task reads. Do not return the successor task as the task supposedly completed by this command.

Identical authorized replay returns the original committed snapshot, even after later stages progress or the instance finishes. It does not refresh that receipt to current state, invoke another resolver or duplicate a task/action/event. A fresh read supplies current state. Changed payload under the same command ID conflicts; competing commands on one active task produce at most one committed progression. Preserve existing authorization for receipt retrieval and exact task identity checks before returning it.

### R06 — Preserve stage-specific evidence and reads

Reuse existing history types/formats. Record the completed task/activity and selected human transition before downstream progression evidence and the next ACTIVITY_ENTERED/TASK_CREATED, or INSTANCE_COMPLETED. Stage activity activationSequence is the sequence of its own ACTIVITY_ENTERED, not a fixed ordinal or a reset per stage. History sequences increase across the instance and command history ranges cover all work in that command. Retain task/activity IDs, node keys, task-type reference, actor and actual selected outcome in their existing event locations. Emit nothing for unselected stages.

Authorized exact-task/activity and accepted-submission reads continue to resolve the requested historical stage after progression. Task contracts come from the task's activity node in the pinned definition, not the instance's current node. Existing immutable outcome/comment submissions remain available, including same-transaction visibility before downstream work; this does not add structured results or submission expression bindings. Reads remain bounded and detached. Preserve the distinction between historical command snapshots and live instance reads.

### R07 — Present progression correctly in the sandbox

Use existing endpoints and console pages. After completion, show the submitted task as completed and distinguish an instance waiting at another stage from a finished workflow. Refresh authorized instance/activity/task lists to discover current work; never rebind a retained completion command to the successor task or automatically submit it. New tasks obtain a new command ID and the current instance version. Only display/open a successor task where existing authorization allows it.

Render each task's pinned outcome actions/comments, including historical completed tasks. The graph shows completed stages, the current waiting stage and unreached branches from actual history, with exact activity/task links. Retain the explicit unavailable state for unsupported/inconsistent graph evidence. Refresh, route changes, pending states and recovery must not mix contracts or payloads between stages. No graphical authoring or new sandbox business-role model is included.

### R08 — Extend the baseline without changing storage ownership

Use the existing activity/task/instance schema and command/history codecs; no migration or new public DTO field is selected. Internally replace the singular human field with an immutable node-keyed human collection and exact lookup. Update callers together without a first-human fallback. Remove fixed first-stage sequence assumptions while preserving evidence integrity checks. Existing F010 definitions and outcomes, F007 actions, F008 routing and F009 values retain their behavior. Do not edit published documents, reset data or add old-data conversion tooling.

## Errors

Use existing structured diagnostics and runtime categories, with existing host HTTP mappings. Invalid graph/path/route: INVALID_GRAPH at the relevant node/transition pointer; duplicate keys: DUPLICATE_KEY; invalid document shape: existing structural diagnostics; known deferred features: UNSUPPORTED_FEATURE. Report a missing outcome route at that node's outcome pointer. Preserve capability/catalog diagnostics for each affected node.

At runtime, unknown outcome or missing required comment is INVALID_INPUT; a missing task is NOT_FOUND; a task outside the active activity is INVALID_STATE; wrong assignment is NOT_ASSIGNED. Existing STALE_VERSION/CONCURRENT_COMMAND and COMMAND_ID_CONFLICT semantics remain. Invalid resolver cardinality/assignee output is INVALID_ASSIGNMENT; missing/inconsistent pinned contract or unavailable required host configuration is HOST_CONFIGURATION. Preserve existing service/expression/limit failure categories, including their current resolver-failure translation. Never route to END, skip a stage or substitute an assignee after failure. Authorization may reject a request before these checks; no new error precedence or information disclosure is required.

## Acceptance scenarios

| ID | Scenario and required observation |
| --- | --- |
| AC01 | Publish two and three sequential SINGLE stages with no services, variables or gateway. Start creates only the first task; each completion creates only the next; final completion reaches END. Reordered node arrays preserve execution. |
| AC02 | Gateway selects an optional later stage or early END; alternative first stages and reconverging branches are supported. Only the selected path executes. Human-to-human edges pause without automatic completion. |
| AC03 | Reject cycles, unreachable/dead-end nodes, all-human bypass paths, missing/duplicate/mismatched outcome routes and unsupported policy/guards/results. Diagnostics identify the affected later node or edge. |
| AC04 | Stages share outcome names/task type but differ in outcomes/comment requirements; completion and historical contract reads use the exact stage. Missing later capabilities prevent publication. |
| AC05 | Resolver receives each reached node key once, returns a new stage task, and is never invoked for skipped stages. Same task key/assignee across activities is valid; separate assignees are respected. |
| AC06 | First approval pauses ACTIVE at the second stage; later rejection reaches its END while the earlier immutable approval remains. Receipt activity/task identify the submitted stage, with the final instance version matching storage. |
| AC07 | After participating host/JSON effects, next-stage assignment fails (exception, zero/multiple tasks or missing assignee), or event recording fails after entry. No completion, new stage, receipt, handoff or host/JSON change commits; earlier successful stages survive. Also cover start assignment failure. |
| AC08 | Replay first-stage completion after second-stage entry and again after final completion: return its original snapshot with no duplicated task/effect/assignment. Changed outcome/comment/task under the same ID conflicts. |
| AC09 | Concurrent distinct commands on one task commit one successor only; stale new commands and new commands for old tasks cannot advance current work. Wrong-assignee and unauthorized read/replay attempts fail without disclosure/effects. |
| AC10 | Contiguous cross-stage history identifies each activity/task and selected edge, includes same-transaction exact submission visibility, and retains final managed version and durable handoff. Verify rollback on downstream service/output/routing failures. |
| AC11 | At the automatic-step boundary, 50 visits succeed and 51 fail before the extra effect; a new human completion resets the allowance. A three-stage failure preserves both earlier command boundaries correctly. |
| AC12 | Console walkthrough: complete successive tasks, inspect historical contracts, waiting/final state, graph and history; refresh/recover the original command after moving stages. A second user's task is not exposed or automatically opened. |
| AC13 | F010 single-stage fixtures and scalar/JSON/gateway regressions remain valid. Later stage entry and future starts work after catalog selectability is disabled; published versions remain immutable and pinned across a new publication. |

## Demonstration and remaining decisions

The direct fixture uses the existing SANDBOX_REVIEW and sandbox.singleReviewer with empty assignment inputs: BEGIN → MANAGER; MANAGER.APPROVE → PARTNER; either REJECT → REJECTED; PARTNER.APPROVE → APPROVED. Both nodes use SINGLE, empty inputContract, FORBIDDEN resultContract and APPROVE/REJECT with a required rejection comment. Reusing the sandbox reviewer intentionally demonstrates distinct stages rather than real manager/partner roles. Derived gateway/service and three-stage fixtures support AC02/07/11. Names and business labels are illustrative, not reserved core semantics.

No material generic contract decision remains open for this slice. APOLO approver sources, authorization, shared immutable review basis, production transaction/delivery integration and resubmission remain separate decisions/verification gates. The broader language's loops and automatic-only paths are not enabled here. Implementation and the actual coverage/gaps for AC01–13 are recorded in [implementation.md](implementation.md); IMPLEMENTED does not close interactive browser or APOLO acceptance.

Runnable fixtures: [direct two-stage review](sequential-review.json), [optional partner with a service/gateway](optional-stage-review.json), and [three-stage review](three-stage-review.json). Use sample capabilities and selectable SANDBOX_REVIEW for publication. The direct/three-stage fixtures start with `{}`; the optional fixture starts with inputs `{"quantity":"20"}` to reach PARTNER or `{"quantity":"0"}` to skip it. The roles reuse the same sandbox reviewer and do not select real business policy.
