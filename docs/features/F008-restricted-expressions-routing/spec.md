# F008 — Restricted expressions and exclusive routing

**Owner correction — 2026-11-05:** expression arithmetic follows native SpEL numeric behavior, including floating-point approximation, promotion, rounding and integral overflow. Do not reject a result solely for precision loss or compare it with an exact reference calculation. Declared DECIMAL variables enter evaluation as BigDecimal; decimal literals retain native SpEL types. Native evaluation errors and resource limits still fail the command. Exact decoding, transport, persistence and accepted-submission rules remain unchanged. This supersedes earlier expression-arithmetic exactness wording and the misinterpreted numeric owner decision.

**Status:** IMPLEMENTED, revision 3 — 2026-11-05. **Owner:** Workflow project owner. **Constitution:** [v3.0.0](../../constitution.md). G01–G03 are resolved; code and permitted verification are recorded in implementation.md. T08 browser/live-delivery acceptance remains open; no production readiness claim.

## Outcome, authority and dependency

Extend [F007](../F007-typed-bindings-service-tasks/spec.md) with restricted Boolean expressions and EXCLUSIVE_GATEWAY routing. A synchronous service produces typed scalar values and the workflow chooses its next path deterministically, preserving the host transaction, pinned version, aggregate concurrency, successful-command replay, history and durable handoff.

Authority: [language design](../../architecture/workflow-definition-language-design.md), sections 5.2, 8.3–8.5, 10.2–10.4, 11.1–11.2, 12.1.9/16 and 14.2; [source precedence](../../design-sources.md); CON-01–09/11/12 and ASM-01–05/08. First-match, SpEL, strict-error and resource-bound decisions are AGREED. The owner confirmed the bounded scope below on 2026-11-05. Remaining evaluator contracts are subject to G01–G03; this slice does not redefine the full language.

Dependency baseline: F007 is IMPLEMENTED through revision 3, including batched variable-write events; its revision 2 T09 was closed by owner acceptance on 2026-11-01. F008 uses its typed variables/actions, progression, codecs and host sample, including VARIABLES_WRITTEN and retained VARIABLE_WRITTEN history/receipt compatibility. See the [F007 handover](../F007-typed-bindings-service-tasks/implementation.md) for its scoped verification and remaining gaps. F008 does not close F005/F006 acceptance or establish production readiness.

## Selected bounded slice

Support START, SERVICE_TASK, one SINGLE human node, one or more EXCLUSIVE_GATEWAY nodes and one or more END nodes. Keep one active position and an acyclic graph. Every START-to-END path must pass through the sole human node exactly once. Gateways may branch and reconverge; a merge does not synchronize parallel branches. Each command still pauses at the human task or ends with the same task/activity result shape defined in F007. Reject bypass-human paths, additional human nodes and cycles. Review loops remain broader agreed language work.

Use declared scalar #vars only initially. Reject #facts references explicitly until a declared provider/snapshot/fingerprint contract is specified; do not silently substitute an empty map. No expression bindings are added to start/service mappings. Nonempty guards, JSON navigation, structured submissions, ALL, external variable updates, no-human workflows, parallel/inclusive gateways, decision tables and graphical authoring remain outside scope.

**Owner confirmation — 2026-11-05:** retain one human stage and scalar #vars for this feature. Hosts may supply data through F007 start inputs with explicit initialization mappings, or registered service calls with explicit output-to-variable mappings. Expressions read the resulting workflow variables; they do not fetch live external data. Direct #facts support and multiple human stages are not prerequisites for F008. This selection narrows this delivery, not the broader language's agreed capabilities.

Scalar-only scope does not justify banning ordinary arithmetic or inventing another expression language. G01 must define a supported SpEL profile consistent with the governing semantics and exactness rules.

## Requirements

- **R01 — Gateway and graph validation.** Gateway nodes use only common key/type/name/optional description fields. Each outgoing transition has exactly one of nonempty condition or default:true. Reject false/null defaults, combined fields, gateway outcomes, unknown fields and legacy DECISION/unsupported gateway types. Require at least one outgoing route; a default-only gateway is valid. Retain START/service single unconditional routes and the sole human outcome route. Every node is reachable and can reach END; enforce the acyclic/mandatory-human subset structurally without predicting conditions.
- **R02 — Ordered first match.** Filter transitions by source and evaluate conditional routes in their relative top-level array order. First actual Boolean true wins; overlaps are valid. Stop immediately at a match. Consider the sole optional default only after all conditions return false, regardless of its array position. Preserve source text/order in immutable canonical definitions; diagram layout and key sorting do not determine priority.
- **R03 — Strict errors.** All-false without default fails ROUTE_NOT_FOUND. A non-Boolean result, evaluated missing/uninitialized value, invalid conversion, arithmetic/evaluation error or exhausted limit fails the command without later conditions or default. Boolean false is normal. Skipped conditions are not evaluated or recorded as false.
- **R04 — Validate all conditions.** Parse and structurally inspect every condition before publication, including routes that may never be evaluated. Check permitted roots, declared names, AST/operator profile, complexity and types where statically provable. Publication does not invoke actions or evaluate live business data. Emit located bounded diagnostics. Runtime checks remain necessary; publication does not prove initialization or every result type.
- **R05 — Exact detached values.** Decode F007 values into the finalized exact scalar representations. Preserve uninitialized versus explicit typed null. An evaluated absent-variable access fails even through null-safe syntax; short-circuited reads do not occur. Snapshot variables at gateway entry after preceding service mappings. Decode supplied values exactly and prohibit mutation; arithmetic follows native SpEL behavior. Dates/instants/Booleans/strings retain declared meaning.
- **R06 — Enforce read-only access.** Deny arbitrary roots, beans/types/constructors, reflection/class metadata, general method/function calls, assignment/increment, auto-growth, collection projection/selection, unrestricted regex and hidden time/random/system values. Inspect structure and constrain context/accessors; a restricted context alone is insufficient. Property/index access must resolve only declared scalar keys under #vars, without reflection, Map-method or arbitrary-property fallback. No nested JSON traversal.
- **R07 — Bound work.** Enforce 2,000 Unicode code points per expression, 256 parsed nodes, depth 32 and applicable navigation bound 16. Share a 500-evaluation allowance per command; each evaluation has 10,000 operations enforced by a verified local mechanism. A gateway visit costs one of F007's 50 automatic steps, independently of condition count. Charge before the next step/evaluation. Skipped conditions consume no evaluation. An unchecked timeout leaving work running is not enforcement.
- **R08 — Preserve transaction/replay.** Routing runs under F007's receipt-first protocol and early flushed instance claim. Routing failure rolls back current task completion, service effects, variables, history, receipt and handoff; earlier successful commands remain. Successful authorized replay performs no evaluation/action even if current state/deployment changed. No automatic mutation retry or default-on-error catch.
- **R09 — Explain the selected route.** Retain ordered evaluated transition IDs/results, selected transition/default flag and typed values actually read. Do not read skipped expressions' inputs for evidence. Required evidence must fit F007 event/cumulative budgets or fail atomically. Externally delivered payloads contain no scalar values.
- **R10 — Preserve version semantics.** Conditions belong to schemaVersion 1; no expressionPolicy field or host override. Preserve older definitions and fingerprints/results. Supported profile behavior must survive engine/dependency upgrades or be explicitly rejected under compatibility rules. Inspect the effective dependency before choosing an operation-counter solution; upgrade related Spring components coherently if necessary. Separate APOLO compatibility evidence remains required.
- **R11 — Deliver through existing host surfaces.** Reuse scalar entry, sandbox action/effects, variable/history inspection and command recovery. Present readable route evidence without frontend evaluation. Preserve roles/CSRF/exact numbers and graph-unavailable fallback for unsupported topologies. No new predicate-preview endpoint, fact inputs or graph editor is required.
- **R12 — Record real evidence.** Distinguish code, unit/build results and actual safety/provider/browser acceptance. Preserve F007 receipts/migrations and older feature evidence. No schema change is expected, subject to final event/reader reconciliation. Mocked tests cannot prove database rollback/concurrency or actual expression operation accounting.

## Expression and runtime contract boundary

Use condition source only from the pinned published definition, never from a runtime command. Conditional transitions use condition or default:true with no numeric priority. Reject templates and unknown AST nodes; a restricted context alone is not the security boundary.

### G01 reconciliation — 2026-11-05

The owner correction selects native SpEL arithmetic, promotion, floating-point behavior, rounding and overflow. Exact F007 input decoding/storage and typed projections remain mandatory; they do not impose lossless expression arithmetic. The profile and resource contracts below resolve T01. The implementation record preserves the earlier investigation and explicitly superseded accuracy policy as history.

Conditions must return actual Booleans. Evaluated errors or uninitialized reads fail; skipped reads do not occur. Only declared scalar #vars is available. All conditions, including skipped routes, undergo publication checks. No #facts, reflection, method calls, mutation or application-object access is enabled. Conditions/order remain pinned and immutable. No expressionPolicy field or host override is introduced.

## T01 integration contract — 2026-11-05

The following G03 contracts were selected against implemented F007 revision 3. The final revision 3 evaluator/resource contract governs the implementation; the implementation record distinguishes delivered code and actual acceptance evidence.

### Evaluator boundary

Keep Spring parser, AST and context types private to `service.expression`. `RestrictedExpressionService` is the documented core-to-core boundary for publication validation and runtime evaluation. Publication accepts source and declared `ActionField` descriptors, never live values. Runtime additionally accepts pinned definition hash, transition key, a defensively copied initialized `Map<String, ScalarValue>` snapshot and one command-local evaluation allowance shared across gateway visits. Absence from the snapshot is uninitialized; declarations are supplied separately. Each evaluation uses fresh state. Do not cache snapshots, read traces or allowances.

Immutable `dto.expression.ExpressionRead(String variableKey, ScalarValue value)` and `ExpressionEvaluation(boolean result, List<ExpressionRead> reads)` carry successful actual-read evidence. Copy collections defensively. Deduplicate repeated reads within each expression in first-access order; the same key can appear in separate evaluations. An error returns no success DTO. Detached projections are String, Long, BigDecimal, Boolean, LocalDate and Instant respectively; arithmetic/literals follow the native contract below.

### Routing event and errors

`GATEWAY_ROUTED` uses details schema version 1 and exactly these fields: `nodeKey`, `evaluations`, `selectedTransitionKey`, `usedDefault`. Each ordered evaluation is `{transitionKey,result,reads}`; each read is `{variableKey,type,value}`, with the existing normalized F007 scalar token and type discriminator. No uninitialized or skipped read is synthesized. A default-only gateway emits an empty evaluations array and `usedDefault:true`. After a first true match `usedDefault:false`; all preceding results are false and the last is true. When default is selected every evaluated result is false. Emit the routing event immediately before its `TRANSITION_TAKEN` event; both remain in the command transaction. Routing-event relational activity/task/source/target/transition fields are null, as for F007's non-transition evidence. Definition source retains expression text.

The version 1 durable `WorkflowEvent` payload contains exactly `nodeKey` and `selectedTransitionKey`. Retain ordinary envelope and ID/sequence rules. No scalar values, reads, conditions or comments enter delivery payloads. The complete details and handoff event must each fit 128 KiB in both UTF-8 and UTF-16; retain 6,000 events and cumulative 16 MiB details in each encoding per command. Evidence overflow fails `LIMIT_EXCEEDED` and rolls back, with no truncation. The 500-evaluation allowance does not guarantee evidence fits.

Add runtime categories `ROUTE_NOT_FOUND`, `EXPRESSION_EVALUATION_FAILED` and `EXPRESSION_LIMIT_EXCEEDED`, mapped to HTTP 422 by the development host. Preserve `UNINITIALIZED_VARIABLE` (422), aggregate/evidence `LIMIT_EXCEEDED` (422), access/conflict mappings and sanitized correlated error envelopes. No expression text, operands or provider exception message is returned. Parser/allowlist/declaration/type diagnostics use `INVALID_EXPRESSION`; expression structural limits use `EXPRESSION_LIMIT_EXCEEDED`. Locate both at `/transitions/{sourceIndex}/condition` with CONTRACT stage and fixed bounded messages; retain existing diagnostic count/byte limits. Invalid route combinations and graph topology retain `INVALID_GRAPH`, with the existing structural schema diagnostics for unknown fields/types.

### Definitions and retained codecs

Use a separate bundled `exclusive-routing-v1.schema.json`; preserve the bytes/digests of F004 minimal and F007 scalar-service schemas. Gateway-bearing definitions select the routing schema and validator identity `wf-exclusive-routing-validator-v1`. Include expression profile identity `wf-restricted-spel-v1` and effective Spring Expression version in validation provenance/context hash, alongside existing action/task/assignment descriptors. Earlier definitions keep their existing validator/context identities. No host-selectable expression policy is added.

Extend the internal model with gateway membership, per-key END nodes, and condition/default fields on ordered transitions. Preserve existing constructors for minimal/F007 callers. Runtime traversal returns the reached human or END node; it must no longer assume the sole model END is the destination. Each selected END supplies the terminal currentNodeKey/result. Publication checks all nodes/edges, reachability, reverse reachability, cycles and mandatory-human dominance without evaluating conditions. Pinned loading uses the same pure structural/profile checks but performs no catalog-selectability/publication writes; current host capability checks remain separate.

Gateway-bearing definitions use existing receipt result schema 2, including gateways without scalar declarations or service nodes. Keep command DTOs, start-input fingerprints and result fields unchanged. Inspection of `RuntimeJson.verifyResult` confirms schema 2 already checks bounded history spans and activation positions without a fixed transition count or END key; preserve those checks. Schema 1's fixed positions and eight-event contract remain strict. Replay remains before pinned loading/evaluation. `RuntimeDefinitionPins` retains its unchanged seven-field INSTANCE_STARTED contract. `TASK_COMPLETED` retrieval is unchanged. Authorized history returns bounded raw details; it need not reinterpret older VARIABLE_WRITTEN or VARIABLES_WRITTEN records. Continue emitting F007 revision 3 batches, never replace them with per-variable events. Existing history event_type VARCHAR(60), positive schema versions and JSON storage accept the new event without a migration; real provider behavior remains implementation verification.

### Concrete G01 finding and owner resolution

The probes show native LONG overflow, operand-scale BigDecimal division rounding and binary floating-point approximation. These are accepted native behaviors under the owner correction. NumericWork enforces work/allocation limits only; ExactArithmetic and its accuracy comparisons are removed. The earlier contrary policy is retained only in historical implementation evidence.

## Final evaluator and resource contract — revision 3

**Current numeric contract:** native SpEL values/results/types govern. With DECIMAL variables 1 and 2, division returns 0 at native scale; LONG max + 1 wraps to LONG min. Literal 0.1 + 0.2 produces native Double approximation, so equality with 0.3 is false. Decimal literals are not rewritten into BigDecimal; DECIMAL variables are constructed exactly from stored text. Native exceptions fail; primitive Infinity/NaN are native values rather than invented evaluation errors. Resource limits apply independently of arithmetic accuracy.

### G01 supported profile and types (RESOLVED)

Use the stock SpEL parser, without ParserContext/template parsing. Whitelist exact concrete classes: BooleanLiteral, NullLiteral, StringLiteral, IntLiteral, LongLiteral, RealLiteral; OpAnd, OpOr, OperatorNot; OpEQ, OpNE, OpLT, OpLE, OpGT, OpGE; OpPlus, OpMinus, OpMultiply, OpDivide, OpModulus, OperatorPower; Ternary and Elvis. OperatorBetween is allowed only with an immediate InlineList of exactly two scalar expressions; InlineList is forbidden elsewhere. Aliases/hex/suffix/parentheses use their native parsed meanings. There is no custom tokenizer, grammar or reparsing of string values. Explicitly reject OperatorMatches/regex, OperatorInstanceof/type access, references to facts/root/this/beans/functions, constructors, methods, assignment/increment, arbitrary collections, projection/selection and every unknown AST class.

The sole permitted navigation is a CompoundExpression with exactly two children: VariableReference `#vars`, followed by PropertyOrFieldReference naming one declared scalar key, or Indexer containing exactly one StringLiteral naming that key. Permit null-safe property/index forms; the #vars container always exists, so they still read the exact key and fail if uninitialized. No bare #vars, dynamic index, computed key, chained property/index or scalar metadata access. Keys with dots/hyphens use quoted indexing. A declared key literally named `class` is ordinary data through this path; it never accesses Java metadata. Replace these inspected direct accesses with internal detached scalar-read leaves, never Map/reflection/property/index fallbacks. A declaration alone does not initialize a value. Project the six scalar types as String, Long, BigDecimal, Boolean, LocalDate and Instant, with Java null only for initialized typed null. Snapshot F007 immutable ScalarValue records; record a read only when its leaf executes.

Use native same-type scalar equality/order (including lexicographic strings, Boolean order, LocalDate order and Instant order), native numeric promotion, and native null equality/order (`null < nonnull`, null equals null). Heterogeneous nonnumeric equality follows native false/true behavior; incompatible nonnull ordered comparisons fail. No string-to-number/date/Boolean coercion. Logical operands and ternary tests require actual Boolean; null is not false. Elvis retains native null/empty-string fallback. String concatenation with these known scalars/null retains native formatting; numeric/character subtraction and string repetition retain native semantics where supported, with bounds below. Temporal arithmetic is unsupported and fails. Root result must be actual Boolean, without conversion. Publication rejects statically provable non-Boolean roots/incompatible operands; unresolved conditional/null-dependent cases retain runtime checks. Validate every branch structurally even when short-circuited at runtime.

Evaluate each operand once and use the native SpEL result and type. No independent exact-reference calculation is performed. Integer division, decimal rounding, floating-point arithmetic, promotion and overflow retain native behavior. Native exceptions fail evaluation. Work and intermediate-size bounds remain enforced independently of exactness.

Golden contract cases: `false and #vars.absent > 0` returns false without reads; `true or #vars.absent > 0` returns true without reads; `#vars.absent == null` and `#vars?.['absent'] == null` fail UNINITIALIZED_VARIABLE; initialized null equals null and native null order remain valid. `#vars.n + 1 > 10` with LONG 10 is true; LONG max + 1 wraps using native semantics. Exact mixed DECIMAL addition/comparison succeeds; native rounded DECIMAL division is accepted. `true ? true : (1 / 0 > 0)` succeeds; selecting the error branch fails. `#vars['a.b']` is one read; `#vars.a.b`, `#vars[#vars.key]`, `T(java.lang.Runtime)`, constructors, methods, writes and a lower-priority unsafe condition all fail publication.

### G02 local work and compatibility (RESOLVED CONTRACT; enforcement verified during implementation)

The verified evaluator baseline is exactly Spring Expression 6.2.6. Reject another/unknown implementation version for F008 with publication diagnostic `EXPRESSION_VERSION_UNSUPPORTED`. The evaluator classifies it UNSUPPORTED, mapped to runtime UNSUPPORTED_VERSION if reached during progression; an incompatible pinned document rejected earlier by the definition reader retains its existing HOST_CONFIGURATION failure contract. Legacy definitions/replay remain independent. Re-verifying the baseline requires golden/compatibility checks, not a host property. Check default-only gateways too; unchanged AST class names do not establish compatibility.

Keep Boot 3.4.5 and coherent Spring 6.2.6; the effective dependency graph and nine isolated probes establish this readiness baseline. Do not upgrade expression alone or use global Spring settings. Parser compiler mode is OFF, auto-growth disabled. Bound source to 2,000 valid Unicode code points (at most 4,000 UTF-16 code units); reject malformed surrogates. Configure parser max source length 4,000 code units. Before recursive parsing, a linear quote-aware preflight counts delimiter nesting (maximum 32) and conservative syntax-descent markers (maximum 64 total: opening delimiters, `?`, `!`, `+`, `-` and standalone `not` outside quoted strings). Doubled quotes stay inside their native string literal. This additional resource check is not the syntax/security validator; native parsing and exact AST allowlisting remain authoritative. Reject excess before parser descent. Count all original parsed nodes (maximum 256), root depth 1 (maximum 32), before replacing direct access nodes. Navigation contains exactly one key segment, within the inherited limit 16. Catch provider parse failures with bounded sanitized diagnostics, never as false.

Cache at most 128 immutable validated plan descriptions in an access-ordered LRU. Key by pinned content hash, transition key, exact source, profile/implementation version and immutable declarations; evict eldest, never cache command data or mutable Spring nodes. Publication need not cache. Build fresh interpreted stock AST delegates from plan descriptions for each evaluation and local guards. No reflection to alter parser AST fields. Every visited original AST node costs one operation before execution (a collapsed direct read charges its original subtree size); skipped subtrees cost zero. Each evaluation permits 10,000 work units, every command 500 evaluations, charged before work. A command-local allowance is never stored on the singleton evaluator. The tested guarded-node prototype proves pre-execution accounting and short-circuit feasibility; production safety still requires full tests.

Before allocation, charge operand work: string work in UTF-16 code units (at least one per operand/result estimate); numeric add/compare/divide/remainder work as the sum of expanded decimal digit counts, multiply as their product, and power conservatively as base digits squared times max(1,abs(exponent)). Numeric intermediate precision and absolute scale each may not exceed 256; exponent magnitude at most 1,024, with conservative resulting precision/scale estimates checked before pow. Resource estimates remain bounded; no reference arithmetic is computed. String intermediates at most 8,192 UTF-16 code units; retain stricter native repetition limit 256. Use long arithmetic for allocation/cost estimates; reject overflow/over-budget before native allocation or arbitrary-precision expansion. Native evaluation errors are EVALUATION_FAILED; exceeded work/capacity is EXPRESSION_LIMIT_EXCEEDED. Neither is fallback. Static source/AST/preflight limits reject publication; value-dependent limits fail runtime. Evaluation/limit failures after earlier reads still roll back the command and produce no success event.

### G03 golden evidence and compatibility (RESOLVED)

With quantity 20, GATEWAY_ROUTED details are `{"nodeKey":"ROUTE","evaluations":[{"transitionKey":"high","result":true,"reads":[{"variableKey":"recordedQuantity","type":"LONG","value":"20"}]}],"selectedTransitionKey":"high","usedDefault":false}`. With 5, record high=false then positive=true, each reading LONG `"5"`; select positive, usedDefault=false. With 0, record both false with LONG `"0"`, select fallback, usedDefault=true. Repeated reads in one condition produce one evidence entry in first-access order. The durable payload for high is exactly `{"nodeKey":"ROUTE","selectedTransitionKey":"high"}`. These are acceptance vectors, not claimed observed runtime results. Legacy definitions/receipt vectors remain F007's retained fixtures and must run unchanged. A default-only route has evaluations=[]; failed/no-route/error commands have no committed GATEWAY_ROUTED or receipt. The integration contract above fixes the remaining DTO/storage/error/provenance obligations.

## Complete fixture

Requires F007's sandbox.recordQuantity, SANDBOX_REVIEW and sandbox.singleReviewer. The isolated definition suite validates this fixture under revision 3; guarded SQL tests exercise derived fixtures with test-owned adapters. Executing this exact fixture through the packaged sandbox browser remains acceptance work, not claimed by those checks.

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
| AC08 | Exact scalar projections and native numeric promotion, decimal rounding, floating-point results, overflow and temporal/null semantics match the profile. | R05/10 |
| AC09 | Structural/evaluation/operation/automatic-step limits hold at boundaries; skipped routes consume no evaluation. | R07 |
| AC10 | Routing failure rolls back current effects/variables/task/history/receipt/outbox and preserves earlier commands. | R08/12 |
| AC11 | Successful replay evaluates nothing; overlapping completions produce one committed route/result. | R08 |
| AC12 | Evidence includes only actual evaluations/reads in order; overflow rolls back; external payloads omit values. | R09 |
| AC13 | Invalid fields/defaults/cycles/disconnected/bypass-human graphs fail; valid branching/merging/multiple ends pass. | R01 |
| AC14 | Packaged browser shows selected route/result with correct authorization, exact input, recovery and graph fallback. | R11 |
| AC15 | Dependency/profile changes preserve golden behavior and cannot enable unsafe syntax or silently change pins. | R06/07/10 |

## Readiness gates

**Execution clarification — 2026-11-05:** DRAFT is the starting state for T01, not a prohibition on readiness work. The implementing agent may inspect dependencies/code, run isolated probes, specify contracts and complete G01–G03 within the agreed scope. Once the contracts and required readiness evidence are complete, update spec/plan to READY and continue dependent implementation without a separate status-change or implementation-start approval. Do not mark gates complete merely to proceed. A demonstrated conflict requiring a change to agreed behavior still needs the specific owner decision; continue independent work while that decision is pending. The later owner correction resolves numeric semantics; this clarification does not extend database, commit or push authorization.

- **G01 — RESOLVED:** owner corrected the policy to accept native SpEL arithmetic; the revision 3 profile, projections, numeric/type/error matrix and golden cases above complete the contract. Nine isolated dependency probes support the chosen enforcement approach; production acceptance remains T03/T07/T08.
- **G02 — RESOLVED:** current coherent dependency inspected; tested local pre-execution guard mechanism, parser-boundary probes, explicit structural/allocation/work/cache contracts above. Full production enforcement and compatibility tests remain required, not claimed complete.
- **G03 — RESOLVED:** current F007 revision 3 integration contracts and golden evidence/error/legacy vectors are specified above. Preserve variable batches and retained readers; no migration. Runtime/provider acceptance remains required.

T01–T07 implementation/checks are delivered under these contracts; actual results and T08's unperformed acceptance are recorded separately. Readiness does not imply complete acceptance.
