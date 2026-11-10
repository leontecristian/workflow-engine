# Specification — F019 Named task output variables

**Revision:** 2 — 2026-11-10. **Status:** IMPLEMENTED. **Owner:** project owner. **Constitution:** 6.3.0. See the [implementation handover](implementation.md) for verification and open gates.

## Purpose and agreed scope

Let a service receive either a human node's original accepted submission or the current value of a workflow variable through one input-binding mechanism. Prefer a submission binding when the service needs the reviewer's original answer. Create a named output variable when subsequent steps need a mutable working copy.

An author may optionally name a human task node's structured output. When that task completes with an accepted result, core creates a JSON workflow variable containing the result. No separate variable declaration, duplicate structure schema or service node is required. Before submission, the variable does not exist. Submission bindings do not require this copy.

Owner decisions: configuration is per task node; create only at completion; reject name collisions; distinguish absence from null/defaults. On 2026-11-10 the owner also selected later replacement like other JSON variables and existing workflow-variable visibility. The immutable submission remains the original evidence. The owner also selected both service input sources: current variable value or original submission by node key. No product decisions remain open for this slice.

Build on [F018](../F018-structured-task-submissions/spec.md) and [F009](../F009-json-execution-variables/spec.md). Apply CON-06 transaction/concurrency and CON-07 history guarantees, [CON-13/14](../../constraints.md#con-13--structured-task-submissions), [ASM-10/11](../../assumptions.md#asm-10--completion-history-can-support-operational-result-retrieval), and [source precedence](../../design-sources.md). The F019 amendment permits explicit named-output creation; it does not permit implicit merging or arbitrary runtime variable names.

Scope is existing SINGLE human activities. ALL aggregation, loops, multiple output names, field mappings, computed names, outcome-specific mappings, variable deletion, new generic variable-update endpoints and generated designer forms are deferred. Existing workflows without the new settings retain their behavior. This revision also adds explicit submission-by-node-key service input bindings; it does not add arbitrary prior-submission expression access.

## Required behavior

### R01 — One optional name, one existing result contract

Add optional `outputVariable` to a HUMAN_TASK node. Its value is a literal variable key using the existing case-sensitive key grammar, `^[A-Za-z][A-Za-z0-9._-]{0,99}$`. Omission disables copying. Empty string, null, wrong type or use on other node types fails definition validation. It is permitted only with OPTIONAL or REQUIRED result contracts; FORBIDDEN cannot produce a named result. It applies to every accepted outcome of that node.

The name and existing result contract are pinned with the workflow version. Authors do not add an entry to top-level `variables`, repeat a type or duplicate the result schema. Core may derive a descriptor to validate bindings and expressions; this creates neither a runtime value nor an uninitialized variable-list entry. A named output is a non-null JSON variable. Its creation value is the entire accepted object; outcome, comment and scalar inputs are not copied.

Illustrative fragment inside an otherwise valid human node:

```json
{
  "outputVariable": "reviewResult",
  "resultContract": {
    "mode": "REQUIRED",
    "schema": {
      "type": "object",
      "properties": {"approvedAmount": {"type": "number", "minimum": 0}},
      "required": ["approvedAmount"],
      "additionalProperties": false
    }
  }
}
```

The enclosing node and result-contract wire structure must follow the current F018 definition language; the example introduces only `outputVariable` and does not replace those required fields.

### R02 — Reject conflicting producers

At definition validation/publication reject any output name matching a top-level variable or another human node's output name, including nodes on mutually exclusive routes. Reuse the existing key comparison; no case folding or implicit renaming. Count top-level declarations plus named outputs toward the existing 100-variable ceiling.

Only the configured task completion creates a named output. Start initialization cannot target it. A runtime creation encountering an existing row fails with CONFLICT and leaves the completion uncommitted; it must never silently replace that row. An authorized successful receipt replay returns before this creation check and performs no new write.

### R03 — Create with successful completion

After F018 structural and host business validation, copy the exact accepted object into the named variable before downstream service/gateway traversal. Use existing workflow_variable JSON storage and exact F009 value/scale handling. Persist creation and its variable-write evidence in the same host transaction as task/activity completion, submission evidence, receipt and effects, under the existing instance claim.

Before completion, after a skipped task, after failed validation, or when an OPTIONAL result is omitted, no named variable row or variable-list entry exists. Do not create null, an empty object or a default. An accepted empty object is a present value if the schema allows it. No lazy reconstruction from history on read is permitted.

Downstream bindings, expressions and authorized variable reads in the same transaction see the new value. Failure of creation, capacity/evidence checks or downstream execution rolls back this command's creation and completion. Existing committed submissions/variables remain. Creation is engine behavior and requires no host service action.

### R04 — Explicit absence

Definition validation recognizes output names for existing variable bindings and expression roots without requiring creation first or proving which route will run. An evaluated read of an absent output fails with existing UNINITIALIZED_VARIABLE, including a direct null-safe root read; absent is not JSON null. Nested missing-property behavior stays as defined by F009.

Add the restricted expression predicate `#hasVariable('reviewResult')`: exactly one string literal naming a known top-level variable or named output. It returns false for absence and true for a materialized value, including explicit null in an ordinary nullable variable. Unknown names, computed arguments and general function/method dispatch remain invalid. Existing short-circuit and evaluation budgets apply, so `#hasVariable('reviewResult') && #vars.reviewResult.approvedAmount > 0` safely avoids reading an absent output. Only evaluated checks produce routing evidence.

Variable queries list named outputs only after creation, preserving existing pagination/version consistency and ordinary declared-variable behavior.

### R05 — Ordinary mutable copy, immutable original

After creation, existing explicit service output bindings may replace the whole value using ordinary authorized execution rules. Such a destination is non-null JSON (object or array); subsequent replacement follows F009 JSON bounds, not the original task-result field schema. The result schema validates the submission once; it is not a second ongoing variable schema. No nested merge, schema duplication or new update API is introduced.

A service mapping that actually writes a named output before its producer has created it fails with UNINITIALIZED_VARIABLE. An omitted optional service output makes no write. Replacements never change the immutable submission or rerun task validation. Retain F018's 64 KiB submission ceiling, F009's 256 KiB JSON-variable ceiling and 1 MiB total initialized JSON-variable budget; copying also consumes the current-state budget.

### R06 — Visibility and evidence

Configuring `outputVariable` explicitly opts the copied data into existing instance-variable and variable-write-history visibility, including explicit service bindings. It does not grant access to the original exact-task submission. Preserve existing host access checks for each API; do not add per-variable task ACLs.

Record creation through the existing VARIABLES_WRITTEN mechanism: the full exact value, previousInitialized false, producer node and exact task/activity provenance. Replacements retain ordinary variable-write evidence. Durable delivery remains metadata-only. For an evaluated existence check record the variable key/type, empty path, state EXISTS and a Boolean value; do not invent a null read or retain the variable contents just to prove existence. Existing routing records remain readable.

### R07 — Two explicit service input sources

**Settled authoring guidance — owner decision, 2026-11-10:** both sources are part of F019, using the same declared service-input contract. This guidance recommends a source; the engine never switches sources implicitly or falls back from one to the other.

| Need | Recommended binding |
| --- | --- |
| Use what a reviewer originally submitted | `{"source":"submission","nodeKey":"financeReview"}`; no output variable is necessary. |
| Use working data that later steps may replace | `{"source":"variable","key":"financeResult"}`; optionally create this copy through the producer's outputVariable. |

A service input may use the existing `{"source":"variable","key":"reviewResult"}` binding for the current variable value, or the new `{"source":"submission","nodeKey":"financeReview"}` binding for the original accepted result. Both supply a detached JSON value to the same declared service input. Submission binding requires neither an outputVariable setting nor a variable row, and never reads a variable copy or depends on completedActivityId.

Submission bindings are supported only in SERVICE_TASK inputs. Require exactly source and a literal nodeKey; reject key/value fields, computed selectors, unknown nodes, non-human nodes, FORBIDDEN result contracts and non-JSON target inputs at definition validation. Resolve within the current instance and its pinned definition. Do not add expression syntax, field selection or bindings for actor/outcome/comment metadata. OPTIONAL and REQUIRED source result contracts are supported; an optional service input does not suppress an explicitly configured binding failure.

At execution, find exactly one activity occurrence for that node in this instance, require it to be completed, and read its one accepted SINGLE task result using F018's strict submission reader. A skipped/not-yet-completed node or omitted optional result fails with new SUBMISSION_UNAVAILABLE (422) before invoking the service. Multiple activity matches, multiple accepted submissions or corrupt/missing required completion evidence fail with the existing integrity-error category; never choose the latest or return null/defaults. Successful same-command completion is visible. An unavailable source fails even if a later route might eventually reach that node; static validation need not prove route ordering.

This is an engine-resolved input under the existing host-authorized command and registered service capability. No new external history lookup endpoint or per-binding authorization framework is introduced. Configuring this source explicitly supplies the original result to the service and its existing service-input history, governed by those existing access rules; it does not grant browser/API access to the original exact-task submission. Keep full input evidence within existing bounds and durable delivery metadata-only. Resolution failure rolls back the current command; authorized successful replay performs no new lookup or service effect.

### Example — current value or original submission

Use START → HUMAN_TASK reviewAmount → SERVICE_TASK recordAmount → END. The human node retains F018's approvedAmount/note result contract and sets `outputVariable: "reviewResult"`. The service declares a required non-null JSON input named `reviewResult`, bound through the existing syntax:

```json
{
  "inputs": {
    "reviewResult": {"source": "variable", "key": "reviewResult"}
  }
}
```

The F019 sample action reads approvedAmount from that input and records the amount using the existing sandbox cents convention. It does not query submission history to obtain its working input. No separate variable declaration or duplicate result schema is needed.

Keep F018's `completedActivityId` unchanged as execution context for the activity completed by the current command. Existing actions may continue using it with authorized submission reads, but it is not the recommended data-selection mechanism for F019 services. Use the explicit submission binding to select an original answer independently of that trigger. A later replacement of reviewResult changes service input data, never the original submission. F019 does not change the existing F018 sample action's contract.

For a service that needs the original Finance review instead, configure its same JSON input as:

```json
{
  "inputs": {
    "reviewResult": {"source": "submission", "nodeKey": "financeReview"}
  }
}
```

If Finance submitted approvedAmount 1000 and a later step replaced its named variable with approvedAmount 900, the variable source supplies 900 and the submission source supplies 1000. A service running after a subsequent Manager review still resolves Finance explicitly. The service implementation consumes its input identically in both cases; it does not query history itself.

## Acceptance

| ID | Required observation |
| --- | --- |
| AC01 | A valid named output needs no separate declaration; invalid placement/name, FORBIDDEN contracts, duplicate producers/top-level collisions and excess combined keys fail publication. Existing definitions remain unchanged. |
| AC02 | No variable exists before submission, for a skipped task or for omitted optional results. Accepted objects create one exact JSON value; invalid/rejected submissions create nothing. |
| AC03 | Same-transaction downstream bindings, expressions and reads see the new variable; the sample recordAmount action receives reviewResult through its JSON input binding without a submission lookup. Capacity/downstream failure rolls back this creation/completion; successful replay does not create or overwrite again. |
| AC04 | An absent direct read fails; hasVariable returns false and safely short-circuits. After creation it returns true. Unknown/dynamic names and other calls fail validation; actual Boolean existence evidence is readable. |
| AC05 | A service cannot create the named output early, but may replace it after creation. The original submission and its numeric scale remain unchanged; replaced values obey ordinary JSON-variable rules. |
| AC06 | Existing variable permissions govern the copy and write history, even where task-submission access is denied. Pagination hides uncreated outputs and exposes created ones consistently. |
| AC07 | Both sources feed the same JSON service input: after replacement the variable source supplies the current value and submission-by-nodeKey supplies the exact original, including scale. Finance resolves after Manager completion and without any named output configured. Invalid selectors/types fail publication; skipped/incomplete/omitted results fail before the action, and ambiguous/corrupt evidence fails without selecting a latest result. Current-command submissions are visible and failed resolution rolls back the current command. |

The [plan](plan.md) defines verification. The [implementation handover](implementation.md) records the results.
