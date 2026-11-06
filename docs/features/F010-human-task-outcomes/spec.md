# F010 — Human-task outcomes and outcome-based routing

**Status:** READY, revision 1 — 2026-11-06. **Owner:** Workflow project owner. **Constitution:** [5.0.0](../../constitution.md). **Authorization:** specification preparation only; implementation requires a subsequent user request.

## Purpose and authority

Allow a reviewer to choose a declared outcome, such as APPROVE or REJECT, and execute its explicit route. F009 supplies JSON data and expression routing, but current definitions/execution still assume one human node with one outcome. This feature removes the single-outcome restriction while retaining one human stage and one directly assigned task.

Authority: constitution C06–C09; CON-03/05–10/12; ASM-02–06/08; [design-source precedence](../../design-sources.md); [language design](../../architecture/workflow-definition-language-design.md) sections 9, 12.1 and 14 (including the agreed 16-outcome limit). Build on F005 execution, F007 service actions, F008 routing and [F009](../F009-json-execution-variables/spec.md) values/evidence. This implements an existing language capability in a bounded slice; no new constitutional policy is selected.

## Scope

**Selected:** 1–16 declared outcomes, per-outcome comment requirements, exactly one transition per outcome, chosen-outcome execution/history, and outcome actions in the test-host console. Include branches with service actions, optional exclusive gateways, multiple END nodes and shared downstream nodes. A single-outcome workflow remains valid.

**Deferred:** multiple human stages, ALL/multiple assignees, loops/resubmission, cancellation/reassignment, nonempty guards, simple task input submissions and structured task results. Keep inputContract empty and resultContract FORBIDDEN. No outcome automatically changes business status, writes a variable, refreshes JSON, grants a permission or means cancellation. APOLO supplies business effects through explicit services. APPROVE/REJECT are example keys, not reserved engine behaviors.

## Requirements and concrete contracts

- **R01 — Pinned outcome declarations.** Keep schemaVersion 1 and the existing outcomes array. Each outcome requires key, label and requiresComment; omitted guards or an empty array are accepted, nonempty guards remain unsupported. Keys are unique within the human node, case-sensitive and follow the existing 100-character technical-key grammar. Labels retain the existing 1–200-character limit. Accept 1–16 entries inclusive; reject empty/oversized arrays and duplicate keys. Preserve array order for presentation only. Comments retain the current command length/Unicode limits and fingerprint semantics; requiresComment means non-null and nonblank under the existing Java String.isBlank check. Never silently trim or rewrite accepted comment text.
- **R02 — Complete and unambiguous routes.** Every declared outcome has exactly one outgoing transition from the human node, identified by transition.outcome. Reject missing/duplicate outcome routes, undeclared outcome keys and human transitions without outcome. Human transitions cannot carry condition or default; put any subsequent conditional selection in an EXCLUSIVE_GATEWAY. START/SERVICE_TASK still have exactly one outgoing edge without outcome/condition/default; END has none. Gateway rules remain F008. Multiple outcomes may target the same node. Reordering transitions does not change which route an outcome selects.
- **R03 — Graph boundaries.** Retain exactly one START and one HUMAN_TASK with SINGLE completion, at least one END, and an acyclic reachable graph. Every complete START-to-END path must pass the human node; every node must be reachable from START and able to reach an END. Allow multiple END nodes and outcome branching with zero gateways, zero variables and zero service actions. Reject a second human node, bypass routes, dead ends and cycles. Retain current node/edge, automatic-step, expression, history and value limits.
- **R04 — Completion selects the submitted outcome.** Retain CompleteWorkflowTask(commandId, instanceId, taskId, expectedLockVersion, outcomeKey, comment) and existing host completion/replay endpoints. Resolve outcomeKey against the pinned human contract, check its comment requirement, and select its exact transition. No implicit first outcome, default outcome or fallthrough to another outcome. Unknown/missing/invalid outcome and missing required comment are INVALID_INPUT (host HTTP 400). A malformed pinned model/route is HOST_CONFIGURATION (500), not an alternative route. Existing actor/assignment, host authorization and state checks remain mandatory.
- **R05 — Atomic execution and recovery.** Preserve authorized receipt lookup before state-dependent validation, the early instance claim and host transaction. Complete the task/activity with the selected outcome, run only its downstream path and complete at the reached END. Service/gateway/budget/evidence failures roll back the current completion and all participating effects. A rejected business outcome is a normal successful workflow completion when its route reaches END; instance status is COMPLETED and resultKey comes from that END. It is neither an execution failure nor CANCELLED.
- **R06 — Identity and evidence.** Existing completion fingerprints already include outcomeKey and comment: retain full identity and F009 codecs. Identical authorized retry returns the committed result without repeating effects. Reusing a command ID with another outcome/comment conflicts. Concurrent distinct commands selecting different outcomes cannot both commit. TASK_COMPLETED, ACTIVITY_COMPLETED and the human-origin TRANSITION_TAKEN must contain the actual selected outcome; transition metadata identifies the selected edge. Never write evidence or invoke actions for unselected branches. Existing result/receipt/history shapes suffice; no version bump solely for supporting additional outcome keys. Preserve exact task-submission reads and pinned definition loading.
- **R07 — Console and reads.** The existing authorized task-contract endpoint returns all pinned outcomes, in declaration order, including label and requiresComment. Render a labeled action button per outcome using that contract. A shared comment field indicates the selected action's requirement and blocks a required blank comment; server validation remains authoritative. Selecting an action must bind that explicit key to one command, capture its comment/version and freeze the command payload for retry/recovery. Disable all action buttons while the command is pending and on non-OPEN tasks. Do not default to the first outcome or infer outcome keys from labels. Preserve auth/CSRF controls, safe text rendering and current recovery behavior. Read-only graphs must display all outcome edges and highlight only actual transitions; labels or array order must not imply execution.
- **R08 — One focused extension.** Retain single-outcome behavior and current scalar/JSON, service and expression semantics. Internal Human representation becomes an ordered immutable List<Outcome> with exact key lookup, replacing its singular outcome field; update all internal callers together. Public command/result DTOs need no changes. Do not add compatibility shims or old-data conversion tooling for disposable test data. No schema migration, new persistence field, module, service SPI or external integration is required. Do not alter existing published content or delete/reset test data during this work.

## Publication and implementation boundary

Structural validation must accept 1–16 outcomes in every supported schema path. Graph/contract validation uses existing location-aware diagnostics: INVALID_GRAPH for missing/duplicate/mismatched outcome routes, DUPLICATE_KEY for repeated outcome keys, and existing structural diagnostics for shape/count violations. Retain UNSUPPORTED_FEATURE for known deferred features. Report the relevant outcome/transition JSON pointer, including an outcome pointer for a missing route. No new error response format.

The loader must detect outcome branches even without an EXCLUSIVE_GATEWAY or execution variables; do not leave such definitions on the old linear-only parser. Use one shared graph-validation/model-building path for supported node types, removing divergent single-outcome assumptions where necessary. Preserve schema digest participation in validation context, so validation performed before schema changes cannot authorize publication afterward. Use validator identifier `wf-human-outcome-validator-v1` for the expanded supported baseline; retain F009 canonicalization/fingerprint/history formats. Current one-outcome documents remain valid by behavior, without preserving old validation-cache identities. Revalidate drafts against the deployed contract before publishing.

No implicit outcome variable or new SpEL root is introduced. Services needing different behavior use different nodes/bindings on the chosen route. Existing payload and comment limits remain unchanged. This specification grants no APOLO business authorization: nonempty guard support remains a separate feature.

## Complete minimal example

This future executable example requires the existing sample SANDBOX_REVIEW catalog entry and sandbox.singleReviewer resolver. It deliberately contains no gateway or variable, proving outcome routing does not depend on them. It is not accepted by the current single-outcome implementation.

```json
{
  "schemaVersion": 1, "key": "sandbox.outcomeReview", "version": 1,
  "name": "Approve or reject", "startPolicy": {"inputContract": []}, "variables": [],
  "nodes": [
    {"key":"BEGIN","type":"START","name":"Start"},
    {"key":"REVIEW","type":"HUMAN_TASK","name":"Review","taskType":"SANDBOX_REVIEW",
     "assignment":{"resolver":"sandbox.singleReviewer","inputs":{}},
     "completion":{"policy":"SINGLE"},"inputContract":[],"resultContract":{"mode":"FORBIDDEN"},
     "outcomes":[
       {"key":"APPROVE","label":"Approve","requiresComment":false},
       {"key":"REJECT","label":"Reject","requiresComment":true}]},
    {"key":"APPROVED","type":"END","name":"Approved","result":"APPROVED"},
    {"key":"REJECTED","type":"END","name":"Rejected","result":"REJECTED"}
  ],
  "transitions": [
    {"key":"begin","from":"BEGIN","to":"REVIEW"},
    {"key":"approve","from":"REVIEW","to":"APPROVED","outcome":"APPROVE"},
    {"key":"reject","from":"REVIEW","to":"REJECTED","outcome":"REJECT"}
  ]
}
```

Two independent instances demonstrate APPROVE without a comment and REJECT with a reason. Both finish with COMPLETED status and their different resultKey values. A third instance submitting REJECT without a reason remains OPEN/ACTIVE with no committed completion evidence. For transactional verification, derive a fixture with a participating host service on each outcome branch and an intentionally failing downstream action/gateway. Do not add real booking effects to core.

## Acceptance scenarios

| ID | Required observation | Requirements |
| --- | --- | --- |
| AC01 | One and 16 unique outcomes validate; zero, 17, duplicate keys and invalid descriptor fields fail with locations. | R01 |
| AC02 | Missing/duplicate/unknown outcome routes, human condition/default routes and nonhuman outcome routes fail publication; shared destinations are valid. | R02 |
| AC03 | Minimal example validates without gateways/variables/services; one human with multiple ENDs works. Second human, bypass, cycle, unreachable node and dead end fail. | R03 |
| AC04 | APPROVE and REJECT on separate instances take their exact edges/results; reordering outcomes/transitions changes presentation only. | R01/02/04 |
| AC05 | Required missing/empty/blank comments and unknown/case-mismatched outcomes fail; optional comments are accepted and retained exactly. No completion or effect commits on failure. | R01/04/05 |
| AC06 | Only the selected branch runs its service/gateway/variable writes. Shared downstream nodes execute once; F009 exact JSON and F008 conditional-route semantics remain intact. | R02/05/08 |
| AC07 | Failure after a selected-branch host effect/JSON replacement rolls back task/activity/instance, host effect, variables, history, receipt and handoff. | R05 |
| AC08 | Identical replay repeats no effects; changed outcome/comment conflicts under the same command ID. A successful replay still works after task completion and returns the original outcome/result. | R06 |
| AC09 | Competing approve/reject commands against the same version yield one committed outcome, with the actual final managed version. The losing command records no effects. | R05/06 |
| AC10 | Task/activity completion and transition history agree on the selected outcome; task-submission reads, pin loading and receipt recovery work. No unselected-branch evidence appears. | R06 |
| AC11 | Console shows ordered pinned labels/comment requirements, submits the explicit action, prevents duplicate pending submissions and retains frozen recovery payloads; graphs distinguish taken and untaken routes. | R07 |
| AC12 | Single-outcome/scalar/JSON workflows remain functional; unauthorized/wrong-assignee completion still fails. Existing definitions remain immutable and task-catalog selectability does not invalidate pinned execution. Stale validation contexts cannot publish. | R08/04 |

## Readiness

No material product decision remains open for this bounded slice. READY means specified, not implemented or verified. All AC01–AC12 are NOT RUN. Follow [plan](plan.md), [tasks](tasks.md) and [implementation record](implementation.md). Multiple stages and operational commands remain separate roadmap candidates.
