# Specification — F022 Previous-decision comments

**Revision:** 1 — 2026-11-13. **Status:** IMPLEMENTED; owner-authorized delivery on 2026-11-13, verification in implementation.md. **Owner:** project owner. **Constitution:** 6.6.0.

## Purpose, authority and scope

Let a later approver see earlier decisions and their comments in the receiving task's read context. For example, an APOLO budget partner sees the engagement manager's decision and explanation before submitting their own. Reuse existing immutable completion evidence; this is not a discussion or a new submission mechanism.

**AGREED:** workflow-wide hide/show-all default; human-task overrides inherit/hide/show-all/selected stages; comments remain attached to accepted decisions; published definitions retain their behavior. On 2026-11-13 the owner explicitly selected receiving-task read authorization, without requiring access to source tasks. The following detailed contracts make that scope executable; they did not by themselves authorize implementation. The owner subsequently authorized T02–T06 on 2026-11-13.

Apply constitution C03/C06–C10; CON-03/07–10/12 in [constraints](../../constraints.md); ASM-10 for retained operational history and ASM-01 for eventual APOLO compatibility in [assumptions](../../assumptions.md). Follow [design precedence](../../design-sources.md). Build on [F010](../F010-human-task-outcomes/spec.md), [F020](../F020-human-task-input-bindings/spec.md) and implemented [F021](../F021-all-collective-review/spec.md). F021's implementation record supersedes older index/roadmap planning summaries.

Includes core definition validation/query contracts, optional shared web reads, shared task UI and sandbox demonstration. Excludes APOLO implementation, graphical designer, discussion threads, standalone/pre-completion messages, replies, edits, deletion, attachments, notifications, cross-instance history, expression access, and changes to comment submission/requirement rules. No new completion command or automatic comment-to-variable/input/result copying.

## R01 — Pinned configuration and compatibility

The definition may declare `previousDecisions: {"mode":"NONE"}` or `previousDecisions: {"mode":"ALL_PREVIOUS"}` at the root. Omission means NONE. Each HUMAN_TASK may declare the same property with mode INHERIT, NONE, ALL_PREVIOUS or SELECTED; omission means INHERIT. These are node contracts shared by every runtime task created by the node, not per-assignee settings. Other node types reject the property.

SELECTED requires `nodeKeys`, a nonempty array of distinct, case-sensitive human-node keys under existing key/count limits. Each key must identify another human node that can precede the receiving node along a directed path in the validated acyclic graph. A source need not lie on every path. Reject self, unknown, nonhuman, unreachable-from-source and duplicate selectors. `nodeKeys` is forbidden for every other mode; null, unknown properties/modes and root INHERIT/SELECTED are invalid. Selector order does not order the displayed decisions.

Resolve INHERIT against the pinned root setting; a node override replaces, rather than combines with, that setting. NONE controls this context section only: it neither disables writing/retaining a comment nor revokes independently authorized history access. `requiresComment` remains outcome-specific and unchanged.

Keep schemaVersion 1 as an additive supported contract. Strict draft/import validation must cover all supported schema paths and report existing location-aware structural/graph diagnostics. Change the validation identity so stale draft validation cannot publish the extension. Published legacy documents with no setting load as NONE without rewriting content or hashes. Enabling visibility for future instances requires a new published definition version; existing instances stay pinned.

Illustrative fragments, using the exact proposed-for-delivery syntax (not a complete import document):

```json
{"previousDecisions":{"mode":"ALL_PREVIOUS"}}
```

```json
{"key":"partnerApproval","type":"HUMAN_TASK",
 "previousDecisions":{"mode":"SELECTED","nodeKeys":["engagementManagerApproval"]}}
```

## R02 — Stable earlier-stage selection

Resolve within the receiving task's instance and pinned definition. The receiving activity's retained ACTIVITY_ENTERED history sequence is the exclusive cutoff. A source activity must have completed before that cutoff, established by its retained ACTIVITY_COMPLETED sequence and consistency with completed activity state. Current status alone does not establish historical ordering. Include its accepted TASK_COMPLETED decisions only, filtered by R01; use execution evidence, never definition-array order, timestamps or the current process position.

For SINGLE include its accepted task decision. For ALL include each completed task's decision, including earlier contributions and the decisive early-resolution task, with its individual outcome. Exclude cancelled/open siblings and do not substitute the activity resolution outcome for a task outcome. Exclude the receiving activity itself, including previously completed peers in that same ALL stage. Later stages and different instances cannot appear.

A conditionally skipped selected stage contributes no entries and does not prevent task creation or viewing. A source with FORBIDDEN or omitted structured result can still supply its comment; result bindings are independent. Include decisions with null/blank comments so the decision sequence remains understandable, preserving the exact accepted value. The UI may label these as no comment provided without modifying stored text.

Order by completion history sequence ascending. The cutoff and immutable source evidence make the selected set stable after creation, including completed-task reads, without copying comments into a new snapshot. Current receiving-task access is still checked on every read. Opening, claiming, refreshing, completing or advancing the instance does not extend the cutoff. This feature supplies no loop/repeated-occurrence semantics; the supported graph remains acyclic.

## R03 — Receiving-task visibility and narrow projection

An enabled policy explicitly shares only this projection with current authorized readers of the receiving task, including eligible shared peers. Source-task access is not required. Authorize the receiving task before returning settings or entries, including on every continuation request and on completed-task reads. Hosts retain business access, membership and trusted identity policy. No new per-comment ACL is introduced.

This grants no access to source-task pages, structured answers, inputs, variables, full history details or other instances. Existing source-submission/history endpoints retain their existing checks. Do not expose comment text in task lists, durable delivery, command receipts or diagnostic logging. Source identifiers are attribution, not permission-bearing links; the shared UI must not infer navigation rights from their presence.

Each entry contains exactly `taskId`, `activityId`, `nodeKey`, `nodeName`, `outcomeKey`, `outcomeLabel`, `actorKind`, `actorRef`, `occurredAt`, `historySequence`, and `comment`. Node/outcome labels come from the pinned source contract; actor/time/comment come from accepted completion evidence, never the assignment target or current claimant. IDs/sequences use existing exact decimal-string wire encoding, actor kinds use existing values, and occurredAt uses the existing instant encoding. comment is the original string or null. No result, command payload or input snapshot is included. Actor display-name enrichment remains host presentation work; actorRef is the reliable fallback.

Missing/duplicate cutoff evidence, missing required completion facts for eligible completed tasks, mismatched source identities/outcomes and corrupt evidence fail existing sanitized HOST_CONFIGURATION integrity handling. Never present corrupt or unavailable required evidence as an empty successful list. Retention of the original facts remains required while tasks are operationally readable (ASM-10); this slice does not add archival/redaction policy.

## R04 — Task read context and bounded access

Extend the existing exact-task contract with `previousDecisions: {"mode":"NONE|ALL_PREVIOUS|SELECTED"}` using the resolved mode; INHERIT never appears in this read projection. This metadata tells APOLO or the shared task page whether to load the section. It does not contain the full list. Do not change generic task-list projections or completion payloads.

Add a read in the existing optional runtime-read group: `GET /tasks/{id}/previous-decisions?afterSequence=0&limit=20`, relative to the configured runtime base path. The core equivalent accepts the receiving task ID and pagination arguments and returns workflow-owned detached DTOs; hosts use the same core selection logic with explicit host authorization. No UI-side history assembly or APOLO-specific query implementation.

Return `{"items":[...],"nextAfterSequence":null}`; a non-null nextAfterSequence is an exact decimal string for continuation. afterSequence is an exclusive, nonnegative history sequence (default 0); limit is an integer 1–50 (default 20). Invalid numbers, overflow, duplicate/unknown query parameters and out-of-range limits use existing INVALID_INPUT/HTTP 400 handling. A valid cursor at or beyond the cutoff produces an empty terminal page. Cursors never expand the task's configured sources or authorization.

Bound each serialized complete page to 256 KiB UTF-8 and UTF-16, also respecting any stricter enclosing transport limit. Return only whole entries; use the last returned history sequence when another eligible entry remains. Count and byte bounds both apply; never silently omit remaining entries or truncate comment text. An entry that cannot fit alone fails LIMIT_EXCEEDED. Query in bounded batches with the source/cutoff filters pushed into persistence; do not fetch all instance history or materialize structured result collections just to list comments. NONE returns an empty terminal page after authorization.

Use existing runtime-read not-found/access-denied conventions without leaking task existence. Reads make no instance claim, increment no version and write no history/receipt. Existing completion facts remain authoritative; no migration, new table/column, history format or command fingerprint change is planned.

## R05 — Shared task presentation

For an enabled policy show a read-only Previous decisions section for open and completed tasks, separate from Inputs, the task's own comment/result editor and its own accepted submission. Display stage, actual actor, time, outcome and comment as safe text, preserving meaningful whitespace. Do not render HTML/Markdown from comments. Support explicit loading, empty, failed-read/retry and load-more states without replacing existing entries with a misleading empty state on failure. A task with NONE has no section and requires no page request.

Use the same core-backed read for the sandbox/shared task page and future APOLO consumers. The read-only section works without a command provider. It neither changes completion permissions nor blocks completion based on whether the user expanded/read the section. Clear task-specific pages on task/account changes and ignore late responses for a previous task/session under existing UI lifecycle conventions.

## Acceptance scenarios

| ID | Required observation |
| --- | --- |
| AC01 | Omitted/root/node modes resolve correctly; overrides replace the default. Invalid modes/fields/selectors fail at the proper location. Existing published documents remain NONE; stale validation cannot publish new settings. |
| AC02 | In a three-stage flow, ALL_PREVIOUS shows executed earlier decisions; SELECTED narrows them, skipped sources are empty, and later/same-stage/other-instance facts never appear. Reading after advancement retains the original cutoff. |
| AC03 | Resolved ALL supplies all accepted individual outcomes/comments in history order, including early-rejection contributions; cancelled peers contribute nothing. Null/blank comments and result-free sources remain valid entries. |
| AC04 | Receiving-task readers without source-task access can read configured entries. Denied receiving-task access denies metadata/pages/continuations; source endpoints remain protected and no result/input/full-history material escapes. Shared-task eligibility changes are honored. |
| AC05 | Exact text, trusted actor/time, pinned labels and large identifier precision survive persistence/reload. Missing/duplicate/mismatched required evidence fails explicitly rather than disappearing. |
| AC06 | Count/byte pagination covers all eligible entries exactly once with no text truncation; invalid requests fail, terminal cursors end, NONE is empty and query reads cause no writes. |
| AC07 | Shared UI renders safe read-only entries, no-comment/empty/error states and load more; NONE hides the section. Open/completed and read-only hosts work; task/session switches cannot display stale comments. |

## Readiness

No material product decision is open for this bounded slice. The authorization clarification is AGREED. Timing, projection, notation and pagination above are the concrete design for delivery. The owner subsequently requested implementation of T02–T06; see the delivery record. APOLO integration, designer controls and ongoing discussions remain deferred. See [plan](plan.md), [tasks](tasks.md) and [implementation handover](implementation.md).
