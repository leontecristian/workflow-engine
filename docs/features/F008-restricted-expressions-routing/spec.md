# F008 — Restricted expressions and exclusive routing

**Status:** DRAFT, revision 1 — 2026-11-01. **Owner:** Workflow project owner. **Constitution:** [v3.0.0](../../constitution.md). G01–G03 gate readiness; no safe evaluator or implementation is claimed.

## Outcome, authority and dependency

Extend [F007](../F007-typed-bindings-service-tasks/spec.md) with restricted Boolean expressions and EXCLUSIVE_GATEWAY routing. A synchronous service produces typed scalar values and the workflow chooses its next path deterministically, preserving the host transaction, pinned version, aggregate concurrency, successful-command replay, history and durable handoff.

Authority: [language design](../../architecture/workflow-definition-language-design.md), sections 5.2, 8.3–8.5, 10.2–10.4, 11.1–11.2, 12.1.9/16 and 14.2; [source precedence](../../design-sources.md); CON-01–09/11/12 and ASM-01–05/08. First-match, SpEL, strict-error and resource-bound decisions are AGREED. This scalar-only graph/evaluator slice is PROPOSED pending readiness reconciliation; it does not redefine the full language.

At this task baseline, F007 revision 2 was READY with no completed implementation recorded. Concurrent F007 source edits appeared during preparation; their completeness and verification were not assessed here. Its typed variables/actions, progression, codecs and host sample are dependencies for F008 code. Specification work can proceed now. Reconcile actual F007 delivery before dependent implementation; planned APIs are not installed capabilities. No F005/F006 acceptance gate is closed by this specification.

## Proposed bounded slice

Support START, SERVICE_TASK, one SINGLE human node, one or more EXCLUSIVE_GATEWAY nodes and one or more END nodes. Keep one active position and an acyclic graph. Every START-to-END path must pass through the sole human node exactly once. Gateways may branch and reconverge; a merge does not synchronize parallel branches. Each command still pauses at the human task or ends with the same task/activity result shape defined in F007. Reject bypass-human paths, additional human nodes and cycles. Review loops remain broader agreed language work.

Use declared scalar #vars only initially. Reject #facts references explicitly until a declared provider/snapshot/fingerprint contract is specified; do not silently substitute an empty map. No expression bindings are added to start/service mappings. Nonempty guards, JSON navigation, structured submissions, ALL, external variable updates, no-human workflows, parallel/inclusive gateways, decision tables and graphical authoring remain outside scope.

Scalar-only scope does not justify banning ordinary arithmetic or inventing another expression language. G01 must define a supported SpEL profile consistent with the governing semantics and exactness rules.

## Requirements

- **R01 — Gateway and graph validation.** Gateway nodes use only common key/type/name/optional description fields. Each outgoing transition has exactly one of nonempty condition or default:true. Reject false/null defaults, combined fields, gateway outcomes, unknown fields and legacy DECISION/unsupported gateway types. Require at least one outgoing route; a default-only gateway is valid. Retain START/service single unconditional routes and the sole human outcome route. Every node is reachable and can reach END; enforce the acyclic/mandatory-human subset structurally without predicting conditions.
- **R02 — Ordered first match.** Filter transitions by source and evaluate conditional routes in their relative top-level array order. First actual Boolean true wins; overlaps are valid. Stop immediately at a match. Consider the sole optional default only after all conditions return false, regardless of its array position. Preserve source text/order in immutable canonical definitions; diagram layout and key sorting do not determine priority.
- **R03 — Strict errors.** All-false without default fails ROUTE_NOT_FOUND. A non-Boolean result, evaluated missing/uninitialized value, invalid conversion, arithmetic/evaluation error or exhausted limit fails the command without later conditions or default. Boolean false is normal. Skipped conditions are not evaluated or recorded as false.
- **R04 — Validate all conditions.** Parse and structurally inspect every condition before publication, including routes that may never be evaluated. Check permitted roots, declared names, AST/operator profile, complexity and types where statically provable. Publication does not invoke actions or evaluate live business data. Emit located bounded diagnostics. Runtime checks remain necessary; publication does not prove initialization or every result type.
- **R05 — Exact detached values.** Decode F007 values into the finalized exact scalar representations. Preserve uninitialized versus explicit typed null. An evaluated absent-variable access fails even through null-safe syntax; short-circuited reads do not occur. Snapshot variables at gateway entry after preceding service mappings. No silent floating-point conversion, overflow, rounding or mutation. Dates/instants/Booleans/strings retain declared meaning.
- **R06 — Enforce read-only access.** Deny arbitrary roots, beans/types/constructors, reflection/class metadata, general method/function calls, assignment/increment, auto-growth, collection projection/selection, unrestricted regex and hidden time/random/system values. Inspect structure and constrain context/accessors; a restricted context alone is insufficient. Property/index access must resolve only declared scalar keys under #vars, without reflection, Map-method or arbitrary-property fallback. No nested JSON traversal.
- **R07 — Bound work.** Enforce 2,000 Unicode code points per expression, 256 parsed nodes, depth 32 and applicable navigation bound 16. Share a 500-evaluation allowance per command; each evaluation has 10,000 operations enforced by a verified local mechanism. A gateway visit costs one of F007's 50 automatic steps, independently of condition count. Charge before the next step/evaluation. Skipped conditions consume no evaluation. An unchecked timeout leaving work running is not enforcement.
- **R08 — Preserve transaction/replay.** Routing runs under F007's receipt-first protocol and early flushed instance claim. Routing failure rolls back current task completion, service effects, variables, history, receipt and handoff; earlier successful commands remain. Successful authorized replay performs no evaluation/action even if current state/deployment changed. No automatic mutation retry or default-on-error catch.
- **R09 — Explain the selected route.** Retain ordered evaluated transition IDs/results, selected transition/default flag and typed values actually read. Do not read skipped expressions' inputs for evidence. Required evidence must fit F007 event/cumulative budgets or fail atomically. Externally delivered payloads contain no scalar values.
- **R10 — Preserve version semantics.** Conditions belong to schemaVersion 1; no expressionPolicy field or host override. Preserve older definitions and fingerprints/results. Supported profile behavior must survive engine/dependency upgrades or be explicitly rejected under compatibility rules. Inspect the effective dependency before choosing an operation-counter solution; upgrade related Spring components coherently if necessary. Separate APOLO compatibility evidence remains required.
- **R11 — Deliver through existing host surfaces.** Reuse scalar entry, sandbox action/effects, variable/history inspection and command recovery. Present readable route evidence without frontend evaluation. Preserve roles/CSRF/exact numbers and graph-unavailable fallback for unsupported topologies. No new predicate-preview endpoint, fact inputs or graph editor is required.
- **R12 — Record real evidence.** Distinguish code, unit/build results and actual safety/provider/browser acceptance. Preserve F007 receipts/migrations and older feature evidence. No schema change is expected, subject to final event/reader reconciliation. Mocked tests cannot prove database rollback/concurrency or actual expression operation accounting.

## Expression and runtime contract boundary

Use `condition: "#vars.recordedQuantity > 10"` or `default: true`, with no numeric priority. Expression source comes only from the pinned published definition; clients cannot submit source at runtime.

G01 must finalize the exact AST/operator profile, literal handling, numeric promotion/overflow, date/instant comparison, null semantics and access paths. Anticipated support includes Boolean/comparison operators, ordinary bounded arithmetic, parentheses, short-circuit/conditional forms and direct declared-key access. This is not yet an approved AST allowlist. Reject unknown AST nodes rather than accepting wildcard classes or using regex/string scans as the security boundary. Template expressions and implicit evaluation of string values are excluded.

G02 must settle effective dependency support and operation/parse/cache limits. Cache only immutable validated plans keyed by pinned definition hash, transition key, source and language implementation version; never mutable actor data/context/counters. Eviction cannot change semantics. No global Spring settings or compiler mode that bypasses restrictions.

Reuse F007 command DTOs and fingerprints: no new mutable external fact inputs. Retain the sole-human result shape and extended receipt schema path, while preserving strict legacy readers. Extend pinned loading and validation for branches without runtime authoring revalidation. Final G03 reconciliation must remove chain-only assumptions from extended readers without weakening old receipts.

Proposed new history type GATEWAY_ROUTED: nodeKey, ordered evaluations (transitionKey, Boolean result, typed actual-read evidence), selectedTransitionKey and usedDefault. Emit before TRANSITION_TAKEN. Failed commands commit no success routing event. Read evidence preserves null/type and can deduplicate repeated reads within the immutable gateway snapshot. Source text remains in the pinned definition. Proposed durable payload contains nodeKey and selectedTransitionKey only. G03 fixes exact DTO/JSON fields, versions, bounds and error mappings before implementation.

Proposed runtime categories: ROUTE_NOT_FOUND, EXPRESSION_EVALUATION_FAILED, EXPRESSION_LIMIT_EXCEEDED; retain UNINITIALIZED_VARIABLE and existing access/concurrency categories. Messages are sanitized and correlated, with no raw objects/secrets. Final HTTP and validation diagnostic codes are G03 work.

## Complete draft fixture

Requires F007's sandbox.recordQuantity, SANDBOX_REVIEW and sandbox.singleReviewer. Import/publication and execution are future acceptance work, not performed here. G01/G03 must validate this fixture against finalized contracts.

```json
{
  "schemaVersion": 1, "key": "sandbox.quantityRouting", "version": 1, "name": "Quantity routing",
  "startPolicy": {
    "inputContract": [{"key":"quantity","type":"LONG","required":true,"nullable":false}],
    "initialize": {"quantity":{"source":"startInput","key":"quantity"}}
  },
  "variables": [
    {"key":"quantity","type":"LONG","nullable":false},
    {"key":"recordedQuantity","type":"LONG","nullable":false}
  ],
  "nodes": [
    {"key":"BEGIN","type":"START","name":"Start"},
    {"key":"RECORD","type":"SERVICE_TASK","name":"Record quantity","action":"sandbox.recordQuantity",
     "inputs":{"quantity":{"source":"variable","key":"quantity"}},
     "outputs":{"recordedQuantity":{"source":"actionOutput","key":"recordedQuantity"}}},
    {"key":"REVIEW","type":"HUMAN_TASK","name":"Review","taskType":"SANDBOX_REVIEW",
     "assignment":{"resolver":"sandbox.singleReviewer","inputs":{}},"completion":{"policy":"SINGLE"},
     "inputContract":[],"resultContract":{"mode":"FORBIDDEN"},
     "outcomes":[{"key":"COMPLETE","label":"Complete","requiresComment":false}]},
    {"key":"ROUTE","type":"EXCLUSIVE_GATEWAY","name":"Choose result"},
    {"key":"HIGH","type":"END","name":"High quantity","result":"HIGH"},
    {"key":"LOW","type":"END","name":"Other quantity","result":"LOW"}
  ],
  "transitions": [
    {"key":"beginRecord","from":"BEGIN","to":"RECORD"},
    {"key":"beginReview","from":"RECORD","to":"REVIEW"},
    {"key":"finishReview","from":"REVIEW","to":"ROUTE","outcome":"COMPLETE"},
    {"key":"fallback","from":"ROUTE","to":"LOW","default":true},
    {"key":"high","from":"ROUTE","to":"HIGH","condition":"#vars.recordedQuantity > 10"},
    {"key":"positive","from":"ROUTE","to":"LOW","condition":"#vars.recordedQuantity > 0"}
  ]
}
```

Use F007's request/start/complete flow with actual returned IDs/tokens and frozen UUIDs. quantity `"20"` selects high and skips positive despite overlapping true conditions. `"5"` evaluates false/true and selects positive. `"0"` evaluates false/false and takes fallback despite its earlier array position. Start pauses at REVIEW with one recorded service effect; completion routes to a normal COMPLETED result. Replay preserves original evidence/effect counts. Reverse high/positive only in a new published version to demonstrate changed priority without altering old instances.

Derived fixtures cover missing default, non-Boolean result, evaluated uninitialized variable, short-circuited missing access, unsafe syntax in a lower-priority condition and budget boundaries. Never bypass publication to install unsafe definitions.

## Acceptance scenarios

| ID | Required observation | Requirements |
| --- | --- | --- |
| AC01 | Minimal/F007 workflows, receipt replay, reads and recovery remain compatible. | R08/10/12 |
| AC02 | Fixture quantities 20/5/0 select high/positive/default with pinned result and evidence. | R01/02/09/11 |
| AC03 | Overlap chooses first; default position/layout does not change priority; new order leaves old pins unchanged. | R02/10 |
| AC04 | All-false/no-default, evaluated errors and non-Boolean results fail without fallback or committed success. | R03/08 |
| AC05 | Uninitialized access fails even nullable/null-safe; skipped access does not; typed null follows supported semantics. | R03/05 |
| AC06 | Invalid syntax/undeclared names/unsafe AST in any condition prevents publication, including skipped routes. | R04/06 |
| AC07 | Reject bean/type/constructor/reflection/method/mutation/template/regex/traversal escapes; literal text stays data. | R06 |
| AC08 | Exact LONG/DECIMAL boundaries, mixed arithmetic, division, overflow and temporal/null rules match finalized semantics. | R05/10 |
| AC09 | Structural/evaluation/operation/automatic-step limits hold at boundaries; skipped routes consume no evaluation. | R07 |
| AC10 | Routing failure rolls back current effects/variables/task/history/receipt/outbox and preserves earlier commands. | R08/12 |
| AC11 | Successful replay evaluates nothing; overlapping completions produce one committed route/result. | R08 |
| AC12 | Evidence includes only actual evaluations/reads in order; overflow rolls back; external payloads omit values. | R09 |
| AC13 | Invalid fields/defaults/cycles/disconnected/bypass-human graphs fail; valid branching/merging/multiple ends pass. | R01 |
| AC14 | Packaged browser shows selected route/result with correct authorization, exact input, recovery and graph fallback. | R11 |
| AC15 | Dependency/profile changes preserve golden behavior and cannot enable unsafe syntax or silently change pins. | R06/07/10 |

## Readiness gates

- **G01 — Profile and typed semantics (OPEN).** Finalize AST/operators/access paths, Java scalar projections, numeric/temporal/null matrix and publication/runtime typing. Reconcile standard SpEL arithmetic with exactness without silent rewrites or a blanket ban. Confirm scalar-only #vars scope; facts need a separate declared contract if added.
- **G02 — Enforceable limits (OPEN).** Inspect actual effective Spring APIs; choose coherent dependency/configuration changes if needed, define structural/operation counters and bounded caching, and plan abuse/compatibility verification. The current POM declares spring-expression through Boot 3.4.5 management; this does not establish counter availability/safety. Source length is not an operation limit.
- **G03 — F007 integration and codecs (OPEN).** Reconcile actual F007 status/APIs; finalize evaluator/route DTOs, event fields/version, runtime/HTTP errors, validator-context identity and result/sequence checks. Complete golden expression/evidence/error fixtures and confirm storage/retention bounds. No core migration is currently expected.

Independent specification work can proceed while F007 implementation is pending. Mark READY only when contracts are resolved; runtime acceptance remains a separate implementation obligation.
