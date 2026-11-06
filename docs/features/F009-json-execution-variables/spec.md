# F009 — JSON execution variables

**Status:** READY, revision 3 — 2026-11-06. **Owner:** Workflow project owner. **Constitution:** [v5.0.0](../../constitution.md). **Authorization:** documentation only; do not implement until requested. Concrete contracts are finalized in the normative [technical annex](contracts.md); implementation and verification remain pending.

## Outcome and authority

APOLO supplies a DTO-derived JSON snapshot through explicit start/service mappings. Authors can write new conditions over existing supplied fields without a Java action per predicate or a separately maintained structure schema. New data still needs to be supplied by APOLO. Core evaluates expressions; it does not fetch or serialize host DTOs.

Authority: C03/C05–C10; CON-06–09/12/14; ASM-02–05/08/11; [source precedence](../../design-sources.md), [language design](../../architecture/workflow-definition-language-design.md) sections 10–14 and [database design](../../architecture/Workflow_Engine_Database_Design_v1.md) section 12. Build on [F007](../F007-typed-bindings-service-tasks/spec.md) and [F008](../F008-restricted-expressions-routing/spec.md), retaining their scope and acceptance gaps.

## Selected scope

**AGREED — 2026-11-06:** declared JSON variables; explicit start initialization and service-output whole-value replacement; no per-field structure schemas; stored snapshots; bounded read-only object/array navigation; missing JSON properties exposed as null (D04), with other evaluation errors remaining failures; full-value idempotency and retained evidence. JSON validity, generic limits, top-level declarations and expression restrictions remain mandatory. Workflow-document schemas and structured task-result contracts are unaffected.

Retain F008's acyclic graph with one mandatory SINGLE human node and existing start/completion commands. Include JSON in declared service inputs/outputs so a service can consume a snapshot and return its replacement. No standalone external variable-update command is selected; replacements in this slice occur through mapped service outputs.

**DEFERRED:** execution-data structure schemas, structured task submissions, multiple human stages/ALL, direct #facts/providers, delegated expression evaluation, DTO-class lookup conventions, implicit refresh/merge, nested mutation, JSON-path search/indexing and arbitrary Java serialization. Do not accept schema configuration and silently ignore it.

## Requirements

- **R01 — Declared containers, open nested content.** Declare JSON type and applicable presence/nullability at variable/input/output boundaries, without nested-field descriptors. Non-null roots are objects or arrays; primitives use scalar types. Accept new nested fields and structures within generic limits. Reject unknown top-level contract keys, duplicate JSON keys, malformed JSON/Unicode and exceeded limits. No inferred DTO schema or automatic date/string coercion.
- **R02 — Exact values and states.** Preserve scalar behavior and distinguish uninitialized variables, explicit top-level null, absent nested keys, nested null and empty collections. Explicit variable null uses SQL NULL; initialized variables cannot be cleared back to uninitialized. Preserve array order. Nested numbers retain language section 10.2's exact DECIMAL(38,10) capacity without intermediate floating-point loss; numeric strings remain strings. Expression arithmetic retains native SpEL semantics. D03 selects BigDecimal projections; D12 preserves scale through storage/evaluation and command identity. Concrete numeric codecs remain G02 work.
- **R03 — Explicit writes and snapshots.** Accept JSON start inputs/defaults and service values through explicit whole-value mappings. Preserve F007 optional-output omission and simultaneous mapping semantics. Validate generic type/presence/bounds before applying a batch. Later operations in the same command see accepted replacements. Changes to APOLO records do not refresh stored values. APOLO owns snapshot content, business validation, authorization and review-material identity; stored JSON alone proves none of these.
- **R04 — Safe nested reads.** Extend #vars with bounded object-key/array-index navigation over detached read-only data. Prohibit methods, reflection/class metadata, beans, constructors, assignment, auto-growth, mutation, collection selection/projection and application-object traversal. Keys such as class are ordinary data. Use standard SpEL navigation under D09 with the agreed JSON adaptations; retain F008's other operator restrictions.
- **R05 — Strict errors and publication.** A missing property of an initialized JSON object evaluates to Java null, just like explicit JSON null (D04); a valid integer array index outside the bounds also returns null (D06). Uninitialized workflow variables, invalid index types, incompatible operations, non-Boolean conditions and limit failures still abort the command without trying later routes/default. Preserve absence versus explicit null in stored data; no automatic insertion of null properties. Short-circuited reads do not occur. Publication checks all expressions' syntax, allowed features and declared variable roots, but cannot prove nested field existence/types without schemas. D05 selects explicit per-access null-safe property traversal; D09 selects standard null-safe bracket syntax; concrete integration verification and sanitized errors remain G02/G03 work.
- **R06 — Transaction, concurrency and replay.** Preserve the host transaction and early checked instance write. Roll back triggering task completion, replacements, participating host effects, history, receipt and handoff on failure; earlier committed commands remain. Fingerprint full supplied JSON under versioned normalization; D10 ignores insignificant whitespace and object-property order recursively, while preserving array order and string content. Successful authorized replay returns the original result without services/evaluation. No pre-F009 receipt readers or algorithms are required (D17); capture the actual final managed instance version and support replay for commands created under the new baseline.
- **R07 — Reads and evidence.** Extend existing authorized bounded reads with detached data. D14 retains complete JSON variable-write values and actual-path/value routing evidence independently of later replacements, without copying an entire root for a leaf read; do not read skipped paths for logging. Overflow fails atomically without truncation. Metadata-only delivery must not leak JSON values. APOLO selects snapshot fields appropriate for existing access/retention policies.
- **R08 — Bounds and persistence.** Use workflow_variable.json_value and scalar storage, with explicit whole-value writes. D15 capacities apply to one new baseline under D17; no legacy limit profile is required. Establish ISJSON and the 4 MiB UTF-16 history constraint through a specified fresh test schema/rebaseline or migration. Existing test rows need not survive; keep schema setup coherent and bounded. Bound parsing, copying, serialization, traversal and evidence work.
- **R09 — One coherent test baseline and host demonstration.** D17 permits breaking Java/definition/codec changes with core and test-host callers updated together, without old-data readers, adapters or compatibility fixtures. Retain scalar functionality and agreed expression semantics in the new model. New executions still use immutable published definitions, pinned versions, history and authorized idempotent replay. Demonstrate JSON start, service replacement, nested routing and authorized inspection without client numeric loss. No APOLO implementation or production compatibility waiver is included.

## D01 — Existing declaration and initialization format (AGREED, 2026-11-06)

The owner selected reuse of F007's existing format with `type: "JSON"`, without a nested-field schema or a new initialization mechanism. Variable declarations retain key/type/nullable and optional label/description. Start descriptors retain key/type/required/nullable, optional label/description and a literal default for optional inputs. Required means the input must be supplied; nullable governs explicit top-level null separately. A default applies only when an optional input is omitted, never to explicit null or invalid input. JSON defaults must satisfy the generic JSON/type/nullability/bound checks. Variables have no default field; explicit initialization supplies their values.

The following definition fragment fixes the selected notation (it is not a complete executable definition):

```json
{
  "variables": [
    { "key": "booking", "type": "JSON", "nullable": false }
  ],
  "startPolicy": {
    "inputContract": [
      { "key": "booking", "type": "JSON", "required": true, "nullable": false }
    ],
    "initialize": {
      "booking": { "source": "startInput", "key": "booking" }
    }
  }
}
```

The mapping copies the entire supplied value into the stored variable; it does not declare or validate nested fields. Retain existing F007 binding forms rather than introducing a JSON-specific mapping mechanism. This settles author-facing declaration/default/initialization format; Java carriers, serialization, new-baseline format identities and diagnostics remain open in G01–G03.

## D02 — Jackson-backed read-only expression access (AGREED, 2026-11-06)

Core represents loaded JSON internally as Jackson JsonNode trees; it does not reconstruct APOLO DTOs. The expression context exposes declared variables under #vars, with JSON containers represented through controlled read-only views/accessors. Object/array traversal returns another protected view; scalar leaves return Java values (String, Boolean, the selected exact numeric representation), and explicit JSON null returns Java null. D04 supersedes the initial missing-property error rule: an absent object property returns Java null, like explicit JSON null.

Do not expose raw mutable Jackson containers, their methods or reflective Java properties to expressions. Core owns the parsed snapshot and must prevent mutable aliases from escaping; detached public reads must not mutate evaluation/storage state. Accessors perform only permitted data reads and integrate with work limits and actual-read evidence. They do not replace F008's AST allowlist, method/type/bean restrictions or bounded execution.

This selects the internal representation and access approach, not a public JsonNode API or an unrestricted standard SpEL context. F008 currently rewrites validated scalar accesses into internal evaluation nodes; the concrete bridge to Jackson reads must preserve that execution model's safety/accounting guarantees. Direct Spring accessor registration versus equivalent controlled evaluator integration remains engineering work. D03 below settles numeric projections and D05 settles null-safe property traversal; D07 below allows computed array indexes; D08 below allows computed object keys; D09 selects standard bracket/null-safe syntax, with concrete integration remaining engineering work; D02 does not accept the earlier proposed fixed-index restriction.

**Accessor approach confirmed — 2026-11-06:** use a private JSON view around a core-owned tree, with no raw-node getter or mutation operations exposed to evaluation. A property-read adapter resolves the requested key, fails on an incompatible container, returns Java null for a missing object key or explicit JSON null (D04), projects scalar leaves and wraps nested containers in another protected view. A Spring PropertyAccessor-style adapter accepts only that view, delegates reads, reports writes unsupported and rejects attempted writes. Index access follows the same controlled-read principle, using standard SpEL key/index grammar under D09. A chain such as booking.department.code therefore returns a nested view followed by a String, leaving comparison to SpEL. The shown decimalValue conversion was illustrative and does not select numeric semantics. This is implementation guidance, not implemented code or a complete safety mechanism; retain AST enforcement, disabled reflective/method fallbacks, work accounting and actual-read evidence.

## D03 — JSON numeric projection (AGREED, 2026-11-06)

Expose every JSON numeric leaf to SpEL as Java BigDecimal, including whole numbers. Parse and retain values exactly within the existing DECIMAL(38,10) capacity; do not pass through double/float or infer the original DTO's numeric Java type. Numeric JSON strings remain String, without implicit conversion. Existing scalar LONG and DECIMAL projections remain unchanged. Numeric literals written in expressions retain native SpEL types.

Arithmetic over these projected values follows native SpEL BigDecimal behavior, including scale-dependent division rounding; this does not guarantee mathematically exact arithmetic results. D03 settles numeric projection and D12 subsequently selects scale preservation. G02 must specify exact numeric codecs consistently across initial evaluation, persistence reload and replay, without stripping scale. The earlier illustrative decimalValue call is consistent only when used on a tree parsed exactly under this contract.

## D04 — Missing JSON properties evaluate as null (AGREED, 2026-11-06)

The owner prefers omitting null-valued DTO fields to reduce payload size. Reading an absent property of an initialized JSON object returns Java null, matching an explicit JSON null. APOLO is not required to serialize null-valued properties. This supersedes the earlier missing-property failure rule in R05/D02 and the illustrative accessor's missing-key exception. Jackson MissingNode or a null result from get must be projected accordingly, without exposing sentinel objects to SpEL.

Preserve the original absent-versus-explicit-null distinction in stored/read JSON; this decision equates their expression read results, not their fingerprint representations. It does not make undeclared or uninitialized workflow variables valid, introduce false/zero/string defaults, permit incompatible operations, or select array out-of-range behavior. D05 below selects explicit per-access null-safe property navigation; ordinary dot access does not propagate null automatically. Normalization and complete navigation contracts remain open.

## D05 — Explicit null-safe property traversal (AGREED, 2026-11-06)

Use explicit SpEL `?.` for optional property traversal. An ordinary property access on a null receiver fails; it does not automatically propagate null. A null-safe property access on a null receiver returns null. D04 still governs reads on an existing JSON object: an absent property returns null, whether the property access is ordinary or null-safe. Null-safe access does not suppress incompatible non-null receiver errors or uninitialized/undeclared workflow-variable errors.

For initialized booking JSON `{}` or `{"department":null}`:

- `#vars.booking.department == null` returns true.
- `#vars.booking.department.code` fails because department resolves to null.
- `#vars.booking.department?.code` returns null; comparing it with `'SALES'` returns false.

Null safety applies at each access, not automatically to the remainder of a chain. For optional department and manager values, use `#vars.booking.department?.manager?.name`; an ordinary `.name` after a null manager still fails. If the booking variable itself is explicitly null, use `#vars.booking?.department?.code`. An uninitialized booking variable still fails before navigation. Routing conditions must return actual Booleans; a bare null result is not false. D09 selects standard explicit null-safe index syntax (`?.[]`).

## D06 — Out-of-range array reads return null (AGREED, 2026-11-06)

Reading an array with a valid integer index outside its bounds returns Java null, including a negative integer index or any index into an empty array. Negative indices do not count backward from the end. This supersedes earlier blanket invalid-index failure wording for out-of-range reads only. It does not grow or modify the array. An in-range explicit JSON null also returns null.

For `{"reviewers":[{"name":"Ana"}]}`, `#vars.booking.reviewers[0].name` returns `"Ana"`; `#vars.booking.reviewers[5]` returns null and `#vars.booking.reviewers[5]?.name` returns null. Ordinary `.name` on that null result still fails under D05. Invalid index types such as Boolean remain errors, as does indexing an incompatible non-null receiver. D07 below permits computed numeric whole-number indexes; null-receiver indexing and exact implementation of range checking/conversion remain specification work; do not narrow or wrap oversized numeric indices into a different valid index.

## D07 — Computed array indexes (AGREED, 2026-11-06)

Allow both fixed and computed array indexes. For example, `#vars.booking.reviewers[#vars.selectedIndex]?.name` reads an index supplied by a declared variable; an index expression may combine otherwise permitted operands/operators. The evaluated index must be a numeric whole number. Fractional values, strings, Booleans and null are invalid index values; do not coerce them, truncate fractions or wrap overflow. Whole-valued BigDecimal results are valid, including those projected from JSON under D03. Exact range checking/conversion must preserve D06: an out-of-range whole-number index returns null, including a negative value without reverse indexing.

Computed indexes remain inside the existing restricted expression profile and work/traversal limits; they do not permit methods, reflection, external lookups or mutable objects. Index expressions contribute their actual variable/path reads and work to the enclosing evaluation, without separate budgets. Short-circuited expressions are not evaluated. Publication must inspect index subexpressions even when runtime execution may skip them. Exact AST integration, null-receiver/index-evaluation order and diagnostic contracts remain specification work. D08 below selects computed object keys separately.

## D08 — Computed object keys (AGREED, 2026-11-06)

Allow both fixed quoted object keys and computed object keys, for example `#vars.booking['department']` and `#vars.booking[#vars.selectedField]`. A computed key must evaluate to a String; do not coerce numbers, Booleans or null into property names. The string selects one exact, case-sensitive JSON property without trimming, normalization or path interpretation. A key of `department.code` names that literal property, not two nested accesses. A missing property returns null under D04, and keys such as class remain ordinary JSON data rather than Java metadata.

Key subexpressions use only the permitted expression profile and share the enclosing evaluation's work limits and actual-read evidence. Inspect them at publication even if execution may skip them. No method invocation, reflection, mutation or external lookup is enabled. D09 selects standard null-safe bracket behavior, key-subexpression evaluation order and escaping; concrete accessor/AST integration and verification remain engineering work.

## D09 — Standard SpEL navigation by default (AGREED, 2026-11-06)

Use standard SpEL property and bracket navigation over the read-only JSON model, preserving D02–D08. Do not require separate owner approval for ordinary navigation syntax or invent a second navigation language. Support property names, quoted/escaped keys, fixed/computed indices and keys, and explicit null-safe property/index access using standard SpEL notation (`?.` and `?.[]`). Ordinary access on a null receiver fails; a null-safe operation returns null for that operation, without making the rest of the chain implicitly null-safe. For example, `#vars.booking.reviewers?.[0]?.name` handles a missing/null reviewers value and an absent first item under the agreed rules.

The JSON adaptations take precedence where deliberately selected: missing object properties and out-of-range array indexes return null, JSON numbers project to BigDecimal, array indexes must be numeric whole numbers and object keys must be strings. Preserve uninitialized-variable errors and existing scalar behavior. This does not enable general methods, reflection, beans/types, constructors, mutation, auto-growth, collection selection/projection or application-object access. Standard navigation is not unrestricted SpEL execution.

Routine grammar, escaping, evaluation order and AST/accessor integration are engineering specification/verification work against the selected Spring dependency, not further syntax approval gates. Follow native navigation evaluation order unless it conflicts with an agreed JSON rule; document and raise any such material conflict before dependent code. Validate syntax, restrictions, work and actual-read accounting, including skipped index/key subexpressions. Questions for the owner should concern observable data/behavior/compatibility tradeoffs rather than re-approving standard language features. No implementation is authorized.

## D10 — Formatting and object order in command identity (AGREED, 2026-11-06)

For JSON values included in command fingerprints, ignore insignificant JSON whitespace and object-property order at every nesting level. Preserve array order: arrays with the same elements in different positions are different inputs. Preserve string content exactly; whitespace inside strings is data, not formatting. This decision applies to JSON execution-data normalization, not a requirement to retain pre-F009 receipt algorithms (D17). Newly created published definitions remain immutable.

For example, `{"amount":100,"country":"RO"}` and `{ "country": "RO", "amount": 100 }` have the same JSON input identity. `["Ana","Dan"]` and `["Dan","Ana"]` do not. Duplicate keys remain invalid and must be rejected before normalization, not resolved by sorting or last-value wins. D11 below settles missing-property versus explicit-null identity as distinct; D12 selects scale preservation. Exact numeric codecs remain engineering work. Implement the selected rules through a versioned fingerprint contract for the new baseline; no pre-F009 readers are required (D17).

## D11 — Omitted properties and explicit null have distinct identity (AGREED, 2026-11-06)

Preserve omitted JSON properties and explicit JSON null properties as distinct inputs in command fingerprints at every nesting level. Thus `{}` and `{"department":null}` are different command inputs even though reading department yields null for both under D04. Do not strip null properties or insert missing ones during normalization. Preserve null array entries and their positions; do not remove or compact them.

A retry using the same command identifier must preserve the original submitted structure under the selected normalization rules. Changing omitted-versus-null content causes an idempotency conflict; D10 still allows insignificant formatting and object-order differences. This decision concerns JSON execution data and does not change existing top-level input/default/presence rules, expression read semantics. Pre-F009 scalar fingerprints need not remain supported (D17). D12 below selects scale-sensitive numeric command identity.

## D12 — Preserve JSON decimal scale (AGREED, 2026-11-06)

Preserve the decimal scale of accepted JSON numeric values through exact BigDecimal parsing, persistence, public round trips, expression projection and retained evidence. Do not strip trailing fractional zeros or rescale values as a normalization shortcut. Thus `{"amount":1}` and `{"amount":1.00}` are distinct command inputs: their numeric magnitudes agree, but their BigDecimal scales differ. Include both exact value and scale in the versioned JSON fingerprint representation, recursively, without requiring pre-F009 scalar fingerprint algorithms to remain supported (D17).

This preserves scale-dependent native SpEL arithmetic across initial evaluation and reload; numerically equal values with different scales must not collide in command identity. Capacity validation must not mutate accepted scale: existing exact DECIMAL(38,10) representability limits still apply to the value, and harmless trailing fractional zeros remain acceptable within token/resource limits. JSON numeric strings remain strings. This decision does not require byte-for-byte retention of numeric source tokens; exponent spelling and equivalent encodings that produce the same BigDecimal value and scale are codec-contract work. Do not silently change scale when serializing exponent-form values. C02 specifies parser/tree behavior, storage serialization and fingerprint vectors for readiness; their executable verification is required during implementation before acceptance.

## D13 — JSON container equality by content (AGREED, 2026-11-06)

Whole JSON objects/arrays compare by content, never by wrapper or Java reference identity. For `==` and `!=` between JSON containers, compare recursively using the selected JSON identity rules: object-property order is irrelevant; array order and length matter; missing properties differ from explicit null; numeric value and decimal scale both matter; strings preserve exact content. Different container kinds are unequal. Equal snapshots loaded or wrapped separately compare equal. Scalar numeric comparisons continue to use native SpEL semantics; this container rule does not replace scalar comparison behavior. In particular, containers containing 1 versus 1.00 are unequal even though those numeric leaves compare equal numerically when read and compared directly.

This operation supports optional whole-snapshot comparisons; it is not required for field-based routing and does not determine business materiality or automatically trigger reapproval. Comparisons must be read-only, bounded and charged to the enclosing expression's work budget. Work exhaustion remains an error, not inequality. Retain sufficient evidence for container operands actually compared; exact trace representation is G03 work. Comparison must not fetch skipped expressions' data or depend on mutable Jackson nodes escaping core. Exact container/scalar mismatch handling and operator integration remain engineering contracts; do not add ordering/arithmetic over containers by implication.

## D14 — Actual-path routing evidence and complete writes (AGREED, 2026-11-06)

Retain complete JSON values in the required variable-write history. For routing, record the actual resolved JSON paths and values used by evaluated conditions, alongside their results and selected route; do not copy the whole root snapshot merely because one nested leaf was read. For `#vars.booking.amount > 10000`, evidence includes the booking.amount value and the condition result. Historical routing must remain explainable after later variable replacement.

Computed key/index operands contribute their own actual reads; retain the resolved target path, not just the expression text. Do not read or synthesize evidence for skipped conditions or subexpressions. Navigating intermediate containers to reach a leaf does not by itself require copying those complete containers into routing history. Container values consumed as operands (including D13 whole-object comparisons) require sufficient retained evidence for those operands; exact inline-versus-immutable-write-reference encoding remains G03 engineering work. References, if selected, must remain resolvable under operational retention and cannot point only to mutable current variables.

Null results from missing properties/out-of-range indexes and null-safe traversal must retain enough location/state evidence to explain the actual read; exact path/state encoding is G03 work. Preserve decimal scale and stored shape in retained values. Required evidence must fit applicable bounds or fail atomically, without truncation. Keep full values out of metadata-only external delivery. Existing host authorization and retention requirements apply; this decision adds no public data access or retention deletion policy.

## D15 — JSON capacity and related envelopes (AGREED/delegated engineering, 2026-11-06)

The owner selected **256 KiB UTF-16 per JSON variable** and retained **1 MiB UTF-16 for all initialized current variables per instance**, including names, scalars and explicit nulls. The following related limits are engineering selections under the owner's instruction to reconcile the complete data path. They are specified, not implemented or load-tested.

Measure values/envelopes using the exact compact serialization selected for persistence, preserving numeric scale. KiB = 1,024 bytes; MiB = 1,048,576 bytes. Limits are inclusive. Measure actual UTF-8 transport separately. All applicable limits must pass; do not assume multiplying a per-value allowance gives an aggregate allowance.

| Resource in the new F009 baseline | Limit | Scope |
| --- | --- | --- |
| Each JSON variable, JSON start input/default or JSON service argument/output | 256 KiB UTF-16 | Each complete object/array value. This does not enlarge scalar values or structured task results. |
| Resulting current-variable projection | 1 MiB UTF-16 | Complete object of initialized names/values, after preparing all replacements; do not count old and new versions together here. |
| Complete start-input object after defaults | 1 MiB UTF-16 and 2 MiB UTF-8 | Names and all supplied/defaulted values, including unmapped inputs. |
| Complete service argument map / output map | 1 MiB UTF-16 and 2 MiB UTF-8 each | Includes names and unmapped outputs. Validate arguments before invocation and outputs before accepting mappings. |
| Complete start/start-replay HTTP request | 2 MiB UTF-8 | Stream-count actual request bytes, including whitespace/envelope; reject excess before unbounded parsing. No general increase for completion/cancel/admin endpoints or definition uploads. |
| Each complete history details record | 4 MiB UTF-16 and 4 MiB UTF-8 | Includes all required evidence and metadata; applies to new F009 history serialization. |
| Cumulative history details per command | 16 MiB in each of UTF-16 and UTF-8 | Unchanged; add every serialized record, including repeated snapshots. |
| History read response | 8 MiB UTF-8 | Complete serialized response including envelope, escaped details and continuation metadata; byte- and item-paginated. |
| Other runtime read responses, including variable reads | 1 MiB UTF-8 | Unchanged; reduce page item count to fit, never truncate a value. |
| Command receipt result / serialized durable handoff | 128 KiB in each of UTF-16 and UTF-8 | Unchanged. Receipt results retain execution summaries, not JSON snapshots. Handoff remains metadata-only. |

Retain existing 6,000-event, 50-automatic-step, 500-expression-evaluation, per-evaluation work, depth-16, 100-properties-per-object and 1,000-items-per-array limits. Retain other selected scalar/string/token/declaration limits and the 2 MiB UTF-16 definition/4 MiB UTF-8 upload limits. A large DTO must satisfy collection/depth bounds as well as bytes. Do not raise CPU/work limits merely because byte capacity increased.

### Capacity rationale and failure behavior

Start/service maps accommodate multiple 256 KiB values subject to their 1 MiB aggregate, including names. Four values each exactly 256 KiB cannot all fit in the current-variable object because keys/punctuation also count. A full replacement may retain nearly 1 MiB of old state and 1 MiB of new state; 4 MiB history records provide envelope headroom without splitting F007 mapping batches. A service-success event can likewise retain both bounded argument and output maps. One 256 KiB value plus its prior value must work through start/service/write/read paths when all other limits pass.

History's dual 4 MiB limits and the 8 MiB read allowance provide room for a complete individual record plus read-envelope/JSON-string escaping. Variable-read pagination retains 1 MiB responses, enough for one 256 KiB UTF-16 value plus its ordinary metadata under the chosen codec. Verify both claims against worst-case Unicode/escaping and the actual codecs; never truncate or commit records that cannot subsequently be returned by the supported history API. A page may contain one large record and must report continuation when more remain. Reject an individually unreturnable record before commit; pre-F009 records need not be retained/read under D17; new-baseline records must never be returned as truncated successes.

Repeated container reads or many replacements may still exhaust per-record/cumulative evidence limits. Fail atomically with the existing limit-failure category; do not drop values, split mapping facts, retry automatically or raise budgets dynamically. These are finite capacities, not a guarantee that every combination of maximum-sized inputs can execute all 50 automatic steps.

### New test baseline, persistence and transport

D17 applies these capacities to one new baseline supporting scalars and JSON. Remove dual legacy/JSON-profile branches and old receipt dispatch. Keep the 2 MiB start/start-replay ingress bound and replay-before-state-dependent-execution ordering for new commands. History reads use the 8 MiB envelope; other selected endpoint/read limits remain as listed. No client-requested limit overrides.

The current wf.workflow_history.details_json column is NVARCHAR(MAX), while its old check constraint limits UTF-16 storage to 262,144 bytes. The F009 baseline must instead enforce ISJSON and a 4,194,304-byte DATALENGTH ceiling with a trusted constraint. Under D17, a preserving forward migration is optional, not required: specify a fresh test-schema baseline/rebaseline or a migration before execution, keeping Liquibase tracking consistent. No old-row conversion or shrinking rollback path is required.

No JSON-variable column migration is needed for this capacity change. Receipt and host outbox/inbox SQL ceilings remain unchanged (256 KiB UTF-16 physical, with the stricter 128 KiB runtime bounds). APOLO remains production migration owner. Final schema setup and format IDs are implementation planning details to finalize before code; no migration is created or run now. Hosts must align relevant ingress/proxy limits with 2 MiB start requests and 8 MiB history responses; unrelated global limits must not be changed by core.

### Required capacity checks

Verify inclusive per-value and aggregate boundaries plus one-unit overflow; start inputs/defaults and mapped/unmapped service outputs; exact BigDecimal scale and worst-case Unicode escaping; old/new full replacement batches; complete service-success evidence; history summary pagination and maximum-sized detail responses; variable pages with maximum values; cumulative 16 MiB overflow and atomic rollback; new-baseline scalar/JSON behavior and replay; and schema setup with ISJSON enforcement and trusted constraints. Unit/build success does not prove SQL/provider or host/proxy behavior. All checks are currently NOT RUN.

## D16 — Immutable workflow-owned Java JSON value (AGREED, 2026-11-06)

Use JSON text at the Java integration boundary, parsed by a core-owned codec into a small immutable workflow-owned JSON value. APOLO serializes its DTO using its own configured mapper, including its null-inclusion and domain serialization choices. Core does not accept arbitrary DTOs for serialization or reconstruct APOLO types. Keep Jackson trees, parser types and mutable containers internal; do not expose JsonNode or a mutable Map/List graph as the public JSON carrier.

The codec parses exactly and enforces generic JSON/type/resource rules, duplicate-key rejection and decimal-scale preservation under D01–D15. This is not field-schema validation. The immutable carrier may retain a private, exclusively owned Jackson representation to avoid repeated parsing during evaluation; no accessor may leak mutable aliases. Public reads return detached immutable workflow-owned values with a JSON-text export. Keep the carrier small rather than reproducing Jackson's navigation API. Define explicit top-level-null representation consistently with existing typed-null contracts before finalizing signatures.

Illustrative usage (names are not final API signatures):

```java
String json = apoloObjectMapper.writeValueAsString(bookingDto);
WorkflowJsonValue booking = workflowJsonCodec.parse(json);
String exportedJson = booking.toJson();
```

Input text is immutable but still bounded before/during parsing. Core preserves values and scale supplied at its boundary; it cannot recover precision or properties already lost by host serialization. Configure and reuse a private codec without modifying APOLO's global mapper. JSON text exported by core must preserve the agreed data/scale semantics, not necessarily the original whitespace or property order. This Java boundary does not require HTTP clients to double-encode JSON: wire values remain ordinary JSON objects/arrays, with host adapters preserving exact values through the core codec.

G01 must finalize carrier/codec names, creation and error contracts, null handling, and integration with start inputs, action arguments/outputs and authorized reads. D17 permits replacing scalar-only value types/maps directly with a coherent scalar/JSON API and updating callers together; no compatibility overloads, adapters or legacy replay readers are required. Serialization-plus-parsing overhead and any internal caching strategy require later measurement, not a new module or speculative optimization. No Java classes, APIs or runtime behavior are implemented by this decision.

## D17 — Disposable test baseline; no backward-compatibility obligation (AGREED, 2026-11-06)

F009 may directly replace Java APIs/value types, definition formats, fingerprints, receipt/history codecs and test fixtures, updating core and the test host together. Existing test definitions, instances, receipts and history do not need migration, replay or continued readability. Do not add compatibility overloads, legacy readers, multiple limit profiles or adapters solely to preserve that old test baseline. Prefer one coherent value model for scalars and JSON. This supersedes earlier F009 compatibility requirements, including D10–D16's legacy-algorithm/API/profile promises.

Scalar values remain supported with their agreed semantics; removing old Java signatures does not authorize losing scalar functionality or changing native SpEL behavior. For executions created under the new baseline, preserve immutable published definitions and pinned versions, transaction/aggregate concurrency protection, authorized full-value command replay and required durable handoff. Continue recording complete variable-write history and actual-path/value routing evidence under D14, with operational retention sufficient to explain new executions after replacements. Removing old-data compatibility does not remove history recording or idempotency going forward.

Use one new baseline for schema/codecs/capacity enforcement. D15's capacities still apply, but no separate legacy scalar profile or old fingerprint dispatch is required. The new schema must provide ISJSON validation and the 4,194,304-byte history constraint. A mandatory preserving forward migration is no longer a F009 acceptance requirement: implementation planning may select a fresh test-schema baseline/rebaseline or a simple migration, whichever is appropriate. Do not invent conversion tooling for disposable data. Finalize the schema setup route before executing it; this is not permission to alter a live applied changeset in place and leave Liquibase tracking inconsistent.

The current task remains documentation only: do not delete/reset data, edit runtime code or migration files, start hosts or run migrations now. Any later data recreation must be explicitly scoped to the intended test target and preserve unrelated data/processes; this decision establishes no production-data reset or compatibility waiver. Preserve historical implementation/verification documents as records of what was actually observed, even though their old runtime data need not survive F009.

## Illustrative use

Given a declared JSON variable booking, APOLO supplies:

```json
{"amount":12500,"department":{"code":"SALES"},"reviewers":[{"username":"ana"}]}
```

Intended conditions include `#vars.booking.amount > 10000 and #vars.booking.department.code == 'SALES'` and array access such as `#vars.booking.reviewers[0].username == 'ana'`. These demonstrate the use case, not final API or navigation grammar. Adding a nested object requires no schema. Reading an absent object property returns null (D04); reading an uninitialized workflow variable still fails. A skipped read does not occur. Updating the host booking alone leaves this snapshot unchanged.

## Readiness gates — RESOLVED

[Concrete contracts C01–C07](contracts.md) close G01 (public values/definitions), G02 (codec/evaluator), G03 (evidence/errors/envelopes) and G04 (persistence/host). This revision supersedes earlier decision narratives that describe these details as open or illustrative. D01–D17 remain controlling owner decisions. READY means specified, not implemented or proven. Implementation requires a new user request; AC01–AC14 remain NOT RUN.

## Acceptance scenarios

| ID | Required observation | Requirements |
| --- | --- | --- |
| AC01 | Objects/arrays and new nested fields are accepted without field schemas; unknown top-level contract keys fail. | R01/03 |
| AC02 | Host-record changes do not refresh snapshots; service replacement removes omitted old fields rather than merging. | R03 |
| AC03 | Property/array conditions select the first true route; false allows later/default routing. Fixed and computed whole-number indexes follow D06/D07, including BigDecimal values; fractional, string, Boolean and null indexes fail without coercion. D08 fixed/computed string object keys select exact properties, not paths; missing keys return null and non-string keys fail. Index/key subexpressions retain allowlist, work and actual-read controls. | R04/05 |
| AC04 | An absent object property and explicit null both project to null without altering stored shape; invalid index type, bad operand, uninitialized variable and non-Boolean condition still fail when evaluated. Skipped paths are not read/recorded. Property chains follow D05 explicit per-access null safety; out-of-range integer array reads return null under D06, including empty arrays and negative indices without reverse indexing. Standard null-safe bracket access and explicit per-operation chain behavior follow D09; verify native index/key evaluation order and skipped reads. | R02/05/07 |
| AC05 | Methods/beans/types/reflection/mutation/auto-growth are rejected; JSON keys cannot expose Java metadata. | R04 |
| AC06 | Malformed, duplicate-key, oversized/deep and inexact JSON fails; accepted boundary values round trip exactly including through UI. | R01/02/08/09 |
| AC07 | Replacement is visible to the next gateway; downstream failure rolls back completion, values, host effects and evidence. | R03/06 |
| AC08 | Competing commands cannot both replace under the same instance version; result retains the actual final version. | R06 |
| AC09 | Full-value replay repeats no effect; formatting and reordered object properties at any depth retain JSON identity (D10), while reordered array elements and changed string content differ. Duplicate keys fail before normalization. Omitted properties and explicit null differ recursively (D11), so changing between them under the same command identifier conflicts; null array entries retain their positions. Numerically equal values with different decimal scales have distinct command identity under D12; reload and serialization preserve that scale. Other changed nested data conflicts under finalized normalization; new-baseline receipts replay correctly without old receipt readers. | R06/09 |
| AC10 | Reads are detached; SQL NULL differs from uninitialized; the new baseline supports scalar storage and semantics. D16 parses host-produced JSON text into immutable workflow-owned values, exposes no mutable Jackson aliases, preserves exact values/scale on export and does not modify the host mapper. HTTP values remain ordinary JSON without double encoding. | R02/07/08 |
| AC11 | D14 retains complete variable-write JSON and routing paths/values actually used, including computed-key/index reads, null/missing/out-of-range states and container operands. A leaf read does not copy the entire root; skipped reads are absent. Earlier decisions remain explainable after replacement; delivery omits values and evidence overflow rolls back. Any immutable evidence references remain resolvable under operational retention. | R07/08 |
| AC12 | Updated core/test-host callers use one scalar/JSON baseline without compatibility overloads or old-codec readers. Existing test data need not survive. New published definitions stay immutable/pinned; history and replay remain required. Unsupported schemas fail explicitly; publication does not promise nested-field existence. | R01/05/06/07/09 |
| AC13 | Separately wrapped equal containers compare equal by D13; object order is ignored, array order/length and missing/null differ, and numeric scale matters recursively. Scalar numeric comparison remains native SpEL. Comparison exhaustion fails rather than returning false; business reapproval is not inferred. | R04/05/07/08 |
| AC14 | D15's inclusive capacities accept a 256 KiB JSON value through start/service/replacement/read when all other bounds pass. One-unit overflow, excessive resulting state or cumulative evidence fails atomically. Verify complete old/new evidence, Unicode/escaping, byte pagination, new-baseline scalar/JSON behavior, fresh schema/rebaseline or migration with the selected history constraint and unchanged receipt/handoff bounds. | R01/03/06–09 |

No F009 scenario has been executed. See [plan](plan.md), [tasks](tasks.md) and [implementation record](implementation.md).
