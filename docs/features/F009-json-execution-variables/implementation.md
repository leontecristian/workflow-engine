# Implementation record — F009

## Actual state — 2026-11-06

IMPLEMENTED revision 3, T03–T06, following the owner's implementation request on 2026-11-06. The latest implementation/verification section below is current. Earlier preparation entries remain historical, including their documentation-only authorizations and NOT RUN observations. F008's historical results and open acceptance are unchanged.

The documentation records schema-free JSON snapshots through start/service mappings, whole-value replacement, safe nested access, strict failures, full-value idempotency and retained evidence. Constitution/source precedence and relevant design/constraint/assumption/navigation documents are reconciled. Field schemas are deferred only for this execution-data slice; workflow-document and task-result validation are unchanged.

## Historical preparation: inspection and verification

- Inspected git status before edits (clean), repository guidance, F007/F008 contracts and existing JSON design.
- Static inspection of F001 found json_value NVARCHAR(MAX), JSON value_type support, matching-column checks and ISJSON validation already present. This does not establish runtime support or imply a migration is needed.
- PASS: reviewed the documentation diff and scope/status consistency; PowerShell checked 216 relative local link targets across 16 changed/new Markdown files with no missing targets, and checked trailing whitespace including new files. The feature folder contains exactly spec.md, plan.md, tasks.md and implementation.md. `git diff --check` passed (only ordinary LF/CRLF conversion notices).
- Unit tests: NOT RUN — documentation only.
- Build: NOT RUN — documentation only.
- Database/provider, rollback/concurrency, expression safety and browser scenarios AC01–12: NOT RUN — runtime implementation absent.
- No host, database connection or temporary server process started.

## Historical preparation decisions and work remaining at that time

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

## Implementation and verification — 2026-11-06

T03–T06 are implemented against READY revision 3 and C01–C07, after the owner's explicit implementation request. The initial worktree was clean at commit 4841323; no pre-existing documentation changes were overwritten. All work in this implementation remains uncommitted; no push was requested or performed.

### Components and decisions

- `dto.runtime`: `WorkflowLiteral` and `WorkflowValue` replace the scalar-only carriers; all scalar normalization/validation remains. `WorkflowJsonValue.parse(String)` is the public strict codec entry point and `toJson()` exports its immutable canonical snapshot. Jackson stays private. Start inputs retain raw scalar spelling until typed validation; actions, variables and host callers use the same typed model.
- The private execution-value codec enforces object/array roots, duplicate/Unicode/numeric/depth/count/string limits, bounded raw ingress and inclusive canonical size. BigDecimal data keeps scale; temporary normalization checks capacity only. Object keys sort by Unicode code point. Definition canonicalization is `wf-json-c14n-v2`, publication provenance uses `wf-json-variable-validator-v1`, and all new command fingerprints/results use `wf-runtime-request-v3` / result schema 3. No old-reader dispatch or data conversion exists.
- `ExecutionValueDefinitionModel` and `RuntimeValueBindings` replace the now-inaccurately named scalar helpers in their existing packages. Definition defaults/literals accept bounded JSON without field schemas; envelope/metadata validation does not interpret snapshot fields named `name`, `policy`, `schemaVersion`, etc. Typed map envelopes and routing evidence are checked incrementally before retaining oversized trees/records. Canonical output stops at its UTF-16/UTF-8 budget.
- `service.expression` extends the inspected/rebuilt AST, retaining fixed declared root selection and native restricted operators. Private detached trees support exact properties/keys, fixed/computed whole-number indexes, explicit per-operation null safety and bounded scale-sensitive container equality. Receiver/index ordering follows the selected Spring parser. No property/method resolver or mutable application object is introduced. Read evidence retains ordered path occurrences, originating null/missing/out-of-range states, skipped null-safe operations and complete consumed operands; intermediate containers are not copied merely for leaf evidence.
- Existing variable rows are explicitly replaced in `json_value`, with SQL NULL distinct from no row. The existing early aggregate claim, host transaction and final managed version capture remain authoritative. History details schema 2 is coordinated across writers, pin loading, task-created recovery, exact submissions and the console. History detail uses the annex's `{summary,detailsJson}` shape; JSON text remains exact. Receipts and metadata-only handoff retain their smaller limits.
- Added ordered changeset `003-json-history-capacity` (`workflow`) in `003-json-execution.xml`. It expands only the checked/trusted history JSON constraint to 4,194,304 bytes, preserving ISJSON and applied 001/002. No database reset, old-data conversion or production migration execution occurred.
- Test host start/start-replay stream-count 2 MiB UTF-8 and accept ordinary JSON containers. Variable values are raw JSON, never Java wrapper objects or double-encoded strings; nested numbers bypass Long string serialization. Core page sizing uses that wire shape, preserves lock-version paging, and reduces count without skipping values. History detail reads have their separate larger budget.
- The console retains raw input text through command recovery and HTTP assembly, and exact response text for variable inspection/download. History details also download without numeric parsing. `sandbox.refreshBooking` records a participating sandbox effect and returns an explicit complete replacement. The complete [booking-review fixture](booking-review.json) validates as a definition and supplies the documented sample setup.
- Codec errors exclude payload-bearing parser causes. Console error logging retains category/correlation without exception payloads. No host-wide ObjectMapper change, APOLO dependency, field schema, merge/patch behavior, DTO reconstruction or new endpoint was added.

### Actual verification

Environment: Windows, Corretto JDK 21.0.13, Java release 17; repository Maven wrapper. SQL execution used only the guarded `integration-test` profile on localhost:1433/apolo_workflow_test with native Windows authentication. Database runs were serialized.

- PASS — `./mvnw.cmd -B -ntp -Pintegration-tests verify`: 157 core isolated unit cases, 48 host isolated unit cases and 36 SQL Server integration cases, no failures/errors/skips. A final run also enables `console-ui` to package the pinned frontend into the executable host. This is local standalone evidence, not APOLO integration acceptance.
- PASS — codec/definition/model checks (AC01/02/06/09/10/12/14): strict tokens/Unicode/duplicates; exact scale and exponent vectors; canonical reordering versus scale/null/array conflicts; open nested snapshot fields and rejected top-level schemas; immutable replacements, typed null, scalar regression suites and complete example validation.
- PASS — parser/evaluator checks (AC03–05/11/13): property/quoted/computed access, BigDecimal indexes, negative fractions, null-safe index skipping and ordinary null failures, exact data keys including `class`, blocked methods/types/beans/assignment/collection operations, actual read order, leaf evidence and bounded container comparison. F008 native arithmetic regression tests still pass.
- PASS — capacity checks (AC06/10/11/14): exact 256 KiB UTF-16 values and one-code-unit overflow; bounded raw factory ingress; collection/depth boundaries; exact 1 MiB current-state/start/service envelopes including keys/punctuation and overflow; full old/new replacement batches; exact 4 MiB history UTF-16/UTF-8 and 16 MiB cumulative serialization limits; unchanged receipt bound. SQL cases cover maximum values through explicit start, default, service replacement and history reads, Unicode byte pagination and stable continuation.
- PASS — committed SQL commands and observations (AC02/07–12/14): scale survives persistence/reload; no-row versus SQL NULL projections; replacement visible to routing; leaf and complete old/new evidence; task completion recovery and exact submission reads under history schema 2; actual final version; reordered-input replay without effects/evaluation and scale conflicts; one winner for overlapping JSON completions; atomic rollback of task/state/claim, host audit effect, history, receipt and outbox after navigation, recorder, per-record evidence and cumulative evidence failures.
- PASS — migration inspection: `ck_workflow_history_json` contains ISJSON and 4194304; `is_disabled=false`, `is_not_trusted=false`. Repeat migration startup reported 0 new / 10 previously applied changesets, with no reset. Tests remove only their own fixtures.
- PASS — frontend isolated tests: 73 cases using the project's Node 22.22.0 binary, including raw numeric transport/replay/export, exact variable response text, exact Blob download, recovery, identity clearing, paging and existing UI behavior.
- PASS — Angular production build and existing CSP stylesheet check. The initial bundle reports a warning above the existing 900 kB warning budget (about 931 kB); it remains below the error budget. No unrelated bundle refactor was performed.
- PASS — final diff/content/link/whitespace inspection, including new documents and fixtures; `git diff --check`. No generated target artifacts, logs, native DLLs or credentials are intended source changes.

Earlier attempts are retained as failures rather than counted as passes: sandboxed wrapper startup could not inspect its Maven cache; the authorized run succeeded. Initial compilation exposed Jackson feature configuration/protected AST-access mismatches, corrected before successful builds. Early unit runs found the receipt entity's old schema guard and superseded fingerprint/deduplicated-read/smaller-capacity expectations. The first SQL run had two old scalar evidence expectations; both were reconciled to D14/D15 and replacement overflow scenarios were added. Subsequent complete runs passed.

### Limits and remaining acceptance

- Interactive browser walkthrough: NOT RUN. Raw transport, download and component behavior have automated coverage; no full browser/real-login workflow or visual QA is claimed.
- APOLO provider/security/transaction integration, production migration ordering, operational retention and performance/load acceptance remain unverified and outside this standalone slice. No production readiness is claimed.
- No development host, development database connection or temporary HTTP server was started. Verification uses mock-web integration contexts; test/build JVMs and Node commands exit normally. Other processes were preserved, including an unrelated process that reused an earlier test PID.
- T03–T06 require no further implementation work in this slice. Review the diff before any later commit; no commit or push was made.

Final packaging command: ./mvnw.cmd -B -ntp '-Pintegration-tests,console-ui' verify (PASS, same 157/48/36 cases). The focused JsonExecutionUnitTest run additionally checks the final full old/new maximum-state batch assertion. Frontend tests use workflow-test-host/target/frontend/node/node.exe --experimental-strip-types --test 'unit/*.test.mjs' 'src/app/**/*.test.mjs' from the frontend directory. Both commands use the project-pinned runtimes and generated reports remain under ignored target directories.

Final follow-up results: 9/9 focused boundary unit cases and 73/73 pinned-Node frontend cases passed. JAR inspection confirmed the packaged console index and generated JavaScript asset. Static review checked 13 changed Markdown files, 199 relative link targets and whitespace across all changed/new source files; the staged diff is empty.

## Interactive Chrome verification — 2026-11-06

The owner requested testing the already-running application in Chrome. This authorizes the local sandbox actions below; the host was not started, restarted or stopped by this session. The worktree already contained the F009 implementation and documentation changes, which were preserved. Browser URL: `http://127.0.0.1:4200/console/`; initial session: `f006.admin`.

- PASS — imported the booking-review fixture with only its identity/name changed to `sandbox.f009.browser.20261106`, version 1, name `F009 browser JSON booking review`. Family ID 19 / version ID 23. Validation reported PASSED; the published version was available for start selection. The existing selectable `SANDBOX_REVIEW` catalog entry was reused. No existing definition was changed.
- PASS — created request 30 (`F009 browser exact JSON and replacement`) and started version 23 with raw inputs `{"booking":{"amount":12500.00,"department":{"code":"SALES"},"obsolete":true,"precise":9007199254740993.1234567890,"reviewers":[{"name":"Ana"}],"optional":null}}`. Result: instance 34 ACTIVE at REVIEW, task 27 assigned to `f006.reviewer`, instance token 1 and last history sequence 6 (AC01/06/10/12, partial).
- PASS — Command recovery retained the exact raw numeric text. Retrying original start command `74c1bdee-0df1-49bc-a5bd-5959daa286ee` reported REPLAYED_ORIGINAL_RESULT with the original instance/token/history range (AC09, identical-payload browser case only).
- PASS — current instance variable inspection returned initialized JSON with `12500.00`, `9007199254740993.1234567890`, explicit nested null, the array and arbitrary extra fields intact. This checks displayed persisted read text, not downloaded-file bytes (AC01/06/10, partial).
- INCONCLUSIVE — clicking Download exact page JSON with a browser download-event waiter stalled the automation until its session reset. The tab remained usable after recovery. No downloaded path/content was obtained; this is not proof that the application download failed or succeeded.
- FINDING — execution-variable headings display literal question-mark separators (`booking ? JSON ? Initialized`). Confirmed in `workflow-test-host/frontend/src/app/pages/runtime/instance.html`; JSON data itself is unaffected. No source fix was made during testing.

The owner then supplied a signed-in reviewer session in Chrome. No credentials were read from private configuration.

- PASS — task 27 appeared in `f006.reviewer`'s inbox. Completing COMPLETE with comment `F009 browser verification: exact snapshot, replacement and nested routing.` committed command `a110811f-439a-4c89-8999-0ad9e23b4fb8`. Instance 34 reached COMPLETED / SALES, actual token 3 and history sequence 15. The accepted submission is readable at task 27, history sequence 7, with the correct actor/outcome/comment (AC07/10/12, successful browser path).
- PASS — retrying that completion returned REPLAYED_ORIGINAL_RESULT with the same token/history range. The subsequent current-state history listing still contained 15 events, including one SERVICE_TASK_COMPLETED and one replacement VARIABLES_WRITTEN event (AC09, observable workflow evidence; the host service-effect table was not independently inspected).
- PASS — authorized reviewer variable inspection returned exactly `{"amount":12500.00,"department":{"code":"SALES"}}`. The original obsolete/optional/precise/reviewers fields were removed, demonstrating whole-value replacement rather than merge (AC02/07).
- PASS — history event 11 retained details schema 2 and the complete previous JSON, including the exact large decimal, nested null and array, alongside the complete replacement and actionOutput binding source. Event 13 selected sales with usedDefault=false and recorded only leaf paths amount (`12500.00`) and department/code (`"SALES"`), rather than copying the root for those reads (AC03/11, selected fixture path).
- PASS — final visual inspection showed instance 34 COMPLETED at SALES, token 3, history sequence 15. Chrome was left on that instance in the reviewer session.

This is a successful browser walkthrough, not complete AC01–AC14 acceptance: negative inputs, missing/null/index variants, default routing, malicious expressions, changed-payload replay, concurrency, rollback, capacity boundaries, delivery payloads and APOLO behavior were NOT RUN in this browser session. The sample refresh action always returns the fixed SALES snapshot; changing only its start input would not exercise the other route. The earlier automated evidence remains separately recorded above. Download verification is inconclusive and the heading-separator finding remains unfixed. Automated tests/builds/database integration suites were not rerun. No process was started or stopped; the owner's app remains running. No commit or push was made. Final documentation diff inspection and `git diff --check` passed.

## Separator fix and negative verification — 2026-11-06

The owner requested fixing the heading separators and continuing negative scenarios. Replaced only the two literal question-mark separators in `workflow-test-host/frontend/src/app/pages/runtime/instance.html` with middle dots. PASS: live Chrome accessibility and visual inspection show `booking · JSON · Initialized` for instances 34/35. No new test was added for this template-only presentation fix.

### Browser checks

All actions used the already-running localhost console. Request 31 (`F009 negative inputs 20261106`) initially targeted published version 23 with request token 0. The following eight start-input cases were rejected (AC01/06):

- Duplicate keys: `{"booking":{"amount":1,"amount":2}}` — client message `Duplicate JSON property`.
- Malformed document: `{"booking":` — client message `Unexpected end of JSON input`.
- Missing required input: `{}` — HTTP 400 INVALID_INPUT.
- Nonnullable root null: `{"booking":null}` — HTTP 400 INVALID_INPUT.
- Primitive JSON root: `{"booking":123}` — HTTP 400 INVALID_INPUT.
- Unknown top-level input: `{"booking":{},"extra":true}` — HTTP 400 INVALID_INPUT.
- Inexact decimal: `{"booking":{"amount":0.12345678901}}` — HTTP 400 INVALID_INPUT.
- Unpaired Unicode: booking.text encoded using the single JSON escape `\uD800` (an unpaired high surrogate) — HTTP 400 INVALID_INPUT.

PASS: after those attempts, reloading the request list still showed no linked instance and version 0. Client-side duplicate/malformed rejection is distinguished from the server rejections above.

Created isolated family 20 / version 24, `sandbox.f009.negative.20261106` (`F009 negative browser rollback`), from the booking fixture. PASS: validation/publication rejected each of these conditions and retained DRAFT, with invalid-restricted-expression diagnostics (AC05/12): `#vars.booking.getClass() != null`, `T(java.lang.System).currentTimeMillis() > 0`, `@environment != null`, `#vars.booking.amount = 1`, and `#vars.undeclared == null`. These attempts changed only this new draft.

Changed that draft's condition to `#vars.booking.missing.code == 'X'`, with an explicit default transition to DEFAULT, then published revision 6. Publication correctly cannot establish nested field existence without a schema. Started version 24 on request 31 with `{"booking":{"amount":1.00,"obsolete":true}}`: instance 35, task 28, token 1, sequence 6. The graph is START → REVIEW → RECORD (`sandbox.refreshBooking`) → ROUTE → MATCH/DEFAULT. The service returns its fixed replacement before the intentionally invalid ordinary null traversal.

- PASS — the stale reviewer UI received HTTP 403 FORBIDDEN after the administrator login changed the shared server session. The owner then supplied a fresh reviewer login; no credentials or private configuration were read.
- PASS — reviewer COMPLETE failed with HTTP 422 EXPRESSION_EVALUATION_FAILED, rather than selecting DEFAULT (AC04/07).
- PASS — refreshed instance 35 remained ACTIVE at REVIEW, token 1, sequence 6; current JSON remained `{"amount":1.00,"obsolete":true}`. Ordered history contained only the six start events. Task 28 remained OPEN with no accepted submission. This establishes observable workflow/variable/evidence rollback; host effect/outbox/receipt tables were not independently queried in the development database.

The deliberately failing published version and open task are retained for repeatable inspection. The original successful instance 34 and existing user fixtures were preserved. Chrome was left on instance 35 in the reviewer session, showing the corrected heading and preserved variable.

### Automated checks and process state

- PASS — pinned Node 22.22.0: `node node_modules/@angular/cli/bin/ng.js build`, followed by `node scripts/check-build.mjs` from frontend. Production build/CSP check passed. Existing initial-bundle warning remains: 930.65 kB versus 900 kB warning budget.
- PASS — Corretto JDK 21.0.13 selected explicitly, Java release 17. `./mvnw.cmd -B -ntp -pl workflow-core '-Dtest=JsonExecutionUnitTest,JsonNavigationUnitTest,JsonDefinitionUnitTest' test`: 16 isolated cases, no failures/errors/skips. The initial sandboxed attempt could not open Maven-cache locks; the subsequent authorized cache-access run passed.
- PASS — guarded SQL Server rollback case on localhost:1433/apolo_workflow_test: `./mvnw.cmd -B -ntp -pl workflow-test-host -am -Pintegration-tests '-Dtest=JsonNavigationUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=RestrictedRoutingIT#jsonFailureAfterReplacementRollsBackHostEffectValuesAndHistory' '-Dfailsafe.failIfNoSpecifiedTests=false' verify`. Five navigation unit cases and one integration case passed. The integration case independently verifies rollback after replacement, including participating host effect, values, task/instance, history, command receipt and outbox. This verify invocation packages the host without console-ui; frontend build verification above is separate.
- PASS — final source/documentation diff and whitespace review, `git diff --check`. No source behavior beyond the separator fix changed. No commit/push.

The build/test commands exited. The integration JVM exited normally; no temporary server was started and the owner's application was not restarted or stopped. This follow-up does not close all acceptance: live invalid-index variants, changed-payload replay, concurrency, capacity boundaries, delivery payloads and APOLO checks were not rerun in Chrome. Focused automated cases cover several of those codec/navigation boundaries; download verification remains inconclusive from the earlier session.
