# Implementation record — F009

## Actual state — 2026-11-06

READY revision 3; documentation only. Earlier dated entries below describe historical decisions; D17 supersedes their old-baseline compatibility/migration promises. T01 records the owner's decision and prepares spec/plan/tasks. No JSON runtime implementation is claimed. F008 remains scalar-only; its historical results and open acceptance are unchanged.

The documentation records schema-free JSON snapshots through start/service mappings, whole-value replacement, safe nested access, strict failures, full-value idempotency and retained evidence. Constitution/source precedence and relevant design/constraint/assumption/navigation documents are reconciled. Field schemas are deferred only for this execution-data slice; workflow-document and task-result validation are unchanged.

## Inspection and verification

- Inspected git status before edits (clean), repository guidance, F007/F008 contracts and existing JSON design.
- Static inspection of F001 found json_value NVARCHAR(MAX), JSON value_type support, matching-column checks and ISJSON validation already present. This does not establish runtime support or imply a migration is needed.
- PASS: reviewed the documentation diff and scope/status consistency; PowerShell checked 216 relative local link targets across 16 changed/new Markdown files with no missing targets, and checked trailing whitespace including new files. The feature folder contains exactly spec.md, plan.md, tasks.md and implementation.md. `git diff --check` passed (only ordinary LF/CRLF conversion notices).
- Unit tests: NOT RUN — documentation only.
- Build: NOT RUN — documentation only.
- Database/provider, rollback/concurrency, expression safety and browser scenarios AC01–12: NOT RUN — runtime implementation absent.
- No host, database connection or temporary server process started.

## Remaining work

**D17 owner decision — 2026-11-06 (revision 2):** removed pre-F009 Java API, definition, receipt/history/fingerprint and disposable test-data compatibility requirements. Core/test-host APIs may be replaced together; no legacy overloads/readers or dual capacity profiles required. History recording, immutable/pinned new definitions, transaction/concurrency and authorized idempotent replay remain mandatory for new executions. D15 history capacity remains, but fresh test-schema/rebaseline or migration setup replaces mandatory preserving forward migration. Updated active spec/plan/tasks and governing scoped precedence; historical evidence is retained. No data deletion/reset, code, migration, host, build or test action occurred. PASS: reviewed active compatibility removals and new-runtime guarantees; checked local link targets, Markdown-only changes, whitespace and unique/well-formed AC01–AC14 rows; git diff --check passed. Builds/tests NOT RUN for documentation only.

**D16 owner decision — 2026-11-06:** recorded the accepted researched recommendation: APOLO serializes its DTO to JSON text; a core-owned codec creates an immutable workflow-owned value, keeping Jackson internal. Export/read semantics preserve exact data and scale without mutable aliases or global mapper changes; HTTP remains ordinary JSON. Reconciled G01, AC10 and plan/tasks. Example names remain illustrative; exact signatures, typed nulls and backward-compatible scalar integration are still specification work. Documentation checks passed; no implementation, builds, tests or migrations.

**D15 capacity decision — 2026-11-06:** recorded owner-selected 256 KiB UTF-16 per JSON variable and retained 1 MiB UTF-16 current state. Specified related start/service/request/history/read envelopes and unchanged receipt/handoff/cumulative limits under delegated engineering authority. Inspected F001: history uses NVARCHAR(MAX), but ck_workflow_history_json caps UTF-16 at 262,144 bytes. Planned a history-only forward expansion to 4,194,304 bytes with ISJSON/trust preserved; no migration created or executed. Host outbox/inbox and receipt physical caps remain unchanged. Reconciled governing notes, plan/tasks and AC14, and repaired a malformed AC05/AC13 table row. PASS: local link targets and whitespace checked across all changed/new Markdown; AC01–AC14 verified unique with well-formed table rows; git diff --check passed. Capacity, migration/provider and performance checks remain NOT RUN. No runtime processes started.

**D14 owner decision — 2026-11-06:** recorded complete JSON variable-write history and actual-path/value routing evidence, without full-root duplication for leaf reads or evidence for skipped expressions. Computed paths/null states and whole-container operands require sufficient retained evidence. Reconciled R07/G03/AC11 and plan/tasks; exact encodings and bounded immutable-reference handling remain engineering work. Documentation checks passed; no implementation, build, tests or migrations.

**D13 owner decision — 2026-11-06:** recorded content-based whole-container equality using the selected JSON identity rules, including scale-sensitive nested numbers. Scalar comparisons remain native SpEL; equality does not define business materiality or require snapshot comparisons. Updated G02, plan/tasks and AC13; bounded comparison/operator integration and evidence representation remain engineering work. Documentation checks passed; no implementation, builds, tests or migrations.

**D12 owner decision — 2026-11-06:** recorded scale-preserving BigDecimal JSON data and scale-sensitive command identity, without changing scalar contracts. Reconciled R02/G02/AC09 and plan/tasks. Exact numeric parser/tree/serialization settings and exponent vectors remain engineering work; no behavior verification claimed. Documentation whitespace/local-link checks passed; no runtime code, build, tests or migrations.

**D11 owner decision — 2026-11-06:** recorded distinct command identity for omitted properties and explicit null recursively, preserving submitted shape and null array positions. Expression reads still project both missing properties and explicit null to null; existing scalar/presence contracts are unchanged. Updated G02/AC09 and plan/tasks; numeric identity remains open. Documentation checks passed; no implementation, builds, tests or migrations.

**D10 owner decision — 2026-11-06:** recorded recursive insignificant-whitespace/object-order independence for JSON command fingerprints, while preserving array order and string content. Duplicate-key rejection and legacy algorithms remain unchanged. Updated R06/G02/AC09 and plan/tasks; numeric and omitted-versus-null normalization remain open. Documentation whitespace/local-link checks passed; no implementation, build, tests or migrations.

**D09 owner decision — 2026-11-06:** recorded standard SpEL navigation as the default, preserving D02–D08 and the read-only/security boundary. Standard property/index/null-safe syntax, escaping and evaluation order are engineering specification/verification tasks rather than separate owner approval questions. Reconciled current requirements, acceptance, plan/tasks and source precedence; historical records of earlier open decisions remain dated history. Numeric normalization, evidence/budgets and API compatibility remain open. Documentation checks passed; no implementation, build, tests or migrations.

**D08 owner decision — 2026-11-06:** recorded fixed/computed string object keys, exact property-name lookup without path interpretation or coercion, and null for missing properties. Key expressions retain shared work, allowlist and actual-read controls. Updated spec/plan/tasks and AC03; null-receiver bracket behavior, evaluation order and escaping remain open. Documentation checks passed; no implementation, builds, tests or migrations.

**D07 owner decision — 2026-11-06:** recorded computed array indexes alongside fixed indexes, requiring numeric whole-number results without coercion or truncation. Whole-valued BigDecimal is supported; index expressions share work limits and actual-read evidence with the enclosing evaluation. Updated spec/plan/tasks and AC03; computed object keys, null-receiver indexing and precise range conversion remain open. Documentation checks passed; no implementation, builds, tests or migrations.

**D06 owner decision — 2026-11-06:** recorded null for out-of-range integer array reads, including empty arrays and negative indices without reverse indexing. Invalid index types remain errors; ordinary property access on a resulting null still fails. Reconciled R05/AC04, G02, plan/tasks and governing rules. Exact index conversion, computed-index syntax and null-receiver indexing remain open. Documentation whitespace/local-link checks passed; no implementation, tests, builds or migrations performed.

**D05 owner decision — 2026-11-06:** recorded explicit per-access `?.` property traversal; ordinary `.` on a null receiver fails. D04 still maps missing object properties to null. Updated spec/plan/tasks and current precedence notes, preserving historical decision records. Array/index behavior remains open. Documentation checks passed; no runtime code, build, tests or migrations changed.

**D04 owner correction — 2026-11-06:** absent properties of initialized JSON objects now evaluate as null, like explicit JSON null. This supersedes earlier strict missing-property wording and the illustrative accessor exception. Stored shape remains distinct; uninitialized variables remain errors. Further navigation through null remains open. Reconciled requirements, example, AC04, plan/tasks and governing precedence. Documentation checks passed; no code, build, tests or migration changes.

**D03 review — 2026-11-06:** recorded owner selection of BigDecimal for all JSON numeric leaves, exact parsing, unchanged scalar projections, no numeric-string coercion and native SpEL arithmetic/literals. Numeric normalization/scale remains open. Updated spec/plan/tasks only; documentation whitespace/local-link checks passed. No runtime code, migrations, build or tests were executed for this decision.

**Accessor confirmation — 2026-11-06:** expanded D02 and P01 with the owner-confirmed private JSON-view/property-read adapter approach: wrap containers, project leaves, distinguish null/missing, reject writes and retain evaluator controls. Numeric mapping and exact index/null-safe grammar remain open. No Java implementation was added. Documentation whitespace and local-link checks passed; tests/build remain NOT RUN.

**D02 review — 2026-11-06:** recorded Jackson JsonNode as the internal representation and protected read-only expression views/accessors, with no DTO reconstruction or exposed mutable nodes. Inspected RestrictedExpressionService: F008 uses inspected plans and rewritten scalar-read nodes, so conventional Spring accessor examples are explanatory, not a drop-in runtime patch. Public carriers, numeric projections and index/null-safe behavior remain open. Updated spec/plan/tasks only; no Java source, dependency or migration changes. Documentation whitespace/local-link checks passed; tests/build remain NOT RUN.

**Decision review — 2026-11-06:** recorded D01 in spec.md: reuse existing JSON type declarations, start descriptors, optional-input defaults and explicit initialization mappings; no variable defaults or nested-field schemas. Added the accepted definition fragment and reconciled P01/T02. G01 is partially resolved, not closed. Existing documentation changes were preserved; no code or migrations changed. Documentation whitespace and local-link checks were repeated; build/tests remain NOT RUN.

G01–G04 and T02 are closed by revision 3's technical annex. T03–T06 remain pending. Implementation requires a user request; readiness does not lift that instruction. No commits have been made.

## Revision 3 — Contract preparation, 2026-11-06

Inspected current scalar carriers, runtime fingerprints/canonicalization, variable/history reads, host controller/error mappings and F007/F008 fixtures. Added normative contracts.md and reconciled spec/plan/tasks. Selected one typed scalar/JSON model, scale-preserving canonicalization, inline evidence, existing detail reads and an additive history-capacity changeset. No runtime code, migration or data was changed. All runtime acceptance, unit tests, builds, database checks and browser checks are NOT RUN. No host or temporary server was started. PASS: inspected the documentation changes; checked 235 local links across 17 changed/new Markdown files, whitespace including untracked documents and all 14 acceptance rows. git diff --check passed. The folder retains four process documents plus the normative technical annex. Changes are uncommitted.

## Revision 3 handoff review — 2026-11-06

Reviewed the annex against D01–D17, WorkflowVariableEntity/RuntimeVariables, WorkflowRuntimeQueryService, RuntimeDefinitionPins, WorkflowExecutionService, RuntimeServiceProgression, expression plans/read DTOs, host response mapping and the applied schema. Corrected these implementation hazards:

- Initialization is row presence, not a stored flag or an uninitialized row; SQL NULL retains its row.
- New history version 2 must reach all pin/recovery/task-history readers, not just event writers.
- Execution-value numeric/depth/count rules do not apply indiscriminately to definition/protocol envelopes. Specified bounded raw parsing and replacement of the old 128 KiB history helper ceiling.
- Path-aware evidence retains ordered occurrences, declared root type, originating missing/null/out-of-range reads and skipped null-safe operations without evaluating computed operands for logging.
- Specified exact HTTP value shape, retained version-aware paging, fractional-index validation before range checking, work charges and context-specific error handling.

Inspected Indexer.getValueRef in the locally cached org.springframework:spring-expression:6.2.6 sources JAR. Its null-target check precedes index-child evaluation, and computed indexes use the expression root context. This is source inspection, not a runtime probe or proof of the future custom adapter. Remote source retrieval was unavailable; the pinned local source was used instead.

Reconciled plan/tasks and removed the conflicting claim that runtime codec verification must precede specification readiness. READY still means the contract is specified; all runtime acceptance remains NOT RUN. PASS: 17 changed/new Markdown files only, 235 local links, whitespace and 14 acceptance rows checked; git diff --check passed. No implementation, migration, database access, build, test or server process. Changes remain uncommitted.
