# F030 — Workflow event batch writer and event catalog

Revision 1 · IMPLEMENTED · 2026-11-19. Owner: workflow maintainer. The owner approved the design and separately authorized implementation of T02–T04. See the [implementation record](implementation.md).

## Host contract

Rename the required functional interface in `io.github.leontecristian.workflow.core.service.runtime` from `WorkflowEventRecorder` to `WorkflowEventBatchWriter`. Its single abstract method becomes `void write(List<WorkflowEvent> events)`. The host supplies the writer. The engine supplies the ordered batch produced by one command. This is a source and binary breaking Java API rename: hosts update their imports, implementation method and wiring, then rebuild with the new core. No parallel legacy interface is proposed.

Writer lookup occurs when a command execution path requests the adapter. Exactly one registered writer must be available at that point. Zero or multiple writers cause a HOST_CONFIGURATION failure at lookup. On an authorized receipt replay, the engine returns the recorded command result without writing the original event batch again. A delivery retry happens after commit and uses the previously stored event and its stable event ID.

The type Javadoc should identify the writer as the synchronous handoff to host-owned durable storage and link to `WorkflowEvent`. The method Javadoc must tell implementers to persist every supplied event in order within the existing command transaction, before returning. A successful return means the writes participate in that transaction. Its owner still decides commit or rollback. Failure on any event must escape the writer and fail the command so partial writes and workflow changes roll back together. Independent commits, mutation reentry, in-memory acknowledgment and deferring persistence to an after-commit callback violate this contract. After commit the host owns delivery, retries and idempotent consumption using the stable event ID. Ordering here is within the batch; the writer contract supplies no global delivery order.

This documents the existing atomic handoff under CON-03/06/07 and ASM-04. Event generation, payloads, IDs, versions, error translation, stored evidence, database schema and host notification policy are outside the change.

## Catalog acceptance baseline

Provide one consumer-facing catalog linked from the Javadoc of `io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent` and from the core module README. Describe envelope fields separately from the string-to-string payload. In particular, `eventId` identifies an instance/history-sequence pair, `commandId` can span several events, and `actorRef` identifies the actor performing the command. Assignment targets and individual owners are in the payload. Explain when activity, task and transition references are present. Catalog the outgoing event schema version independently of the stored history details version.

The following is the verified inventory at commit `7d0b3b0`. Every payload value is a string, including counts and booleans. An empty payload is an empty map. Fields listed are present unless marked conditional.

| Event type | Version | When emitted | Payload keys |
| --- | --- | --- | --- |
| `INSTANCE_STARTED` | 1 | A new workflow instance starts | Empty |
| `START_INPUTS_ACCEPTED` | 1 | Start processing accepts inputs for an extended definition, including an empty input map | Empty |
| `INSTANCE_COMPLETED` | 1 | The instance reaches its end result | `resultKey` |
| `INSTANCE_CANCELLED` | 1 | Whole-instance cancellation closes the instance | `cancelledTaskCount` |
| `TRANSITION_TAKEN` | 1 | Execution follows an edge; the edge keys are on the envelope | `outcomeKey` only when leaving a human node; otherwise empty |
| `GATEWAY_ROUTED` | 1 | A gateway selects its outgoing transition | `nodeKey`, `selectedTransitionKey` |
| `SERVICE_TASK_COMPLETED` | 1 | A service action completes | `nodeKey`, `action` |
| `VARIABLES_WRITTEN` | 1 | A nonempty batch of workflow variable writes is applied | `nodeKey`, `writeCount` |
| `ACTIVITY_ENTERED` | 1 | Execution enters a human activity | Empty |
| `ACTIVITY_INPUTS_CAPTURED` | 1 | An ALL human activity captures a shared input snapshot | Empty |
| `ACTIVITY_COMPLETED` | 1 | A human activity resolves with an outcome | `outcomeKey` |
| `ACTIVITY_CANCELLED` | 1 | Instance cancellation closes the active human activity | `closureReason` |
| `TASK_CREATED` | 3 | Each task in the resolved roster is created with its initial assignment | `taskKey`, `assignmentKind`, `assignmentRef`; `assigneeRef` only when an individual owner exists |
| `TASK_CLAIMED` | 1 | A user claims a shared task | `assigneeRef` |
| `TASK_RELEASED` | 1 | The owner or an administrator releases a claim | `previousAssigneeRef`, `administrative` |
| `TASK_REASSIGNED` | 1 | An administrator changes the assignment target | `previousAssignmentKind`, `previousAssignmentRef`, `assignmentKind`, `assignmentRef` |
| `TASK_COMPLETED` | 1 | A task submission is accepted | `outcomeKey` |
| `TASK_CANCELLED` | 1 | Instance cancellation or early activity resolution closes an open task | `taskKey`, `closureReason` |

Include a small assignment example showing a direct USER target and a shared GROUP or ROLE target. Initial assignment is represented by `TASK_CREATED`; changing the target emits `TASK_REASSIGNED`, while claiming an existing shared task emits `TASK_CLAIMED`. Inputs, submission values, reassignment reasons and other richer history details must not be advertised as event payload fields.

### TASK_CREATED example for an APOLO booking review

Suppose APOLO starts a workflow to review bookings `B-742` and `B-743`. In the same transaction as workflow creation, APOLO associates both bookings with the engine-issued instance ID `120` in its own business data. After commit, its consumer receives a `TASK_CREATED` event. The following excerpt shows the relevant envelope and payload fields.

```json
{
  "eventSchemaVersion": 3,
  "eventType": "TASK_CREATED",
  "instanceId": 120,
  "taskId": 701,
  "actorRef": "alex",
  "payload": {
    "taskKey": "review",
    "assignmentKind": "USER",
    "assignmentRef": "maria",
    "assigneeRef": "maria"
  }
}
```

For a task initially assigned to the Finance group, the creation payload would be `{"taskKey":"review","assignmentKind":"GROUP","assignmentRef":"FINANCE"}`. The `assigneeRef` key is absent. A subsequent `TASK_CLAIMED` event identifies the individual who claimed it.

When Maria opens the task, APOLO takes two reads using the same instance ID:

1. **Related bookings.** Look up the APOLO-owned associations for instance `120`. This returns the two booking references in this example. Apply APOLO's booking access policy before displaying their details. The optional workflow `businessType` and `businessId` fields provide correlation only; APOLO's associations establish membership. See the [booking association contract](../../constitution.md#c09--host-policy-and-trusted-identity-govern-business-actions).
2. **Current workflow details.** Obtain Maria's actor reference from the authenticated request and call `WorkflowAuthorizedRuntimeReadService.instance(viewerActorRef, event.instanceId())`. On an authorized read, the returned `WorkflowInstanceView` supplies fields such as `status()`, `currentNodeKey()` and `definitionVersionId()`. These describe the instance at read time. The event's `actorRef` identifies Alex, who performed the originating command; neither that field nor the assignment authenticates the viewer.

For the review itself, APOLO uses `event.taskId()`, which is `701`. The following paths are relative to the host's runtime API mount. Both reads check the authenticated viewer's access to this task.

**When Maria opens the review**, APOLO calls `GET /tasks/701/contract`. Suppose the workflow author bound a JSON input named `bookingRefs` and configured two outcomes on the human node. This response excerpt shows the input snapshot captured at activity entry and the choices from the instance's pinned definition version:

```json
{
  "inputs": {
    "bookingRefs": { "type": "JSON", "value": ["B-742", "B-743"] }
  },
  "outcomes": [
    { "key": "APPROVE", "label": "Approve", "requiresComment": false },
    { "key": "REJECT", "label": "Reject", "requiresComment": true }
  ]
}
```

**After Maria approves**, APOLO calls `GET /tasks/701/submission` and reads `outcomeKey` from the accepted submission. An illustrative response excerpt is `{"actorRef":"maria","outcomeKey":"APPROVE"}`. Before a submission exists, an authorized read returns JSON `null`. The creation event supplies the task ID for both lookups.

### ALL review example for individual decisions and the final outcome

Suppose the booking review is an ALL activity (`activityId = 301`) with two reviewer tasks. Its configured contribution outcome is `APPROVE`. Each reviewer must approve for the activity to finish with `APPROVE`; a `REJECT` resolves it immediately. APOLO reads `event.payload().get("outcomeKey")` directly from both completion event types. The following rows show the relevant event fields in emission order across two commands.

| Command | Event type | activityId | taskId | outcomeKey | Meaning for APOLO |
| --- | --- | --- | --- | --- | --- |
| Maria approves | `TASK_COMPLETED` | 301 | 701 | `APPROVE` | Maria submitted her decision. Task 702 is still open, so this command emits no activity completion. |
| Alex rejects | `TASK_COMPLETED` | 301 | 702 | `REJECT` | Alex submitted his decision. |
| Same rejection command | `ACTIVITY_COMPLETED` | 301 | null | `REJECT` | The review has resolved. This is the final outcome used for routing. |

For each `TASK_COMPLETED`, APOLO can pair the payload value with `event.taskId()` and the submitting `event.actorRef()`. For `ACTIVITY_COMPLETED`, it pairs the value with `event.activityId()`. The final row describes one activity resolution; the two task rows retain the individual decisions.

## Acceptance scenarios

- **A01:** A host implements the new functional interface. Core passes the same ordered event batch through the renamed method, and write failures follow the existing command failure path. Lookup requires exactly one writer. An authorized receipt replay returns the prior result without a second write.
- **A02:** A host developer can reach the same catalog from the rendered event DTO Javadoc and the core README and identify each emitted event's trigger, version and payload, including optional fields.
  The booking review example traces the instance ID to related bookings and current workflow details, and the task ID to captured inputs, available outcomes and the outcome actually submitted.
  The ALL example shows how to read each reviewer's decision and the final activity outcome directly from their respective event payloads.
- **A03:** A migrating host can identify the old and new Java names and understand why existing event consumers, stored events and outbox rows require no data migration.
