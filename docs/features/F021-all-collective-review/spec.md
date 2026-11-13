# Specification — F021 ALL collective review

**Revision:** 2 — 2026-11-13. **Status:** IMPLEMENTED; implementation authorized by the owner on 2026-11-13. **Owner:** project owner. **Constitution:** 6.5.0.

## Purpose, authority and scope

Let one pinned human node create one runtime activity with 1–100 required tasks, collect independent answers and resolve through the agreed ALL completion policy. Assignment remains a task concern. All tasks receive the same immutable node-defined inputs and share one node result contract. This is the next selected feature; APOLO integration waits for owner access.

Owner decisions from the design discussion: support USER/GROUP/ROLE with existing claim/release behavior; count tasks rather than group members; retain answers only in TASK_COMPLETED and only resolution outcome/closure reason in ACTIVITY_COMPLETED; expose resolved original submissions through bindings; optionally create a mutable named collection only at resolution; retain 64 KiB task results and allow an 8 MiB collection with consistent related limits and no budget reservations. Same-node inputs and result contracts are shared; answers are independent.

Apply constitution C02/C03/C05–C10; CON-03–10/12–14 in [constraints](../../constraints.md); ASM-02/03/10/11 in [assumptions](../../assumptions.md). [Language design sections 9.2–9.4 and 12.1.11](../../architecture/workflow-definition-language-design.md) govern fixed rosters, contribution outcomes and early resolution. F021 extends [F016](../F016-shared-group-role-task-assignment/spec.md), [F017](../F017-task-claim-release/spec.md), [F018](../F018-structured-task-submissions/spec.md), [F019](../F019-named-task-output-variables/spec.md) and [F020](../F020-human-task-input-bindings/spec.md) only as stated here. See [source precedence](../../design-sources.md).

Includes core behavior, shared web/UI adaptation, sandbox capabilities and focused acceptance. Excludes APOLO changes, separate workflow branches/joins, loops, mutable rosters, optional participants, waivers, ANY/QUORUM, new cancellation/reassignment commands, new resolver input-binding language, generated forms and designer work. Cancellation of open siblings on activity resolution is included; general cancellation remains separate.

## R01 — Pinned completion and common task contracts

A HUMAN_TASK may declare `completion: {"policy":"ALL","contributionOutcome":"APPROVE"}`. The contribution outcome must identify one of the node's declared outcomes. Require both fields for ALL; reject null, unknown fields, unknown outcomes and unsupported policy values. SINGLE retains its existing contract and rejects contributionOutcome. Every permitted outcome has its existing exact route. The outcome names are configurable, not hardcoded approval semantics.

All tasks use the pinned task type, outcomes, comment requirements, claim policy and resultContract on that human node. Each submission independently passes current task eligibility/ownership, core structural validation and the existing host business validator. OPTIONAL means a task may omit its result; REQUIRED requires an object; FORBIDDEN accepts no result. An explicit result null/array/scalar remains invalid. Result schema and 64 KiB/depth-16 bounds remain task-level, not an aggregate schema.

Named outputs and `source: submission` selectors retain the existing OPTIONAL/REQUIRED source-contract restriction. ALL may execute with FORBIDDEN results, but this slice does not introduce outcome-only output bindings for such nodes. Ordinary task outcome/submission reads still work. No per-task result schema, implicit field merge or business aggregation is introduced.

## R02 — Atomic fixed roster and task-owned assignment

Resolve the roster once at activity entry using a registered host capability and current AssignmentContext. Validate bounded cardinality before copying/traversing provider collections: 1–100 non-null tasks, unique case-sensitive task keys under existing key rules, valid supported targets and existing reviewRevisionRef rules. Invalid cardinality/keys/targets fail INVALID_ASSIGNMENT and roll back entry. Registry/configuration failures retain existing sanitized host-configuration handling.

Each ResolvedTask retains its USER/GROUP/ROLE target. Repeated targets and one actor completing multiple distinct tasks are allowed unless host policy forbids them. GROUP/ROLE membership is checked currently; do not expand members into tasks. Each task retains F017 ownership/claim rules; direct USER tasks initialize their assignee, shared tasks initialize unclaimed. Membership changes never change the roster or count as contributions.

Persist one activity with completionPolicy ALL, contributionOutcomeKey and requiredTaskCount equal to the validated roster. Create all tasks in that host transaction, in ascending task-key order for deterministic creation/history. No partial roster is committed. Resolve the complete node `inputs` map once from current variables/original submissions, including same-command writes, and give all tasks exactly that immutable typed snapshot. Input failure aborts the entire entry. Opening, claiming, releasing or later variable changes never refresh it. Use shared retained evidence rather than repeating a large snapshot in every sibling history record (C03).

Existing SINGLE providers remain supported. The implementation plan introduces a bounded ALL provider contract without changing their one-task result semantics. Both provider types use existing host context; new nonempty assignment inputs are deferred. Host integration supplies business rules and distinct-person requirements.

## R03 — Contribution, early resolution and atomic progression

For each accepted completion, complete only the submitted task and persist its TASK_COMPLETED fact once, with its original outcome, comment and optional exact result. While another required task remains open and the accepted outcome is contributionOutcome, keep the activity ACTIVE and process position unchanged. Do not emit ACTIVITY_COMPLETED, create the named output, invoke downstream services or follow any route. Such a contribution consumes zero automatic traversal steps.

Resolve the activity when either (a) all required tasks have completed with contributionOutcome, producing that outcome, or (b) the submitted outcome is another permitted outcome, producing it immediately. Under (b), cancel every still-OPEN sibling with closureReason ACTIVITY_RESOLVED, closure actor/time from the resolving command, and no task outcome or fabricated submission. Previously completed tasks retain their facts. Reject later completion/claim/release on cancelled tasks through existing state handling; an authorized successful command replay still returns its original receipt.

The first decisive command that successfully commits wins. There is no implicit rejection priority. Preserve early checked instance writes for every existing-instance mutation, including nonfinal contributions and ownership commands; capture the actual final managed version. A stale competing completion fails concurrency checking before host effects. No automatic business-action retry.

Order new history within a resolving command: submitted TASK_COMPLETED, any TASK_CANCELLED siblings in task-key order, ACTIVITY_COMPLETED, optional VARIABLES_WRITTEN, then normal routing/progression. ACTIVITY_COMPLETED details remain exactly `{"closureReason":"TASK_OUTCOME","outcomeKey":"<resolved outcome>"}`. No result array, reference list, partial-result cache or aggregate result column is added there. Cancellation facts contain the cancelled task key and closure reason and link through existing activityId/taskId columns; durable delivery carries metadata only.

Resolve/record the final submission, cancellations, output, routing, next activity entry, host effects, receipt and durable handoff in the same host transaction. Any failure rolls back this command, including its final submission and sibling cancellations. Earlier committed contributions remain. A permitted early outcome counts as resolution even when some task results are omitted.

## R04 — Original collection and explicit downstream bindings

Reuse `{"source":"submission","nodeKey":"review"}` in service and human inputs. A SINGLE source returns its existing result object. A resolved ALL source returns a non-null JSON array assembled from the exact activity's accepted completion evidence. Resolve within the current instance/pinned definition; require exactly one matching completed occurrence. Never select the latest, use the triggering activity as a substitute for the selector, or read a mutable output variable.

The collection contains completed tasks only, including the decisive early submission. Sort by taskKey using the existing case-sensitive key ordering. Each entry is exactly:

| Field | Representation |
| --- | --- |
| taskId | Exact positive decimal string, matching existing HTTP identifier conventions. |
| taskKey | Required task key, unique within the activity. |
| outcomeKey | Accepted task outcome. |
| comment | Existing accepted comment string or JSON null. |
| result | Exact accepted result object; omit this property if no result was supplied. Never substitute JSON null. |

Reuse TaskSubmission field meanings and the strict original-evidence decoder. The collection is a compact projection, not wholesale serialization of persistence entities or the audit DTO. Do not copy command/actor/time metadata or input snapshots into it; exact task/history APIs retain those facts. Cancelled/open tasks are not submissions and are excluded; roster/status reads describe them. ALL resolution necessarily includes at least one completed task, so a successful collection is not empty. OPTIONAL omissions produce entries without result and do not make the entire collection unavailable. A collection whose entries all omit result is still present. SINGLE omission semantics remain unchanged.

Example derived collection:

```json
[
  {"taskId":"41","taskKey":"finance","outcomeKey":"APPROVE","comment":null,"result":{"approvedAmount":1000.00}},
  {"taskId":"42","taskKey":"legal","outcomeKey":"REJECT","comment":"Agreement missing"}
]
```

A service destination must accept JSON; human input type is derived as non-null JSON. Validate source/target selectors using existing F019/F020 rules with this ALL extension. The final accepted submission must be query-visible before downstream resolution within the same transaction. Skipped/not-yet-resolved sources fail SUBMISSION_UNAVAILABLE before consumer invocation. Ambiguous occurrences, missing/duplicate completion facts, inconsistent task outcomes/identities or corrupt required evidence fail existing HOST_CONFIGURATION integrity handling. Do not silently filter corrupt or unauthorized records into a success-shaped internal collection.

These are internal explicit bindings under the authorized command/capability. They do not grant external sibling-submission access. Preserve per-task filtered external submission paging, including access to accepted partial contributions while an activity is active. Receiving-task authorization governs the explicit immutable input copy; instance-variable visibility governs an explicit output copy. Existing service-input evidence visibility remains unchanged. No new collection endpoint or per-binding ACL framework is required.

## R05 — Optional named output only at resolution

An ALL node may use the existing `outputVariable` under R01. Keep name grammar, collision rejection across all producers/declarations, combined 100-variable limit, and create-only ownership. No separate top-level declaration or duplicate schema is required. Before resolution there is no output row, list entry or partial array, and hasVariable is false.

At resolution create the exact R04 array once, before downstream traversal. Create it even if every accepted OPTIONAL result was omitted. If a row already exists, fail CONFLICT rather than overwrite. A successful receipt replay returns before creation/collection lookup. Preserve the full exact array in ordinary VARIABLES_WRITTEN evidence with activity/node provenance; use activity-level collection provenance rather than labelling the whole array as the final task's result. C03 specifies the retained source tag.

After creation, existing explicit service bindings may replace the whole non-null JSON value under the enlarged ordinary value limits. The replacement need not retain the collection shape, as with F019 working copies. Original bindings always reconstruct the immutable original collection. No live append/update on nonfinal contributions, merge, lazy variable reconstruction or new external write endpoint.

## R06 — Command results, reads and shared UI

Keep completion results scoped to the submitted task and its owning activity. A nonfinal completion returns a COMPLETED task, ACTIVE activity and ACTIVE instance at the same node; final/early results return the completed activity and post-progression instance. Receipts preserve those original snapshots after siblings subsequently complete or the instance advances. Ownership results continue to identify their exact task. No full roster or submissions in receipts.

For START entering ALL, retain the singular execution-result shape: activity describes the entire roster and task is the first task in task-key order, a representative creation result only. It is not the caller's assigned task or the full roster. Consumers discover authorized work through paginated task reads. SINGLE start behavior remains unchanged. UI and recovery code must follow all relevant pages, not silently stop after the current 50-row page; do not reopen a completed/cancelled task because a stale receipt references it.

Expose ALL policy, contribution outcome and existing requiredTaskCount through activity views. Existing authorized task/submission lists show per-task status/outcome; do not add peer visibility just to display progress. The shared task view reports whether its completion left the review waiting or resolved it, refreshes authorized current work, and displays read-only common inputs and independent result editing. The instance viewer shows one activity/node with multiple tasks, not parallel graph branches. Metadata-only summaries remain small; exact values retain numeric scale in reads/downloads.

## C01 — Capacity contract

The owner-selected 8 MiB collection limit is measured as compact canonical UTF-16 bytes, consistently with the existing 64 KiB result limit. UTF-8 allowances below are separate engineering transport bounds. Limits are inclusive and include complete wrapper/metadata bytes, with 1 MiB = 1,048,576 bytes. No configurable per-task reservation or aggregate quota allocation is introduced.

| Boundary | UTF-16 bytes | UTF-8 bytes |
| --- | --- | --- |
| Individual accepted task result | 64 KiB, unchanged | Existing result/request validation; no new lower limit |
| Complete R04 ALL collection | 8 MiB | 12 MiB |
| One ordinary JSON execution value (variable/start/service/human input) | 8 MiB | 12 MiB |
| Complete initialized variable map | 10 MiB | 15 MiB |
| Complete normalized start map; service argument map; service output map; typed human input snapshot | 10 MiB each | 15 MiB each |
| One complete history details record | 24 MiB | 36 MiB |
| Cumulative history details per command | 64 MiB | 96 MiB |
| Complete history detail response | — | 40 MiB |
| Variable pages and exact task-contract response containing input snapshots | — | 16 MiB |
| Standalone raw JSON-value parsing; start/start-replay HTTP ingress | — | 16 MiB |

Retain 256 KiB UTF-8 completion/replay ingress, 128 KiB dual-encoding receipts/durable handoff, 1 MiB UTF-8 ordinary metadata/submission pages (including history-summary pages), definition/schema limits, 100 variables, 50 human input names, 1,000 array elements, 100 properties per object, string/key/number limits, 6,000 events, 50 automatic steps and expression work/count bounds. No blanket increase to all HTTP endpoints or global host configuration. A larger literal still has to fit the unchanged complete definition budget.

Execution JSON permits depth 18 so an array plus submission-entry object can wrap a valid depth-16 result. Individual task results retain an explicitly checked depth 16 even when transported in the enlarged generic carrier. Wrapper depths for maps/history/protocols are separate from value depth and require correctly bounded parsers. Repeated nesting of previous collections is not unbounded: the current value depth still applies. Preserve exact decimals and scalar/null semantics, with no expression operator or arithmetic changes.

Revision 2 reduces the surrounding engineering ceilings after tracing actual serializers; the owner-selected 8 MiB collection and 64 KiB result are unchanged. The sizing derivation is:

| Path | Copies actually retained/serialized | Derived headroom (UTF-16 unless stated) |
| --- | --- | --- |
| Current state or one input/output map | One set of values, names and wrappers | 8 MiB collection + 2 MiB for other values/metadata = 10 MiB aggregate. This is a shared cap, not reserved space. |
| VARIABLES_WRITTEN | Previous values + new values + per-write source/metadata; not the entire history again | Each state map is at most 10 MiB, so changed old/new values total at most 20 MiB. Start-literal provenance is also bounded by the unchanged 2 MiB definition and has no old state. A 24 MiB record cap leaves room for at most 100 write descriptors without changing their schema. |
| SERVICE_TASK_COMPLETED | One input map + one output map + node/action metadata | At most 20 MiB of maps; fits the same 24 MiB record cap. The subsequent variable-write event is a separate record. |
| ACTIVITY_INPUTS_CAPTURED / SINGLE task snapshot | One typed snapshot plus event metadata | At most 10 MiB snapshot. ALL siblings store compact references, not 100 copies. |
| Resolving command with output, one service replacement and next human entry | Output creation (~8 MiB), service maps (up to 20 MiB), old/new variable values (up to 20 MiB), next shared snapshot (up to 10 MiB), final submission and small event metadata | About 58 MiB of large data plus metadata. The 64 MiB command cap provides headroom for this selected path, not 50 maximum-sized services. No earlier task completion records are added to this command again. |
| History detail transport | One canonical details string escaped once inside its response envelope | At most 36 MiB UTF-8 for 24 MiB canonical UTF-16 text under the current encoding, plus summary/envelope; 40 MiB response cap. Do not multiply the UTF-8 cap by another blanket escaping factor. |
| Human task contract / variable page | Snapshot or variable values, not full history; plus schema/transport metadata | At most 15 MiB UTF-8 for a 10 MiB typed snapshot, plus bounded task/result schema metadata; 16 MiB response cap. Variable pages remain byte-paginated. |

Encoding basis: the existing canonicalizer leaves valid non-ASCII characters literal, escapes quotes/backslashes, and emits control characters as ASCII escape sequences. Valid UTF-8 is at most 1.5 times UTF-16 bytes. When canonical JSON is wrapped as detailsJson, its text has no raw controls: ASCII quotes/backslashes expand to two UTF-8 bytes per UTF-16 code unit, other BMP characters use at most three, and surrogate pairs use four. Thus the escaped contents still fit 1.5 times the original canonical UTF-16 bytes, plus enclosing quotes/envelope. Keep this non-ASCII-literal serializer policy explicit in private runtime/web codecs and verify it; enabling universal Unicode escaping would invalidate this calculation. This is a bound on serialized bytes, not on Java heap usage.

Raw ingress is measured before normalization: 16 MiB allows a compact maximal start map (up to 15 MiB UTF-8) and a bounded request envelope. Whitespace and alternate escape spellings consume the raw request budget; a semantically valid value with excessive raw formatting may still fail ingress. No arbitrary formatting allowance is promised.

Retain the separate 4 MiB dual-encoding gateway evaluation-evidence allowance and current expression work/count limits; accepting a large variable does not promise that a gateway may retain multiple full copies of it. No increase to gateway evidence is needed for collecting or binding answers; expression-bound failures remain explicit. All command/record/envelope checks still apply. Multiple large downstream services or maps may exhaust the 64 MiB command cap and roll back; capacity is not reserved for a particular step.

Verify actual envelope handling and memory behavior during implementation before claiming runtime capacity. Use bounded serialization/streaming and long counters; do not allocate unchecked strings then truncate. This trace selects smaller engineering ceilings without changing the accepted-answer storage, adding offloading, or introducing quota/reservation machinery.

## C02 — Capacity failures

Never reserve capacity for reviewers or reject a valid nonfinal contribution because a hypothetical future collection might be large. Each individual result must satisfy its existing limits when accepted. Assemble/check the collection when an output or collection binding is actually needed; a resolved activity with no such consumer is not required to fit into one JSON collection. Paginated original reads remain available.

If collection assembly, output/current-variable state, receiving input, history, response-returnability or any other applicable command bound fails, raise LIMIT_EXCEEDED and roll back the triggering command. A final submission failing during immediate output/binding remains open; earlier accepted answers persist. If the oversized source is consumed by a later command, roll back that later command without reopening an already committed source activity. Error diagnostics identify the failed boundary without leaking submitted values. A larger input limit does not authorize skipped/truncated items or partial history.

Do not claim that 100 individually valid answers always fit 8 MiB: entry metadata and encoding matter. No new retry, edit-accepted-answer or recovery command is part of this feature. Oversized immutable collections may require a different separately specified workflow path; this limitation must be documented rather than hidden by automatic fallback.

## C03 — History, migration and compatibility

Retain TASK_COMPLETED details versions 2/3 and one authoritative original result per completed task. ACTIVITY_COMPLETED retains its current details contract. Use existing activity/task state columns for ALL and ACTIVITY_RESOLVED cancellations; add no result table or aggregate column.

For new ALL activities with configured inputs, write one immutable `ACTIVITY_INPUTS_CAPTURED` fact (details schema 1) linked to activityId, with details `{"inputs":{...}}` using F020 typed values. Each sibling TASK_CREATED uses details schema 6: taskKey, assignment, assigneeRef and inputSnapshotSequence (an exact decimal-string history reference). Require exactly one shared snapshot fact for each input-bearing ALL activity and require every sibling to reference that same fact. Validate reference instance/activity/type/version and the shared entry command identity on retained reads; activationSequence still identifies ACTIVITY_ENTERED, with the snapshot sequence after entry and before every referencing TASK_CREATED fact. Missing, duplicate, cross-command or inconsistent sibling references are integrity failures. Do not resolve from mutable source variables. No-input ALL tasks use existing assignment-only details. Legacy SINGLE/F020 embedded snapshots remain readable; new SINGLE entry may retain its embedded snapshot format. Remove the assumption that every task creation is activationSequence + 1: locate exactly one creation fact by taskId and validate its owning activity.

The shared snapshot is exposed only through authorized exact receiving-task reads. Generic instance-history details must not expose the shared snapshot fact merely because activityId is visible; deny its full details without a receiving-task authorization context. Metadata summaries and compact references remain available under existing policy. This adds no activity-wide permission shortcut. Required input evidence has operational retention identical to task completion evidence. Snapshot sharing is an implementation detail; every task still observes the same immutable values.

ALL named-output VARIABLES_WRITTEN source is `{"source":"activitySubmissions","nodeKey":"<producer>"}` with exact activityId and no single-task attribution. Version affected variable-history readers/writers together; retain readers for previous sources. New ALL execution receipts use a new codec version permitting nonfinal activity state and representative START tasks; existing receipt versions remain readable and replayable. Do not reinterpret old SINGLE completion results.

A preserving forward migration raises only the history details size CHECK to 24 MiB UTF-16 (25,165,824 bytes), retaining ISJSON. Existing NVARCHAR(MAX) variable/history columns and ALL activity constraints already support storage shape. Preserve applied changeset identities and paths; no database reset, rewrite of accepted submissions or migration execution is authorized by this planning pass. Publish/load new ALL definitions only on the coordinated upgraded engine/web/UI/host baseline. Do not mix old writers/readers with new larger evidence; deployments retain old published definitions and supported history/receipt reads.

## Acceptance scenarios

| ID | Required observation |
| --- | --- |
| AC01 | ALL publication accepts a declared contribution outcome and existing common task contract; rejects invalid policy/outcome/source/target combinations. Existing SINGLE definitions, outputs and legacy pins retain semantics. |
| AC02 | Roster sizes 1 and 100 work; 0/101, duplicate keys, missing/unsupported targets fail atomically. Mixed USER/GROUP/ROLE targets and repeated targets count tasks; invalid late roster entries leave no activity/tasks. |
| AC03 | All siblings observe one exact immutable input snapshot; same-command source visibility works. Replacement, claim/release and completed reloads do not refresh it. Source/snapshot failure rolls back entry. |
| AC04 | Nonfinal contribution saves one submission, advances managed instance version, leaves activity/position active, creates no output and invokes no downstream action. Frozen receipt replay after later resolution returns the original ACTIVE-activity result without another write. |
| AC05 | Final contribution resolves to contributionOutcome; early permitted outcome cancels only open siblings. Prior answers survive; cancelled tasks have no answer. Same actor may complete distinct obligations where host policy permits. |
| AC06 | A downstream failure rolls back final submission/output/cancellations/host effects; prior contributions remain. Concurrent sibling decisive commands cannot commit contradictory resolutions, and stale claims/completions fail under the changed aggregate path. |
| AC07 | Original binding returns the deterministic task-key-ordered collection including final/decisive answer and optional omissions. Named output appears once at resolution, remains absent beforehand, supports later replacement, and never changes original binding values. No TASK_COMPLETED data is copied to ACTIVITY_COMPLETED. |
| AC08 | Invalid/unresolved/ambiguous or corrupt sources fail explicitly; active external submission pages remain per-task filtered. Receiving-task reads authorize the shared snapshot; instance-only history cannot reveal it. Output/service copies retain their explicit existing visibility. |
| AC09 | Inclusive 64 KiB result and 8 MiB collection boundaries, one-unit excess, generic depth-18 versus result depth-16, worst-case Unicode/escaping, variable replacement, service maps, snapshots and response returnability obey C01/C02. No reservations/truncation; prior committed answers survive resolving-command size failures. |
| AC10 | Preserving migration retains old data/check validity; legacy history/receipts and newly created ALL receipts reload. TASK_CREATED lookup works for every sibling, not only the first. Large shared inputs produce one snapshot fact, not 100 copies; duplicate/cross-command snapshots or inconsistent sibling references fail integrity checks. |
| AC11 | Shared UI shows authorized ALL tasks, independent completion and cancellation/waiting state, handles tasks beyond page 1 and exact input/result values, and recovers frozen commands without implying their submitted task is current work. |

No product decision remains open for this slice. Capacity and compatibility choices above are engineering specifications, not executed evidence. Implementation discoveries that alter behavior must reconcile these records before dependent code. [Plan](plan.md) owns intended verification; [handover](implementation.md) owns actual results.
