# F007 — Typed bindings and service tasks

**Status:** IMPLEMENTED through revision 3 (batched variable-write events); revision 2 remains the historical baseline; see [implementation.md](implementation.md) for isolated verification. T09 is CLOSED by owner acceptance on 2026-11-01 after manual API/SQL observations and the owner browser walkthrough/replay confirmation. Remaining unverified coverage is explicitly retained as follow-up in implementation.md. G01–G04 remain governing contracts subject to the revision 3 B01–B04 evidence changes below. **Revision:** 3 — 2026-11-05 (revision 2 retained as the historical baseline). **Owner:** Workflow project owner. **Constitution:** [v3.0.0](../../constitution.md).

## Outcome and authority

Extend the F004/F005 minimal workflow with typed scalar start inputs, persisted scalar variables, explicit initialization and synchronous registered service actions. Demonstrate `START → SERVICE_TASK → HUMAN_TASK (SINGLE) → SERVICE_TASK → END` through the existing development host. Service effects, variable writes, task progression, history, receipt and durable handoff share the host transaction.

Authority: [language design](../../architecture/workflow-definition-language-design.md), especially sections 10.2–10.4, 12.1.1–12.1.8 and 14.2; [source precedence](../../design-sources.md); [F004](../F004-definition-management-validation/spec.md), [F005](../F005-minimal-single-workflow-execution/spec.md) and [F006](../F006-test-host-definition-console/spec.md). Apply CON-01–10/12 and the scalar compatibility boundary of CON-14. ASM-01–05/08 remain environment/host verification obligations; F007 does not establish APOLO readiness or close earlier acceptance tasks.

The user selected F007 for specification and subsequently requested implementation of T02-T10. Existing language decisions below are AGREED; revision 2 selects the bounded slice and engineering contracts for implementation under the requested gate resolution. This document does not reopen selected numeric, presence or binding semantics.

## Selected slice

Support six scalar types: STRING, LONG, DECIMAL, BOOLEAN, DATE and DATETIME. Keep exactly one START, one SINGLE human node with the existing sole outcome/empty task-input/FORBIDDEN-result contract, and one END, allowing zero or more SERVICE_TASK nodes before and after the human node. The graph is one acyclic connected chain, independently of node/transition array order. Every nonterminal node has exactly its applicable unconditional or outcome route. Existing three-node definitions remain supported unchanged.

Include scalar start descriptors/defaults, start initialization, service registration and publication checks, synchronous action execution, atomic output mappings, bounded authorized variable reads and console support. Retain existing zero-input assignment and empty/omitted guards. Reject nonempty guards explicitly; host authentication/authorization and stored assignment enforcement remain mandatory at their existing boundaries.

Excluded: SpEL evaluation, gateways, direct fact/prior-submission bindings, structured inputs/results, JSON variables, nonempty human-task inputs, multiple human stages, ALL, cancellation/reassignment, external variable-update commands, production APOLO adapters and graphical authoring. These are deferred increments, not removals from agreed V1. Existing task/submission read APIs remain available; no automatic submission-to-variable mapping is introduced.

## Required behavior

- **R01 — Validate the extended definition.** Retain F004 identity, canonical content, immutable publication and fresh capability checks. Validate all declarations, destinations, source names, type/nullability compatibility, graph reachability and action contracts before publication. Detect duplicate JSON properties before map construction; reject unknown semantic fields/source forms rather than ignoring them. Report bounded diagnostics with source locations. Published content/hash and older validation evidence are not rewritten on upgrade.
- **R02 — Decode exact scalar values.** Apply language section 10.2 throughout literals, input defaults, command values, service outputs and persistence. LONG/DECIMAL use strings at JSON boundaries, BOOLEAN uses Boolean tokens, DATE uses a valid ISO date, DATETIME requires an offset and normalizes to UTC. No coercion from arbitrary objects, floating-point loss, rounding, trimming or Unicode normalization. LONG is signed 64-bit; DECIMAL must fit DECIMAL(38,10) exactly. Reject negative zero, leading plus/zeros, exponents and whitespace in declared numeric encodings. Harmless trailing decimal/fractional-second zeros may be removed without changing value. DATETIME must be exactly representable at seven fractional digits.
- **R03 — Preserve presence and defaults.** Required and nullable are independent. Reject undeclared inputs and missing required inputs. Apply a literal default only to an absent optional input; required-plus-default is invalid. Never replace invalid input or explicit null with a default. A missing optional input without default remains absent. A missing variable is uninitialized; reading it fails even when nullable. An explicit nullable value is initialized null. No operation clears an initialized variable back to absence.
- **R04 — Initialize atomically.** Validate supplied inputs and apply defaults, then prepare every initialization mapping and validate the whole resulting variable state before applying it. Only declared destinations may be written. Host start links must exist before a service or assignment resolver needs them. All start work rolls back together on any later failure. This slice has no declared guards to run; it does not skip guards on definitions that declare them.
- **R05 — Registered typed actions.** A service action has a stable logical identifier and explicit scalar input/output descriptors (name, type, required and nullable). Publication support must correspond to an executable registration, not an advertised no-op. Reject duplicate registrations, unknown arguments/outputs and incompatible bindings. Validate runtime arguments before invocation and every returned output before mapping, including unmapped outputs. Missing required outputs or undeclared output names fail. Host upgrades retain compatible behavior for published definitions; incompatible action contracts need a distinct capability identifier.
- **R06 — Independent output mappings.** Invoke the action synchronously once per visited service node in a new successful command. Prepare all updates against the same pre-mapping variable snapshot plus validated action outputs, then apply together. Missing optional action outputs skip their mapped writes; explicit null writes only to nullable destinations. A variable source that is uninitialized fails. Reordering mappings cannot change the result. No action receives mutable variables/entities or bypasses the declared destinations.
- **R07 — Bounded progression.** Resume from the pinned current position after completion, execute service nodes and stop at the human task or END. Count START, each SERVICE_TASK visit and END as one automatic step, human entry as zero; transitions cost zero. Allow step 50 and fail before invoking step 51. Failure rolls back the current command including task completion and participating business effects; earlier committed commands remain unchanged. No automatic retry or error route.
- **R08 — Preserve the aggregate transaction.** Retain F005 MANDATORY host transactions, trusted actor/context, reentry rejection and early flushed version-checked instance claim before effects on an existing instance. Child-only updates do not protect variables. Capture the final managed lock version. Actions must join the host transaction and perform no independently committed or remote irreversible effect; external work uses the durable handoff. The host owns business validation and access.
- **R09 — Replay without repeated effects.** Resolve an authorized successful receipt before current lifecycle/token checks, capability invocation, defaults or state-dependent validation. Fingerprints cover all supplied start inputs with presence/null distinctions plus existing semantic command fields. Retain older receipt readers and deterministic compatibility with empty-input F005 calls. Changed input under the same command ID conflicts. Recovery after a race occurs only after rollback in a fresh transaction using the original request; no mutation retry loop.
- **R10 — Evidence and reads.** Persist bounded immutable initialization, service invocation/success and variable-write evidence sufficient to explain source/destination values and ordered progression, with exact command, pinned definition and node identity. Use the concrete event/receipt codecs selected in G02 below. Failed commands leave no committed success evidence. Existing TASK_COMPLETED evidence remains authoritative and unchanged. Expose detached, authorized variable projections that distinguish uninitialized from explicit null; paginate and bound bytes. Never leak unrestricted host objects, secrets or unbounded action payloads.
- **R11 — Sandbox usability.** Extend existing authenticated/CSRF-protected start and recovery flows to carry scalar inputs without JavaScript number conversion. Freeze the full original command envelope for retry/export/restore. Display variable state through public core APIs. Supply a real transactional sample action and observable rollback/replay evidence. Unsupported service graphs may show the F006 explicit graph-unavailable state while normal details/history remain usable; a new graph renderer is not required.
- **R12 — Compatibility and evidence discipline.** Preserve old definitions, command calls/receipts, task assignment, retired pins, nonselectable catalog execution and default disabled-console behavior. Add no module and no competing infrastructure. Preserve applied migrations; use existing typed variable columns without a core migration; G03 specifies the separate host-only migration. Record tests/builds/manual results separately; no production claim or closure of F005/F006 follows from this document set.

## Definition notation and value boundaries

Use the already selected JSON, without a new expression or writer field:

| Location | Supported form |
| --- | --- |
| `startPolicy.inputContract` | Array of scalar descriptors: key/type/required/nullable, optional label/description and literal default for optional inputs. |
| `variables` | Array of key/type/nullable declarations with optional label/description; no required/default/schema for scalars. |
| `startPolicy.initialize` | Destination-variable object; each source is `{source: "startInput", key: "..."}` or `{source: "literal", value: ...}`. Omitted or `{}` means none. |
| SERVICE_TASK | Common key/type/name/optional description plus required action, inputs and outputs. |
| Service `inputs` | Argument-name object; each value is `{source: "variable", key: "..."}`. |
| Service `outputs` | Destination-variable object; source is variable or actionOutput with a key. Variable sources read pre-update state. |

For example, declared LONG start input `quantity` can initialize LONG variable `quantity`; a service can bind argument `quantity` from that variable and map returned LONG `total` into declared variable `total`. The complete executable fixture and command sequence are in G04 below; neither example establishes a booking policy.

Use the selected language/storage limits together: 200 nodes/500 transitions; 50 automatic steps per command; 100-character technical keys; numeric/date/time encodings at most 128 code points; STRING at most 4,000 code points and 8,000 raw UTF-16 bytes. Start inputs including defaults fit 128 KiB serialized UTF-16; resulting initialized variables fit 1 MiB serialized UTF-16. Requests fit 256 KiB UTF-8 and read responses 1 MiB UTF-8. Definition limits remain F004/language limits. Retain F005's stricter 128 KiB per-event/per-receipt UTF-8 and UTF-16 limits, including complete new evidence. G01/G02 define additional bounded collections and invocation envelopes; all limits apply together.

Persist absence as no workflow_variable row, typed null as discriminator plus null value columns, and a scalar as discriminator plus its matching typed column; clear incompatible columns on replacement. No JSON-variable writes or new result table are implied. Validate exact representability before SQL conversion.

## Errors and acceptance

Keep malformed input, unsupported definition semantics, missing/incompatible host action, invalid action output, uninitialized read, stale state, command-ID conflict, execution limit and action failure distinguishable. Errors are sanitized and correlated. Existing categories remain compatible; exact new codes, JSON pointers and HTTP mappings are specified in G01 below.

| Scenario | Required observation | Requirements |
| --- | --- | --- |
| AC01 | Publish/start/complete an unchanged F004/F005 example, including old receipt replay after upgrade; no new variable rows or action calls. | R01/09/12 |
| AC02 | Extended chain initializes inputs, runs a pre-review action, pauses, then completion runs a post-review action and reaches END with matching variables/history/token. | R04–08/10/11 |
| AC03 | Reject unknown/duplicate fields, missing required inputs, invalid defaults, incompatible source/destination types and unsupported bindings/guards/gateways/JSON. | R01–04 |
| AC04 | Verify LONG boundaries, exact decimals, timestamp offsets/precision, Unicode byte limits and invalid encodings without rounding. | R02 |
| AC05 | Distinguish absent/defaulted/explicit-null input and absent/null/value variables through initialization, reads and mappings. | R03/04/10 |
| AC06 | Missing/duplicate/incompatible actions fail publication or runtime as appropriate; untrusted actors cannot invoke commands or read variables. | R01/05/08 |
| AC07 | Missing required/unknown/invalid outputs fail; missing optional output preserves destination; explicit null obeys nullability. | R05/06 |
| AC08 | Swap mapping order with two variable-to-variable destinations; both read original state. One invalid mapping commits none. | R06 |
| AC09 | Fail an action after a participating host write, then separately fail after valid outputs/at recorder; current command's host/core/receipt/evidence all roll back. Prior start and open task survive failed completion. | R07/08/10 |
| AC10 | Identical replay repeats no action/write/event. Changed scalar input or absent-versus-null under the same UUID conflicts. Export/restore keeps exact original payload. | R09/11 |
| AC11 | Concurrent completions and duplicate starts yield one committed result/effect; stale aggregate writes cannot change variables. Recover using fresh-transaction replay. | R08/09 |
| AC12 | Step 50 can finish or pause; step 51 is never invoked and rolls back the command. Reject cycles/disconnected graphs before publication. | R01/07 |
| AC13 | Reject oversized inputs, invocation outputs, resulting state or complete evidence without truncation/partial writes; variable paging preserves presence and exact values. | R02/10 |
| AC14 | Actual console start, pause, completion, variable/history inspection and recovery work; unsupported graph display degrades locally. Role/CSRF protections and existing UI regressions hold. | R11/12 |
| AC15 | Retirement/catalog disable after publication preserve pinned execution as in F005; unsupported action deployment fails visibly without changing pinned content. | R01/05/12 |

## G01 — Public contracts and bounded registration (RESOLVED)

### Core API

Keep StartWorkflow and CompleteWorkflowTask records unchanged. Add these overloads on WorkflowExecutionService:

- `start(StartWorkflow command, StartInputs inputs, WorkflowCommandContext context, StartLinkAction links)`; MANDATORY, with the existing nullable links/intent pair rules.
- `findReplay(StartWorkflow command, StartInputs inputs, WorkflowCommandContext context)`; read-only REQUIRED.

Existing start/findReplay overloads delegate with StartInputs.empty(); no constructor or method is removed. Completion signature and submission contract remain unchanged. WorkflowExecutionResult keeps its existing fields: start returns the newly opened human activity/task and paused instance; completion returns that same now-completed activity/task and terminal instance. Variables are queried separately, never added to old receipt snapshots.

New immutable DTOs live in dto.runtime:

- `StartInputs(Map<String, ScalarLiteral> values)`. Copy defensively, reject null map/entries. Missing key means absent. ScalarLiteral is a sealed interface with `TextLiteral(String value)`, `BooleanLiteral(boolean value)` and singleton `NullLiteral`. Reject other token kinds at transport; there is no generic Object/Jackson node in the public contract. Text remains exactly as supplied until typed validation, including numeric/time spelling for fingerprints.
- `ScalarValue(VariableValueType type, ScalarLiteral value)`. Only the six scalar types are allowed. NullLiteral carries typed null; every non-null value is validated and normalized for its type. STRING/DATE/LONG/DECIMAL/DATETIME use TextLiteral; BOOLEAN uses BooleanLiteral. No null Java reference represents absence. Factories enforce R02; accessors remain immutable. Hosts explicitly decode exact decimal strings with BigDecimal if needed.
- `ActionField(String key, VariableValueType type, boolean required, boolean nullable)` and `ServiceActionDescriptor(String identifier, List<ActionField> inputs, List<ActionField> outputs)`. Fields have no defaults or JSON type. Lists have unique keys and are copied defensively.
- `ServiceActionContext(UUID commandId, long instanceId, long definitionVersionId, String nodeKey, String businessType, String businessId, WorkflowCommandContext commandContext)`. No EntityManager, mutable variables, hidden capability lookup or arbitrary host objects. Optional correlation pair follows F005. The action may use authorized host services to resolve its business records by the existing host links.
- `ServiceActionResult(Map<String, ScalarValue> outputs)`. Key absence is an omitted output; NullLiteral is explicit typed null. Null results/maps/entries are invalid action outputs.

New host SPI in service.runtime: `WorkflowServiceAction.descriptor()` and `execute(ServiceActionContext, Map<String, ScalarValue> inputs) -> ServiceActionResult`. The immutable descriptor comes from the actual executable bean. A concrete internal ServiceActionRegistry snapshots registrations once per process, validates uniqueness/contracts and supplies both publication and execution lookup. No hot reload. No change to the existing two-field DefinitionCapabilities constructor or assignment/task capability provider is required; service checks combine that snapshot with this registry. No actions registered is valid for management/legacy-only operation; a referenced missing action fails closed. Malformed/duplicate registrations are HOST_CONFIGURATION and prevent enabling service execution/publication, never a validation-only success.

Required action arguments must have bindings. Optional arguments may omit their binding and are then absent. A bound variable must be initialized even for an optional argument. Type compatibility is exact, with no LONG-to-DECIMAL widening. Nullable sources cannot feed nonnullable destinations/arguments even if a particular runtime value is non-null. Required/optional output presence is governed by the descriptor independently of whether a node maps that output. A missing optional output skips the write. Unmapped valid outputs are allowed; unknown outputs fail. Publication checks bindings against descriptors without invoking actions.

Extended definitions use validator identifier `wf-scalar-service-validator-v1`. Include the referenced sorted action descriptors and structural-schema digest in the F004 validation-context hash, alongside existing task/resolver context. Unreferenced registrations do not change the context. Unchanged minimal definitions retain their existing validator/schema path and context rules. Stable action identifiers promise compatible behavior across deployments; incompatible replacements need a new identifier. Runtime checks the pinned declared bindings against actual support before invocation; it does not reapply catalog authoring eligibility.

### Variable read API and transport

Add `WorkflowRuntimeQueryService.listVariables(long instanceId, String afterKey, int limit, Long expectedLockVersion) -> WorkflowVariablePage(instanceId, lockVersion, items, nextAfterKey)` in a read-only REQUIRED transaction. `WorkflowVariableView(key, type, initialized, value)` includes all declared variables; value is null only when uninitialized, otherwise a ScalarValue, including typed null. Keys use case-sensitive ASCII lexical order. Default limit 50, range 1–100. afterKey is null for the first page; otherwise it must be a declared key and expectedLockVersion is mandatory. A supplied expected token must match. Read scalar instance versions before and after materializing the page, bypassing stale first-level entity snapshots; if they differ, fail STALE_VERSION. This detects a concurrent commit during a page read; subsequent pages require the returned token. It does not promise a snapshot across a later instance mutation. Unknown instance is NOT_FOUND. Byte and item limits both apply; return last emitted key when more remain, null at end, and never truncate a value. An item that cannot fit fails LIMIT_EXCEEDED.

Host endpoint: `GET /console/api/instances/{id}/variables?afterKey=...&limit=...&expectedLockVersion=...`; reject unknown/duplicate query parameters. Use ConsoleRuntimeAccess.instance before the core query, including retained stored-assignment reviewer access; preserve masked 404 for unrelated reviewers. HTTP items are `{key,type,initialized,value}`, where value is the normalized scalar JSON token (not the Java wrapper). Uninitialized and initialized-null both render value:null, distinguished by initialized. IDs/tokens are decimal strings. Response fields are instanceId, lockVersion, items, nextAfterKey. The console resets paging on a stale result and requests an explicit refresh rather than silently mixing versions.

Existing start and start-replay endpoints add optional `inputs` object; omission and `{}` mean StartInputs.empty(), while null is invalid. Other envelope fields remain unchanged. Increase only these two routes to a strict streamed 256 KiB UTF-8 body limit; retain the 16 KiB limit on other F006 command envelopes. Reject JSON number tokens, nested containers, duplicate/unknown fields and compression under the existing rules. Extend SandboxStart with inputs and preserve its four-argument Java convenience constructor with empty inputs. Core command/host-intent identity stays unchanged. Export/restore accepts existing command envelopes without inputs and preserves new supplied literal spelling; no browser parsing of LONG/DECIMAL as Number. New variable views encode exact strings. No client actor field is accepted.

### Limits and errors

Engineering bounds for this slice: at most 100 start descriptors/supplied keys, 100 variable declarations, 256 registered service actions and 100 fields per action input/output contract or mapping object. Identifiers use existing technical-key grammar, maximum 100 ASCII characters. Action arguments and output maps each fit 64 KiB in BOTH canonical UTF-8 and UTF-16, including names and values; validate before invoking the action or accepting its result. Copy only after count checks. User start values/defaults retain the 128 KiB UTF-16 bound; normalized strings fit R02 and existing storage. DATE and UTC-normalized DATETIME years must be 0001–9999; reject leap seconds or offsets beyond ±14:00. STRING preserves valid Unicode. Non-input host identifiers/trace context retain F005 bounds.

Preserve existing error categories and mappings. Add UNINITIALIZED_VARIABLE (422), INVALID_ACTION_OUTPUT (500), SERVICE_ACTION_FAILED (500). Runtime descriptor/registration failures are HOST_CONFIGURATION (500); invalid caller tokens are INVALID_INPUT (400); limit failures remain 422 and oversized HTTP bodies 413. A throwable from an action is SERVICE_ACTION_FAILED with internal cause only; no stack trace, raw values or business data in transport messages. Existing auth/not-found/concurrency categories remain unchanged. No new public retry category or automatic retry.

Use F004 DefinitionDiagnostic shape/severity/stage with JSON Pointer paths. Add codes WF_DEF_INVALID_INPUT_CONTRACT, WF_DEF_INVALID_VARIABLE, WF_DEF_INVALID_BINDING and WF_DEF_ACTION_CONTRACT for the corresponding document violations; retain WF_DEF_UNSUPPORTED_FEATURE and HOST_CAPABILITY_UNAVAILABLE for unsupported semantics/missing named actions. Provider corruption is an infrastructure error, not persisted FAILED language evidence. Diagnostics use the nearest offending declaration, mapping or action field path and F004's bounded error envelope. Schema rejection precedes semantic validation; no exhaustive static proof of variable initialization is promised. Actual uninitialized reads always fail.

## G02 — Command, receipt and evidence compatibility (RESOLVED)

### Fingerprints and snapshots

Do not normalize supplied text or apply defaults to compute request identity. Reuse F005 canonical JSON property ordering/escaping and SHA-256, preserving Unicode and exact text. START with an empty input map uses `wf-runtime-request-v1` and the EXACT existing root/request fields, whether called through a legacy or new overload. START with a nonempty map uses `wf-runtime-request-v2`: same existing root/request fields, root version changed to that literal, plus request.inputs containing raw scalar JSON tokens. COMPLETE_TASK always retains the existing v1 fingerprint. UUID remains the receipt key; attempt time/correlation remain excluded; host association digest remains included.

Receipt lookup occurs after basic bounded token/context checks but before definition lookup, defaults, deployment checks or effects. Dispatch by the receipt's stored fingerprint version. A v1 START receipt with nonempty supplied inputs conflicts; unknown algorithms fail UNSUPPORTED_VERSION. Compare actor/operation/hash before decoding the result. Do not try several hashes until one matches. Absence versus explicit default, `"12.5"` versus `"12.50"`, and equivalent timestamps with different offsets are different requests; clients must retain the original payload. This is request identity, not a change to typed numeric equality. Omitted inputs and `{}` both replay a legacy empty-input request; `{"note":null}` does not.

For new commands on an exact F004 minimal definition, keep receipt result_schema_version=1 and all old sequence/evidence behavior. For an extended definition (nonempty start contract/variables/initialization or service nodes), write result_schema_version=2 with the SAME WorkflowExecutionResult JSON fields. Version 2 relaxes only the fixed sequence/activation positions; it preserves actor, ownership, state, timestamp and pinned-identity integrity checks. A start result has firstHistorySequence=1, an ACTIVE instance, its OPEN task and active SINGLE activity; last sequence is the actual final sequence. A completion has firstHistorySequence>1, a COMPLETED instance and the sole completed task/activity. In both cases 0<first<=last, instance.lastHistorySequence=last, activationSequence is positive and <=last, and history span is at most 6,000 events. Completion activationSequence must precede firstHistorySequence; start activationSequence lies within its span. Keep strict v1 readers unchanged. Do not reread mutable current state to reconstruct replay results.

Typed output/evidence normalization: LONG canonical decimal string; DECIMAL strip insignificant fractional zeros and use plain notation, zero=`"0"`; DATE ISO date; DATETIME UTC with Z and no unnecessary fractional zeros. Boolean/null/string preserve their token meaning. Definition canonical JSON is never rewritten using these runtime normalizations.

### Ordered history and event delivery

Existing F005 event types/details retain details_schema_version=1. New event types below also use version 1 within their own event-type schema. Include the normal relational actor/time/command/instance envelope; new non-transition events carry null relational activity/task/source/target/transition fields and identify their node in details. For each event, reserve the next contiguous aggregate history sequence using the F005 claim protocol. Order maps/variable-write events lexically by destination key; graph order comes from transitions.

| New event | Exact details fields and ordering |
| --- | --- |
| START_INPUTS_ACCEPTED | `{inputs: {...}}` with validated/defaulted normalized input values, preserving absence. Emit after INSTANCE_STARTED and before initialization writes, for extended definitions only, even when inputs is empty. |
| VARIABLE_WRITTEN (legacy revision 2) | `{nodeKey,variableKey,type,source,previousInitialized,previousValue,value}`. Retained records remain readable. Revision 2 emitted one per applied variable, including same-value writes; revision 3 replaces future emission with the batch below. |
| VARIABLES_WRITTEN (revision 3) | One per nonempty applied initializer/output operation; sorted per-variable evidence and metadata-only durable payload follow B01-B04 below. Initializer nodeKey is the START key. Missing optional sources emit no write; unchanged values remain recorded. |
| SERVICE_TASK_COMPLETED | `{nodeKey,action,inputs:{...},outputs:{...}}` containing validated normalized scalar tokens, with optional omissions preserved. Emit once after action and output/mapping validation, before the nonempty VARIABLES_WRITTEN batch, then TRANSITION_TAKEN. No separate durable started/failed event; a failed command commits none of these events. |

Sequence: INSTANCE_STARTED → START_INPUTS_ACCEPTED → initialization writes → outgoing START transition → each service success/writes/outgoing transition → ACTIVITY_ENTERED → TASK_CREATED. Completion: existing TASK_COMPLETED → ACTIVITY_COMPLETED → outgoing human transition → service success/writes/transitions → INSTANCE_COMPLETED. Zero-variable/service legacy flows emit exactly F005's old eight events across their two commands. Service nodes never create human activities or tasks.

The history exposes operational material under the same authorized exact-history policy; host definitions/actions must not return credentials or data that reviewers cannot read. Full scalar service inputs/outputs belong to retained history, not external event payloads. WorkflowEvent remains eventSchemaVersion=1 with ID `instanceId:sequence`, existing envelope and payload map: new input event payload `{}`, new variable event payload `{nodeKey,variableKey}`, new service event payload `{nodeKey,action}`. No scalar values or comments enter delivery payloads. Emit one durable handoff event for every history event in order, including new types. Consumers/inbox treat eventType as an open string and preserve exact payloads.

Every complete history details JSON, receipt and serialized handoff event must fit 128 KiB in BOTH UTF-8 and UTF-16. The 64 KiB action input/output bounds do not guarantee the combined service event fits; validate the complete event and roll back if it does not. START_INPUTS_ACCEPTED including its wrapper can similarly fail the evidence bound despite valid input size. Never truncate or omit fields to fit. Limit each command to 6,000 history/handoff events and 16 MiB total serialized history details measured separately in both encodings; fail/roll back if exceeded. Deliver nothing externally before commit. No retention change is introduced; missing operational evidence remains a failure, not an empty value.

## G03 — Persistence and transactional sandbox (RESOLVED)

Read-only inspection of F001's workflow_variable CHECK constraints confirms absent/typed-null/scalar representations fit existing columns; history event_type is an open VARCHAR(60), and positive receipt schema versions are already allowed. **No core migration.** This is schema compatibility inspection, not evidence of provider writes, rollback or concurrency. Core writes still need those implementation checks.

Add one host-only append migration: `db/changelog/test-host/changes/003-console-service-effects.xml`, logicalFilePath identical, author `workflow-test-host`, changeset `f007-001-create-service-effect`. Include it after 002 in test-host-master.xml, using MSSQL preconditions with HALT. Preserve all applied files/checksums. It creates `wf_test.service_effect` with:

- id BIGINT IDENTITY PK; request_id BIGINT NOT NULL FK to wf_test.sandbox_request(id), NO ACTION; instance_id BIGINT NOT NULL CHECK >0 (no core FK).
- command_id UNIQUEIDENTIFIER NOT NULL; node_key VARCHAR(100) COLLATE Latin1_General_100_BIN2 NOT NULL; quantity BIGINT NOT NULL; actor_ref NVARCHAR(200) COLLATE Latin1_General_100_BIN2 NOT NULL; occurred_at DATETIME2(7) NOT NULL.
- Unique(command_id,node_key); index(instance_id,id). No update/delete API, defaults, seed rows or cascading deletion. This host infrastructure migration runs at ordinary host startup even with the console disabled, consistent with F006; no application effects run at startup.

Register action `sandbox.recordQuantity` only when the console is enabled and capabilities=sample (capabilities=none registers no sample action), with required nonnullable LONG input quantity and required nonnullable LONG output recordedQuantity. The action resolves the unique sandbox request linked to context.instanceId, verifies the SANDBOX_REQUEST correlation matches it and inserts one effect row with quantity/command/node/trusted actor/time. Missing/mismatched link fails SERVICE_ACTION_FAILED. Return the same quantity as recordedQuantity. Use the existing transaction-bound JdbcTemplate/datasource and MANDATORY propagation; core rollback must undo the row. Do not use upsert or suppress the unique violation as a substitute for core replay. In the acyclic chain each (command,node) can run once, and the receipt owns duplicate suppression.

Add startup-only console settings `workflow.console.fail-service-node` (optional exact technical key, empty=disabled) and `workflow.console.invalid-service-output-node` (same). Reject both configured for the same node, malformed node keys, or either nonempty setting when the console is disabled. Matching the first throws after effect insertion; matching the second returns the output map without required recordedQuantity after insertion. These settings are never client command fields, are disabled by default, and do not change the descriptor. Existing fail-event-recording remains available to fail after core history/receipt preparation. Document explicit restart/reset to normal; no automated runtime toggles or DB cleanup.

Read-only admin endpoint `GET /console/api/instances/{id}/service-effects?afterId=...&limit=...`: require admin, existing instance authorization, exact positive instance ID/nonnegative cursor and limit 1–100 (default 50); reject unknown/duplicate parameters. Return `{items,nextAfterId}` with ascending id, lookahead and byte cap as F006. Items contain exactly id, requestId, instanceId, commandId, nodeKey, quantity, actorRef, occurredAt; longs are strings, timestamps UTC. Expose the page from instance detail for administrators only; reviewers use authorized variables/history, not this host-effect endpoint. This page makes persisted effects inspectable without core entity access.

No database operation or migration is run during specification preparation. Implementation includes the migration and code; executing host/database acceptance follows AGENTS.md authorization rules. Existing data are retained.

## G04 — Complete fixture, commands and traceability (RESOLVED)

Prerequisites: normal console capabilities/failure settings; explicitly create/select SANDBOX_REVIEW and enable the existing `sandbox.singleReviewer` resolver, configured to the reviewer account. Install sandbox.recordQuantity from G03. Import the following explicit version, validate and publish through existing management endpoints. IDs are assigned by the server; never assume fixture IDs. The action represents only a sandbox quantity record, not an APOLO booking rule.

```json
{
  "schemaVersion": 1,
  "key": "sandbox.serviceReview",
  "version": 1,
  "name": "Quantity service review",
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
    {"key":"BEFORE","type":"SERVICE_TASK","name":"Record submitted quantity","action":"sandbox.recordQuantity",
     "inputs":{"quantity":{"source":"variable","key":"quantity"}},
     "outputs":{"recordedQuantity":{"source":"actionOutput","key":"recordedQuantity"}}},
    {"key":"REVIEW","type":"HUMAN_TASK","name":"Review","taskType":"SANDBOX_REVIEW",
     "assignment":{"resolver":"sandbox.singleReviewer","inputs":{}},"completion":{"policy":"SINGLE"},
     "inputContract":[],"resultContract":{"mode":"FORBIDDEN"},
     "outcomes":[{"key":"COMPLETE","label":"Complete","requiresComment":false}]},
    {"key":"AFTER","type":"SERVICE_TASK","name":"Record accepted quantity","action":"sandbox.recordQuantity",
     "inputs":{"quantity":{"source":"variable","key":"recordedQuantity"}},
     "outputs":{"recordedQuantity":{"source":"actionOutput","key":"recordedQuantity"}}},
    {"key":"DONE","type":"END","name":"Done","result":"COMPLETED"}
  ],
  "transitions": [
    {"key":"beginService","from":"BEGIN","to":"BEFORE"},
    {"key":"beginReview","from":"BEFORE","to":"REVIEW"},
    {"key":"finishReview","from":"REVIEW","to":"AFTER","outcome":"COMPLETE"},
    {"key":"finishService","from":"AFTER","to":"DONE"}
  ]
}
```

1. Admin creates a sandbox request via the existing request page. Retain returned requestId/lockVersion and published definitionVersionId exactly as strings.
2. POST `/console/api/instances/start` with an authenticated admin session/CSRF and body `{commandId:<new UUID>,definitionVersionId:<returned ID>,requestId:<returned ID>,expectedRequestVersion:<original token>,inputs:{quantity:"9007199254740993"}}`. Angle-bracket terms are placeholders for server values, not literal JSON. The actual input token is a string beyond JavaScript's safe integer range.
3. Expected paused result: current REVIEW, one open assigned task, two initialized LONG variables equal to the exact supplied value, one BEFORE effect row. History sequences: 1 INSTANCE_STARTED; 2 START_INPUTS_ACCEPTED; 3 quantity VARIABLES_WRITTEN (one entry); 4 beginService transition; 5 BEFORE SERVICE_TASK_COMPLETED; 6 recordedQuantity VARIABLES_WRITTEN (one entry); 7 beginReview transition; 8 ACTIVITY_ENTERED; 9 TASK_CREATED. Use the returned managed token; do not predict its increment.
4. Assigned reviewer POSTs `/console/api/instances/{instanceId}/tasks/{taskId}/complete` using the existing If-Match token syntax and body `{commandId:<another UUID>,outcomeKey:"COMPLETE",comment:null}`. Expected history: 10 TASK_COMPLETED; 11 ACTIVITY_COMPLETED; 12 finishReview transition; 13 AFTER SERVICE_TASK_COMPLETED; 14 recordedQuantity VARIABLES_WRITTEN (one same-value entry); 15 finishService transition; 16 INSTANCE_COMPLETED. Result is COMPLETED with the same task/activity closed, resultKey COMPLETED, and two effect rows total. Start costs two automatic steps; completion costs two.
5. Replaying the exact original start or completion returns its original result with replayed=true; receipt and effect/event counts stay unchanged. Current reads may show newer state than the replayed start snapshot. Changing quantity or original expected token under that UUID conflicts. For an old empty-input definition, omitted inputs and `{}` resolve the same v1 receipt; a nonempty map conflicts before any new action.
6. On a fresh attempt, configure fail-service-node=AFTER before completing. The failure leaves the prior start, its first effect, nine history events, original variables and open task intact; no completion receipt or second effect commits. Reset to normal and explicitly submit the original command: because the failed attempt committed no receipt, it may succeed with the original still-current token. Distinguish this deliberate user retry from successful receipt replay. Also exercise failure at BEFORE, invalid-service-output-node and fail-event-recording, recording separate evidence.

The plan's acceptance matrix maps AC01–AC15 to unit, provider and browser checks. This specification extends F004's validation subset and F005's command/evidence implementation only as explicitly listed; it leaves their historical results intact. F006 gains the listed start/read/sandbox extensions; its existing owner browser walkthrough remains historical evidence, not acceptance of F007. No open readiness gate remains. Runtime acceptance and provider verification are implementation work, not reasons to label the specification DRAFT.

### Canonical request reference vectors

These core-codec vectors use illustrative identity values, no host association and UTF-8 without a BOM or trailing newline. They are not sandbox commands. The command UUID is intentionally absent from the fingerprint input. Use these as fixed expected bytes/hashes in implementation tests, independently of the production encoder. Existing receipt/result fixtures must also remain covered.

Legacy empty input (also the new overload with an empty map):

```json
{"actorKind":"USER","actorRef":"f007.admin","operation":"START_WORKFLOW","request":{"businessId":null,"businessType":null,"definitionVersionId":"42","hostStartIntent":null},"version":"wf-runtime-request-v1"}
```

SHA-256: `1214c3e3b82d7d6af165aba1e867d06fc9ea3697b3dbcec5e61f52d57c1e69ef`.

Nonempty typed start input:

```json
{"actorKind":"USER","actorRef":"f007.admin","operation":"START_WORKFLOW","request":{"businessId":null,"businessType":null,"definitionVersionId":"42","hostStartIntent":null,"inputs":{"quantity":"9007199254740993"}},"version":"wf-runtime-request-v2"}
```

SHA-256: `929d0614b1368316890117ff578fc63c07c534ffa6c7110a4d2de26f811d3076`.

## PROPOSED follow-up - batched variable-write evidence

Historical status: **PROPOSED on 2026-11-01; selected and superseded by revision 3 on 2026-11-05.** Recorded at the owner's request on 2026-11-01. At proposal recording, the per-variable `VARIABLE_WRITTEN` contract remained authoritative; revision 3 below now governs future writes. T09 closure and historical evidence are unchanged.

Motivation: a mapping operation writing N variables currently produces N history rows and N corresponding durable events, in addition to service-completion and transition facts. Large mappings increase storage and delivery volume.

Proposal: replace those individual facts for future executions with one `VARIABLES_WRITTEN` history record and one corresponding durable event per nonempty mapping operation, covering start initialization and service output mappings. Each entry retains the variable key, declared type, binding source, previous-initialized flag, previous value and accepted new value. Retain writes whose value is unchanged, so explicit mapping execution remains observable. Do not combine writes across service nodes or commands. Variable storage continues to use one row per initialized variable; mapping preparation, atomic application, host transaction participation and instance concurrency protection remain unchanged (CON-06/07/12, ASM-02/03/04).

Illustrative history-details shape only; not a finalized wire schema:

```json
{
  "nodeKey": "BEFORE",
  "writes": [
    {
      "variableKey": "recordedQuantity",
      "type": "LONG",
      "source": {"source": "actionOutput", "key": "recordedQuantity"},
      "previousInitialized": false,
      "previousValue": null,
      "value": "25"
    }
  ]
}
```

Missing/uninitialized and explicit null must remain distinguishable; exact scalar encodings must be preserved. The full value-bearing history payload must not automatically become the durable-event payload: retain the existing privacy boundary and settle the bounded summary/reference shape separately.

Before adoption, settle history/event versioning, deterministic entry order, per-record and cumulative size limits, overflow behavior without partial writes, sequence-range effects and consumer compatibility. Existing `VARIABLE_WRITTEN` history, retained events and command receipts must remain readable/replayable without rewriting or executing effects again. Update the G02/G04 sequence examples and downstream projections/consumers only as part of an approved contract revision. No migration, runtime change or new execution scope is authorized by recording this proposal.

## Revision 3 - batched variable-write events (READY, 2026-11-05)

Owner selected implementation of the preceding proposal on 2026-11-05. This revision supersedes revision 2's per-variable write-event emission for future commands, while retaining its definitions, command APIs, request fingerprints, receipt schemas and historical acceptance. The earlier proposal is retained as history, not an open approval gate. Applies CON-06/07/12 and ASM-02/03/04.

- **B01 - One fact per operation:** each nonempty applied start-initialization or service-output mapping emits exactly one `VARIABLES_WRITTEN` history record and corresponding durable event. Missing optional sources are omitted; a mapping applying zero writes emits neither. Retain same-value writes. Never coalesce distinct operations/nodes/commands. Variable rows and prepared snapshot semantics are unchanged.
- **B02 - Evidence:** history details are exactly `{nodeKey,writes:[{variableKey,type,source,previousInitialized,previousValue,value},...]}`. Entries are unique and sorted by variable key using Java String natural ordering (technical keys are ASCII). Source descriptors and normalized scalar tokens retain revision 2 rules. All entries observe the same pre-operation state; previousInitialized distinguishes missing from typed null. The new event type starts at history payload schema 1 and durable envelope schema 1; existing types keep their versions. Durable payload is exactly `{nodeKey,writeCount}`, both strings; values, binding descriptors and variable names remain in authorized history only. Event identity remains instance ID plus history sequence.
- **B03 - Atomic bounds:** retain 100-variable/state limits, the existing 128 KiB per-record bound in both UTF-8 and UTF-16, 6,000 facts and 16 MiB cumulative evidence per command. Validate the complete batch, post-mapping state and command evidence budgets before mutating variable rows or the in-memory snapshot. Overflow fails with LIMIT_EXCEEDED and rolls back the host transaction, including earlier action effects; no truncation, splitting or fallback to individual facts. Consequence: mappings whose combined evidence exceeds 128 KiB now fail even when their former individual records would have fitted. This explicit operation bound is required by the selected one-record contract; larger batches require a separately specified capacity change.
- **B04 - Compatibility:** retain/read existing `VARIABLE_WRITTEN` records and version-1 delivery envelopes unchanged. Mixed histories across a deployment are valid. New records use contiguous actual sequence numbers; one batch occupies one position regardless of write count. Receipt result schema 2 already supports variable sequence ranges and remains unchanged; legacy schema 1 keeps strict minimal positions. Replay returns original stored snapshots without synthesizing batches or repeating effects. No migration or published-definition/hash rewrite, command fingerprint change or new execution scope.

G02 ordering now uses a single VARIABLES_WRITTEN after SERVICE_TASK_COMPLETED for a nonempty output mapping. G04 remains 1-9 at start and 10-16 at completion because each operation writes one variable; positions 3, 6 and 14 become VARIABLES_WRITTEN with one entry. A mapping with N applied writes consumes one sequence rather than N; activation/final sequence positions and receipts must derive from actual history, not fixed offsets. The original T09 evidence remains evidence of revision 2 only.

Acceptance: **AC16** multi-variable initialization/output yields one sorted batch per operation, with exact values, missing/null/unchanged evidence and independent sources. **AC17** zero-write mappings emit nothing; per-record/cumulative overflow fails before variable mutation and database checks observe complete rollback of the command. **AC18** retained legacy records/events/receipts and mixed old/new history remain readable; new multi-variable command replay preserves snapshots and repeats no effect. **AC19** privacy-preserving durable batches and console history/graph operate with actual sequence ranges; builds and focused verification are recorded separately from unperformed browser/provider checks.
