# Specification — F018 Structured task submissions

**Revision:** 3 — 2026-11-09. **Status:** IMPLEMENTED. **Owner:** project owner. **Constitution:** 6.2.0. O01–O05 are closed by owner decisions and the concrete contracts below. Implementation and acceptance status are tracked in the [handover](implementation.md).

## Purpose and scope

Allow completion of an existing SINGLE human task with a validated, immutable structured result. Extend existing exact-task/activity submission reads and make the final submission available to a downstream host service action in the same transaction. Preserve the F017 assignment/claim rules and existing no-result workflows.

The owner selected a JSON editor with validation feedback and read-only result viewing, and a focused host submission-validation hook on 2026-11-09. Generated forms and configurable outcome guards/bindings are deferred. Nonempty scalar human inputs, ALL review, loops, cancellation/reassignment, draft saving, partial completion, result edits, implicit variable merging, direct prior-submission expression/binding syntax and APOLO business policy are outside F018.

Authority: [CON-13](../../constraints.md#con-13--structured-task-submissions), [ASM-10](../../assumptions.md#asm-10--completion-history-can-support-operational-result-retrieval), constitution C03/C06–C10, and [language design](../../architecture/workflow-definition-language-design.md) sections 10.2–10.5, 12.1.12 and 14.2. Apply [source precedence](../../design-sources.md). F009's schema-free variables and larger JSON-value allowance do not replace task-result rules. Its disposable pre-F009 baseline exception is not a blanket compatibility waiver for F018.

## Required behavior

### R01 — Pinned result contract

Each human node retains one explicit resultContract shared by all outcomes. FORBIDDEN requires absence of both a submitted result and a schema. OPTIONAL requires an embedded schema and permits result omission. REQUIRED requires a schema and a supplied result. Any supplied result must be an object; top-level null fails in every mode. Nested objects, arrays and nulls obey the schema. Empty object is a supplied result and is valid only if the schema permits it.

Published definition bytes/hashes remain immutable; running instances use their pinned contract. Existing explicit FORBIDDEN definitions continue to work. Unsupported modes, fields, schema placement or schema features block definition validation/publication. Outcome, comment, empty scalar inputs and result remain distinct. OPTIONAL absence is not a request to insert a default.

### R02 — Schema and exact-value validation

Implement the already selected restricted Draft 2020-12 profile in language section 10.5: object-form schemas, closed object shapes, homogeneous arrays, supported primitive types/nullability, enum/const, declared length/count/numeric bounds, selected conditionals, discriminated closed-object oneOf, local acyclic root-$defs references, selected date/date-time/uuid assertions and supported annotations/examples. The detailed keyword and composition rules in that section are authoritative; do not silently widen or narrow them. Unsupported keywords, external references and cycles fail before publication. Schema default is unsupported, rather than an initializer.

Reject malformed JSON/Unicode, duplicate keys, lossy numeric conversion and exceeded limits. Preserve exact values through Java, HTTP, UI, storage and recovery. The selected result ceiling is 64 KiB compact serialized UTF-16, with depth 16, 100 properties per object and 1,000 elements per array. Apply the selected text/number limits and schema/definition budgets from section 14.2 as well. F009's 256 KiB value allowance does not enlarge results. C01/C04/C05 fix concrete codec, envelope, diagnostic and validator-work mappings without changing these selected limits. Limit exhaustion is distinct from ordinary schema mismatch; neither accepts a result or triggers fallback execution.

Schema numeric comparison follows mathematical value, independently of scale-sensitive command identity: for example, 1.0 must satisfy an integer schema. C04 includes this distinction in its verification vectors.

### R03 — Host validation and command ordering

For fresh completion, retain trusted context, host access, early checked aggregate write, exact pinned task/outcome checks, current eligibility and exclusive claimant completion. Validate the result structurally and invoke the focused host business-validation hook before task closure, accepted evidence or downstream effects. Supply detached workflow-owned data and exact instance/activity/task identities. The hook validates business membership, completeness and reviewed material; it must not duplicate downstream business effects. Rejection or hook failure fails the command atomically. O02 fixes the observable behavior; hook wiring and concrete error mapping are implementation details, not additional owner decisions.

Authorized successful replay precedes state-dependent validation and must not rerun the hook or effects. An administrator's release permission or a former member's ownership-only access does not confer submission access or completion rights.

### R04 — Immutable evidence and identity

Persist one authoritative accepted submission in TASK_COMPLETED.details_json. Preserve identity, actor, timestamp and outcome agreement with the task/activity. Open/cancelled tasks have no accepted submission; a completed task missing valid required evidence fails as an integrity error. Existing completion uniqueness and indexes remain authoritative; do not add duplicate storage or indexes by default.

Include the complete result in versioned request normalization. Object property order and whitespace do not affect identity; array order, missing versus explicit nested null, and changed nested values do. Under the O01 owner decision, follow F009 numeric identity recursively: preserve exact numeric value and decimal scale, so 1 and 1.00 have different command identity. Equivalent encodings producing the same BigDecimal value and scale follow F009's codec rules. Schema numeric comparison remains mathematical as specified in R02. Preserve current no-result receipt replay and strict historical evidence readers under O04. Receipts remain execution summaries; durable handoff remains metadata-only, without full results. Required evidence overflow rolls back instead of truncating evidence.

### R05 — Authorized reads and retention

Extend the existing exact-task and activity-submission APIs with detached result data. Distinguish no accepted submission from a completed submission with an omitted optional/forbidden result. Preserve exact activity identity, including historical stages; never select a submission merely by node key or an ambiguous latest activity.

Existing host exact-task access and activity per-task filtering apply to result reads and completion history details. Preserve source-cursor continuation through hidden rows and byte/item bounds without truncating individual submissions. The result does not grant new peer or sibling visibility. Under O03, extend existing APIs and permissions; concrete DTO/wire absence representation is an engineering detail.

This is a required F018 boundary, not a claim that every current shared viewer path enforces it: the default shared history-detail grant is instance-wide, while the sandbox adds exact-task checks. Enforce exact-task submission authorization before exposing a result through shared TASK_COMPLETED history details. An instance-wide grant alone must not bypass it.

Retain evidence/readers while retained tasks or operational dependencies need them. No new purge/redaction feature is introduced. Missing or exceptionally unavailable required evidence must fail affected access/execution explicitly, not look like an omitted optional result. Production retention/redaction policy remains host integration work.

### R06 — Downstream access in the same transaction

Persist the completion before downstream service traversal and expose explicit completed-activity context to actions within that traversal. Actions can use supported typed submission reads to access the final result before commit. Preserve this context through consecutive services/gateways; a later completion must identify its own activity. Use the existing submission APIs and the minimum context needed to identify that completion; start-time execution has no triggering submission. Do not add arbitrary prior-submission binding syntax or automatically copy results into variables.

A downstream failure rolls back this completion/result, activity progression, receipt and participating host effects. Earlier committed stage submissions remain unchanged. Existing published handlers remain usable under the selected compatibility contract.

### R07 — Shared UI and recovery

Expose the pinned contract to the shared task form. FORBIDDEN shows no result editor; OPTIONAL allows explicit omission; REQUIRED requires JSON object content. Provide JSON editing, bounded validation feedback and read-only accepted-result viewing. Server validation remains authoritative; a generated form engine or second browser schema validator is not required.

Keep exact numeric values and immutable captured payloads through submit, uncertain-response recovery, export/import and retry. Do not parse and reserialize result numbers through JavaScript Number. Under O03, extend the existing screen, APIs and recovery flow; transport and command-buffer representations are engineering details. Reuse existing independent read/command activation; read-only consumers need no completion or validation provider.

## Closed decisions

The owner selected the behavior and authorized finalizing routine technical details. C01–C06 settle the remaining contracts with the approved dependency upgrades below, without new modules or result storage. Provider execution and transaction behavior still require implementation verification; that is acceptance work, not an unresolved product choice.

| ID | Closed decision | Implementation |
| --- | --- | --- |
| O01 | **CLOSED — revised owner decision, 2026-11-09:** use NetworkNT JsonSchemaValidator 2.0.8 with Jackson BOM 2.22.3; retain F009 exact numeric value/scale identity. C01/C04/C05 specify validation, limits, diagnostics and normalization. Use a separate exact mathematical projection only for schema comparison, preserving submitted scale in evidence and identity. This supersedes the earlier 1.5.9 retention decision. | Migrate the existing integration and verify C01/C04/C05 plus P05 compatibility checks. |
| O02 | **CLOSED — owner decision, 2026-11-09:** (1) Core checks the JSON against the schema. (2) The host can reject it for business reasons before completion is accepted. (3) A later service action can read the accepted result. (4) If validation or that later action fails, the completion rolls back. Keep implementation simple: use the focused host validation hook and existing typed reads, with only the context needed for exact submission identity. No additional configuration system, validator registry or owner-facing interface choices are required. | Behavior settled; routine wiring belongs in P03. |
| O03 | **CLOSED — owner decision, 2026-11-09:** enter JSON in the task form; submit it with the existing completion action; show clear validation errors for invalid input; allow authorized viewing of the accepted result; recover from a failed connection using the same saved submission without changing its numbers or content. Extend the existing screen/APIs and recovery flow, preserving current access permissions. Concrete Java/HTTP representations and buffering are implementation details, not further owner choices. | Behavior settled; focused web/UI implementation belongs in P04. |
| O04 | **CLOSED — owner decision, 2026-11-09:** add structured results while preserving existing workflows, history and successful-command recovery. Existing workflows do not use this new result feature. Keep the change additive and proportional; preserve existing no-result handling and readers where shared code changes require it, without a separate compatibility framework, data conversion or broad legacy test campaign. Concrete format/version handling is routine implementation work driven by actual changed paths. | Behavior settled; focused preservation of changed shared paths belongs in P03/P05. |
| O05 | **CLOSED — owner decision, 2026-11-09:** use an approved amount and note, host amount validation, and a downstream action reading the accepted result. C06 fixes the small sandbox example; it is not APOLO policy. | Implement the C06 example and focused acceptance. |

## Concrete contracts

### C01 — Validator baseline and exact JSON

Use NetworkNT JsonSchemaValidator 2.0.8 and Jackson BOM 2.22.3 on Spring Boot 3.4.5. Set the root jackson-bom.version property to align Jackson modules. NetworkNT declares 2.22.1; 2.22.3 is selected for the identified security fixes. Boot otherwise manages 2.18.3, so upgrading the validator alone is insufficient. This override requires focused compatibility verification, not an assumption of a Boot-tested combination. Use an engine-private Draft 2020-12 SchemaRegistry, deny external schema loading, disable loose type coercion, enable lossless integer narrowing and explicitly assert the selected formats. Before provider compilation, check the section 10.5 keyword/shape allowlist and local-reference graph. Validate examples against their own schema location with references resolved from the original embedded root. Do not introduce a product-level schema registry or modify host ObjectMapper configuration. The library SchemaRegistry is private wiring. Migrate existing definition validation to the 2.x API while preserving strict parsing, bounded diagnostics, pinned definitions and offline resolution.

Reuse WorkflowJsonValue.parse/toJson for the submitted immutable value. Require an object root and at most 32,768 UTF-16 code units of its canonical text (65,536 bytes). Reuse F009 Unicode, collection, depth, property/string and numeric-token checks and exact DECIMAL(38,10) representability. Do not strip scale from the accepted carrier. Schema validation uses a separate private tree: normalize every numeric leaf in both instance and schema validation trees to an exact BigDecimal DecimalNode with insignificant zeros removed, with zero represented as zero. Verify mathematical integer/enum/const/bound comparisons against 2.0.8 without altering accepted evidence; earlier 1.5.9 inspection is not proof of upgraded behavior. Check numeric token/capacity bounds before normalization or exponent expansion; no float/double conversion. Schema numeric literals use the supported exact numeric domain; out-of-domain literals fail definition validation rather than reaching unsafe provider conversion.

In that validation-only projection, handle count/length keywords before NetworkNT's integer conversion: require nonnegative mathematical integers and clamp min/maxItems above 1,000 to 1,001, min/maxProperties above 100 to 101, and min/maxLength above 16,000 to 16,001. This preserves their meaning for engine-bounded result/example values without relying on provider integer conversion; verify the boundary vectors against 2.0.8. Do not change authored schema bytes or apply these clamps to numeric minimum/maximum. A minLength above the engine limit still rejects every permitted string; a maxLength above it adds no restriction. Include 4294967296 as an overflow vector for both minimum and maximum keywords.

Do not assume native validation-step/depth quotas in 2.0.8 or borrow capabilities from later releases. Retain the engine-owned bound and apply the language section 14.2 implementation-scope note: use a conservative engine preflight, not a claimed provider instruction counter. On the checked acyclic schema graph, memoize an expanded weight and maximum depth without materializing reference expansion. Weight counts schema objects, keyword entries and literal value nodes, including every reference occurrence and every conditional/alternative branch. Count each instance container, property entry and scalar as an input node. Saturating arithmetic rejects expanded depth above 128 or weight above 100,000 before compilation. Before each result/example validation charge weight multiplied by input-node count; reject above 100,000 per validation or 1,000,000 cumulatively per definition-validation request/runtime command. An empty root still counts as one node. Reserve the charge before calling the provider; do not reset the shared allowance between examples. This deliberately conservative engineering bound may reject an expensive schema/value combination before evaluation. It is not measured NetworkNT work; its enforcement and representative cases require focused tests. No timeouts, remote resolution or recursive schemas substitute for it.

Retain author-facing schema limits: 64 KiB UTF-16 per embedded schema, 512 KiB combined occurrences, 2 MiB complete definition and depth 64, with 16 alternatives per oneOf. Apply the existing metadata/text budgets. Existing publication validation reports unsupported profile features and invalid examples using definition diagnostics; no schema features are silently ignored.

### C02 — Completion and host validation

Add a nullable WorkflowJsonValue result component to CompleteWorkflowTask and TaskSubmission under core dto.runtime. Java null means omitted result; a carrier is always an object for these contracts. Preserve the existing no-result constructor for CompleteWorkflowTask as a simple delegating overload. Do not expose Jackson nodes. HTTP explicitly supplied null is invalid before conversion, so it cannot be confused with Java omission.

Use one optional core service.runtime.WorkflowTaskSubmissionValidator host extension, with validate(context, result) returning normally to accept or throwing a sanitized rejection to reject. The immutable dto.runtime.TaskSubmissionValidationContext contains the trusted WorkflowCommandContext, instanceId, definitionVersionId, activityId, taskId, nodeKey, taskType, outcomeKey and comment. The host may dispatch internally; no registration key, workflow configuration field or configurable registry is added. With no host extension, core performs schema validation only; hosts needing business rules supply the hook. Invoke a configured hook for OPTIONAL/REQUIRED contracts, including OPTIONAL omission, after schema validation and existing command checks, before acceptance/effects. FORBIDDEN completions do not invoke this new hook. The sandbox supplies the C06 rule. Exact exception/diagnostic behavior is C05.

Extend ServiceActionContext with nullable Long completedActivityId. It is absent during start traversal, identifies the activity completed by the current completion command, survives reconstruction across its consecutive services/gateways, and is not persisted as a latest-activity pointer. A subsequent completion supplies its own identity. Keep a delegating constructor for current no-context callers. Host actions use existing listActivitySubmissions/findTaskSubmission within their trusted transaction and existing host authorization; the context is not a new public read permission. Existing historical read APIs remain unchanged apart from result data. No automatic result binding or variable copying.

### C03 — Shared HTTP/UI

Extend the existing completion and complete-replay bodies with optional result, encoded as an ordinary JSON object. Retain existing paths, IDs, token header and strict field allowlists. Omission means no result; null, arrays and scalars are invalid. Parse numeric tokens exactly before any generic tree can round or strip scale. Completion and complete-replay accept at most 256 KiB actual UTF-8 request bytes, including whitespace/envelope; other command routes keep their existing limits.

Add resultContract to the existing task contract response, with mode and, for OPTIONAL/REQUIRED, its pinned schema object. Use a detached definition-schema representation, not the runtime-value carrier with its depth-16 limit. Extend submission responses with an optional result object; omit it for an accepted no-result submission. The exact-task endpoint still returns null when there is no accepted submission. Whole response null and omitted result therefore remain distinct. Existing string IDs and history/submission metadata remain unchanged.

Use the shared editor's raw text as an immutable string internally: completion buffer/export body gains optional resultJson text; absence uses the existing no-result envelope unchanged. Check that resultJson contains exactly one object, retaining numeric tokens and rejecting duplicates. At HTTP send/replay insert its validated JSON text as the result value without a JSON.parse/JSON.stringify number round trip. Export/import stores resultJson as a string. The UI may conservatively reject replacing a saved UUID with different resultJson text; recovery always sends the original frozen payload. Core still recognizes semantically equivalent formatting/property order under C04. Invalid input is never inserted as raw HTTP text.

For result viewing, retain raw response text and extract each result's exact JSON token text with bounded structural parsing; never display a result reserialized from JavaScript Number. Display/download that text read-only. Keep server-side schema validation authoritative; local checks handle JSON shape/syntax only. Blank OPTIONAL editor omits the result; blank REQUIRED editor prevents submission. All result/detail access uses existing exact-task grants, including shared history-detail routes. Activity pagination retains hidden-row continuation and enforces the 1 MiB UTF-8 limit on the final combined response, including metadata; never truncate a result or advance beyond an unreturned visible row.

### C04 — Evidence, fingerprints and capacity

For an absent result, retain existing completion-details schema v2, request normalization wf-runtime-request-v3 and its exact field set. For a present result, write TASK_COMPLETED details schema v3 containing exactly outcomeKey, comment, inputs (empty object) and result (object). Existing row references supply instance/activity/task identity and existing integrity checks still apply. Persist only canonical accepted text, never a mutable tree alias. Strict readers accept these two completion versions; unknown versions fail explicitly. No new table, column, index or migration is required.

Present-result completion uses wf-runtime-request-v4: the existing completion request fields plus the complete canonical result under result, preserving exact BigDecimal value/scale recursively. Keep the outer operation/actor identity and hash algorithm. Choose v3/v4 solely from supplied result absence/presence before definition/provider access. A receipt must match operation, actor, selected fingerprint version and full hash; a changed request, including adding/removing result, returns COMMAND_ID_CONFLICT. Preserve existing result-v3/v4/v5 readers and continue writing receipt result-v5: execution summaries are unchanged and do not duplicate result data. START/claim/release fingerprints stay unchanged. Durable TASK_COMPLETED event payload/schema stay unchanged (outcome metadata only).

Required vectors: reordered keys at any depth/whitespace compare equal; [1,2] versus [2,1], missing versus null, and 1 versus 1.00 compare different; 1.00 versus 100e-2 compare equal; 1.0 satisfies an integer schema; nested enum/const comparisons use mathematical equality even when fingerprint scale differs. These are focused changed-path checks, not a legacy migration project.

Retain current F009 ceilings: one history-details record 4 MiB in UTF-16 and UTF-8, cumulative details 16 MiB in each per command, receipt result and durable handoff 128 KiB in each, history responses 8 MiB UTF-8. Other runtime responses remain 1 MiB UTF-8. Each value and complete envelope must independently fit. Budget failure rejects/rolls back; do not truncate evidence or change unrelated capacities.

### C05 — Errors

Keep the existing runtime Failure envelope (code, message, diagnostics, correlationId). Invalid JSON, duplicate keys, wrong root/null and invalid result presence use INVALID_INPUT/400. Valid JSON violating its pinned schema uses new RESULT_SCHEMA_INVALID/422. Host rejection uses new RESULT_REJECTED/422; unexpected host validator failure uses RESULT_VALIDATION_FAILED/503, with internal causes logged only. Size/depth/count and conservative validation-work excess use existing LIMIT_EXCEEDED/422; raw HTTP request overflow remains PAYLOAD_TOO_LARGE/413. Existing authorization, conflict, downstream-action and integrity categories remain unchanged. Definition errors use existing definition validation reporting and block publication.

Supply a bounded first actionable diagnostic for schema or host rejection in the existing diagnostics array: code, path (JSON Pointer into result), schemaPath when relevant, and a sanitized message. Never include rejected values or provider exception text. Use root pointer "" for result-wide errors. Retain the existing design ceilings of 1,000 message code points, 8 KiB per diagnostic and 128 KiB complete error in both encodings; if a complete path cannot fit, omit that location and set locationOmitted true rather than truncate it. Validation-limit messages identify the limit without pretending the value merely mismatched the schema. Use fail-fast bounded collection; no requirement to enumerate every failure. Provider internal alternative-error allocation remains subject to C01 preflight and implementation tests.

### C06 — Sandbox example

Use workflow key sandbox-structured-review with START → HUMAN_TASK reviewAmount (SINGLE, existing SANDBOX_REVIEW assignment, COMPLETE outcome) → SERVICE_TASK sandbox.recordApprovedAmount → END. Existing sample capability gating, request linkage, identity and transaction adapters apply. The result contract is REQUIRED with this closed schema:

```json
{
  "type": "object",
  "properties": {
    "approvedAmount": {"type": "number", "minimum": 0},
    "note": {"type": "string", "maxLength": 500}
  },
  "required": ["approvedAmount", "note"],
  "additionalProperties": false
}
```

Example result: {"approvedAmount":1250.00,"note":"Approved within the sandbox allowance."}. The sample host hook applies the amount rule only to this pinned workflow key/node (resolved using definitionVersionId), checks the exact linked sandbox request and permits at most 10000.00 with an amount exactly representable in cents; it rejects excess/fractional-cent amounts with RESULT_REJECTED at /approvedAmount. Other sample workflows retain their existing rules; this hook adds no restrictions to them. These are fixture rules, not APOLO rules or global engine restrictions.

The no-input/no-output sample action reads the one accepted submission using completedActivityId, converts approvedAmount exactly to cents and records that integer through the existing SandboxServiceEffectStore quantity field (explicitly documented as cents for this action). The note remains immutable submission evidence. Reuse existing injected service-failure settings to fail after recording the effect and verify rollback. No new fixture table or variable mapping is needed. Implementation adds the importable sample JSON under this feature folder and the action under the existing testhost service.sandbox package.

## Acceptance scenarios

These scenarios define the selected behavior; planned verification is in plan.md. Actual check outcomes are recorded in [implementation.md](implementation.md).

| ID | Required observation | Requirements |
| --- | --- | --- |
| AC01 | FORBIDDEN/OPTIONAL/REQUIRED distinguish absence, null and empty object; unsupported schemas fail publication; existing pinned FORBIDDEN versions remain unchanged. | R01–02 |
| AC02 | Supported profile features, local references, examples and asserted formats enforce their rules; unsupported/cyclic/external schemas fail. Exact values and inclusive selected bounds survive round trips; overflow/work exhaustion fails without acceptance. | R02 |
| AC03 | Host business rejection and validation failure commit no completion/effects. Current F017 eligibility/claim restrictions still prevent accepting a result from an unauthorized actor. | R03 |
| AC04 | One immutable completion record is readable by exact task/activity. Open task, accepted omitted result and accepted object differ; malformed/missing required evidence fails. Old v2 completion remains readable. | R04–05 |
| AC05 | Reordered object keys/formatting replay; changed nested data, array order, missing/null and selected numeric-scale changes follow the finalized fingerprint contract. Existing successful no-result receipts replay without rerunning validation/effects. | R03–04 |
| AC06 | Authorized result/detail reads succeed; denied exact-task access and ownership-only access expose no result. Filtered activity pages retain valid continuation and whole submissions. | R05 |
| AC07 | A downstream action reads the final result in the same SQL transaction using exact completed-activity context. Injected downstream failure removes the current result/receipt/effects; a prior committed stage submission survives. | R06 |
| AC08 | Shared UI submits/omits results as required, displays errors and accepted data, and recovers the frozen exact payload including decimal scale. Read-only activation still works without command/validation providers. | R07 |

O01–O05 and C01–C06 establish the approved revision 3 contracts. Planned checks are in [plan.md](plan.md).
