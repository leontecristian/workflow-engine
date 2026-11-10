# Specification — F020 Human-task input bindings

**Revision:** 3 — 2026-11-10. **Status:** IMPLEMENTED; see [verification and remaining work](implementation.md). **Owner:** project owner. **Constitution:** 6.4.0.

## Purpose and scope

Give a reviewer explicit, read-only data from workflow variables or earlier human-task results. The owner agreed to creation-time snapshots and receiving-task visibility on 2026-11-10. Use the same source notation as [F019](../F019-named-task-output-variables/spec.md), limited here to existing SINGLE human nodes.

Applies to CON-03/04/06/08/09/10/13/14 in [constraints](../../constraints.md), ASM-02/10/11 in [assumptions](../../assumptions.md), and constitution C03/C06–C09. See [design sources](../../design-sources.md) for precedence. Revision 3 records the owner's terminology clarification: `inputs` supplies data to the task; `resultContract` describes the reviewer's answer. Remove human-node `inputContract` from new authoring; do not implement scalar submission contracts. This does not remove `startPolicy.inputContract`, which still declares workflow start inputs. Outcome and comment retain their existing separate fields.

## R01 — Pinned incoming contract and bindings

Add optional human-node `inputs`; omission means an empty map. New authoring must omit human-node `inputContract`; reject it with a definition diagnostic directing authors to remove the obsolete field. Runtime loading of already published definitions must continue accepting the historical empty array. Do not rewrite published content or hashes; nonempty legacy values remain unsupported. The reviewer submits any answer fields through `resultContract`, including simple scalar properties inside its structured result. Incoming values are not additional completion-request fields.

Use one binding map, without a separate `reviewInputContract` declaration. Each input name follows existing ActionField key rules; allow at most 50 unique names. Derive type and nullability from the pinned source: variables retain their declared STRING, LONG, DECIMAL, BOOLEAN, DATE, DATETIME or JSON type and nullability; a supplied submission result is a non-null JSON object. No duplicate type declarations, destination schemas, defaults or required/optional flags are needed. JSON uses existing bounded F009 object/array values.

`inputs` maps task-local input names to exactly one of:

- `{ "source": "variable", "key": "booking" }`: the current initialized variable value, including a materialized F019 named output.
- `{ "source": "submission", "nodeKey": "financeReview" }`: the original accepted structured result of that human node in this instance; no named output is required.

Reject invalid/duplicate input names, excess entries, unsupported sources/fields, unknown variables and invalid submission selectors at publication. Submission source nodes must be human nodes with OPTIONAL or REQUIRED result contracts. Reuse F019 selector rules. Every configured binding must resolve; OPTIONAL on a source result contract does not permit a missing result to be skipped. Explicit null from an initialized nullable variable is a valid input, distinct from an uninitialized variable. No coercion, implicit fallback, property selection or expression binding.

Use the name `inputs` consistently wherever explicit incoming bindings are supported. This aligns human and service tasks without adding an inputs block to gateways, start or end nodes. Services retain their registered action's destination contract; human display inputs derive their types from their sources.

Example fragment (the other required human-node fields are omitted):

```json
{

  "inputs": {
    "booking": { "source": "variable", "key": "booking" },
    "financeReview": { "source": "submission", "nodeKey": "financeReview" }
  }
}
```

## R02 — Snapshot once at task creation

Resolve, validate and persist all bound values atomically in the command transaction entering the human node. Include writes/submissions accepted earlier in that same transaction. Submission lookup requires one completed SINGLE occurrence, following F019; never choose the latest occurrence or use the triggering activity as a substitute for nodeKey.

The stored values are detached, exact and immutable. Later variable replacement, opening, refresh, claiming, release and completion do not recalculate them. Retain the snapshot with the task's operational evidence after completion. Reads must not depend on the continued availability of the source variable or submission once the snapshot exists.

Reuse exact F009 scalar/JSON codecs and per-value limits. Bound the complete serialized snapshot, including keys and type metadata, to 128 KiB in each of UTF-8 and UTF-16; existing command/history/response envelope limits also apply. This feature-specific engineering bound keeps the complete task projection within existing response allowances. Never truncate values.

An uninitialized variable fails with UNINITIALIZED_VARIABLE; an unavailable/omitted submission fails with SUBMISSION_UNAVAILABLE. Ambiguous/corrupt evidence, including stored values inconsistent with their source type/nullability, fails through existing integrity handling; exceeded bounds fail LIMIT_EXCEEDED. Failure rolls back task creation and the triggering command, including a preceding completion or participating service writes. Successful command replay neither resolves sources again nor replaces the snapshot.

## R03 — Receiving-task visibility

An explicit binding makes its selected value visible under the receiving task's current exact-task read authorization. Eligible peers on shared tasks can read it when that policy permits viewing the task; claiming does not introduce an input-only visibility restriction. Authorization is checked on every read, including completed-task reads.

Access to the source task is not additionally required to read this explicit copy. The binding grants no access to the source task, its other fields, or unrelated variables. Hosts retain business access and membership policy. Do not add per-input ACLs. Any history-detail route exposing the snapshot must apply receiving-task authorization; instance-history access alone must not expose it. Keep full values out of list summaries and durable delivery payloads.

## R04 — Read-only task display and compatibility

Expose input names, source-derived types and saved values through exact-task reads under `inputs`. Show them in a read-only section labelled Inputs, separate from outcome, comment and result editing, for both open and completed tasks. Preserve exact numbers and typed dates; bounded JSON text display is sufficient. No generated forms or editing controls are required.

Definitions without inputs retain existing behavior and show no input section. Existing draft/import documents containing the obsolete human-node inputContract must remove it before validation/publication; the error must explain that correction. Update current authoring templates and maintained examples during implementation, without rewriting historical evidence or persisted published definitions. Historical tasks without configured bindings have an empty snapshot; never reconstruct old tasks from current variables. Missing/corrupt snapshot evidence for a task whose pinned definition requires it is an integrity failure, not an empty result. Preserve legacy creation-evidence readers and immutable published definitions.

## Acceptance

| ID | Required observation |
| --- | --- |
| AC01 | Valid scalar/JSON variable and submission bindings publish without a separate incoming contract; invalid names/selectors/sources and excess entries fail. New authoring rejects human-node inputContract with a removal diagnostic. Published legacy definitions with an empty field still load; new no-binding definitions omit it. Start inputContract behavior is unchanged. |
| AC02 | A task receives current variable data and the original earlier result, including same-command data. Replacing a variable, refreshing or claiming leaves its saved values unchanged; completed-task reads retain them. |
| AC03 | Nullable variable null is preserved; uninitialized variables, omitted source results and bounds failures leave no partial task/snapshot or triggering-command effects. A successful replay creates no duplicate snapshot and does not reread sources. |
| AC04 | Authorized receiving-task readers, including eligible shared peers, see the copy without source-task access. Denied receiving-task access also denies snapshot history details; unrelated source data and durable events do not expose it. |
| AC05 | Read-only UI preserves exact data and separates inputs from the answer; old tasks show no section. Strict readers reject required missing/corrupt evidence and still accept historical evidence versions. |

## Deferred

ALL/loops, live refresh or re-snapshot commands, editable/prefilled answers, scalar completion inputs, guards using these values, field mappings, per-input permissions, generated forms and inputs for gateways/start/end are outside this slice. APOLO's policy for invalidating reviews after business changes remains host-owned. No product decisions remain open; engineering discoveries changing this contract must update these records before dependent implementation.
