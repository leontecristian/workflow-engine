# Implementation - F007 Typed bindings and service tasks

## 1. Implementation summary

**2026-11-01: implemented against READY spec revision 2.** T02-T10 are complete; T09 is **CLOSED by owner acceptance** after the manual API/SQL run and subsequent owner browser walkthrough/replay confirmation. The closure record below distinguishes observed results from remaining unverified follow-up coverage. All six scalar types, direct initialization, synchronous registered service actions, existing-column variable persistence, versioned receipts/history, host sample storage/action and console support are present together. This does not close F005/F006 or establish APOLO readiness.

## 2. Components and files changed

- Core: immutable scalar/input/action/read DTOs; extended internal definition model; separate scalar-service structural schema and semantic/action checks; shared pinned loading and referenced-action validation provenance. Original minimal schema and canonicalization remain unchanged.
- Runtime: RuntimeScalarBindings, RuntimeVariables, RuntimeServiceProgression and ServiceActionRegistry; new start/replay overloads; bounded variable query; controlled typed-column replacement; early aggregate claim retained; final managed version captured after flush.
- Evidence: raw supplied input fingerprint v2 with empty-start/completion v1 compatibility; strict v1 and range-aware v2 result readers; ordered input/service/write history, sanitized handoff payloads and complete per-event/batch limits.
- Host: append-only 003-console-service-effects.xml plus ordered include; SandboxRecordQuantityAction, SandboxServiceEffectStore and admin reads; startup failure settings; exact scalar start/replay body decoding and authorized variable reads. Existing applied migration files are unchanged.
- Frontend: scalar JSON input entry without number conversion, deep frozen recovery/export/restore, duplicate detection, variable paging with stale-page reset, admin-only effect inspection and explicit extended-graph fallback.
- Isolated test fixtures: G04 document, four frozen legacy/extended start/completion receipt snapshots with independent SHA-256 files and specification request hash vectors.

## 3. Decisions and corrections

No material contract deviation. Core still has no endpoints, datasource, transaction manager, host entity dependency or migration runner. Actions must participate in the host transaction and do not retry; the sample uses MANDATORY JDBC storage. Initializer/output mappings prepare all values against one pre-update snapshot. Missing optional values skip writes; explicit null remains initialized typed null. Bound optional arguments still fail on an uninitialized variable.

Legacy definitions keep the original schema/validator context, fingerprint v1, result schema 1 and eight-event history. Extended definitions use the new validator/schema, referenced descriptors, result schema 2 and actual activation/history positions. Default normalization never changes request identity or published JSON. Variable reads bound stored rows to 101 (reject >100), refresh values, and compare scalar version queries around materialization rather than relying on cached entity versions.

Two verification discoveries were corrected: OffsetDateTime.toString can omit seconds, so persistence read conversion uses UTC instant text and removes unnecessary fractional zeros; the graph extension initially moved the existing history-limit check, so the old early limit ordering was restored. An over-limit graph still fails before expensive reads; supported-size extended shapes explicitly return graph unavailable.

## 4. Verification

Environment: Windows PowerShell; verified Corretto **JDK 21.0.13**; Maven compiler release **17**; repository wrapper Maven 3.10.0; pinned frontend Node **22.22.0** / npm **10.9.4**. No database-backed test was selected or created. The existing WorkflowTestHostApplicationTests remains excluded. The restricted shell could not read the wrapper cache; reviewed execution outside that shell succeeded.

### Isolated backend - PASS

Final explicit selection (root wrapper; JAVA_HOME points to installed JDK 21):

```powershell
./mvnw.cmd -pl workflow-core,workflow-test-host -am '-Dtest=RuntimeInstanceQueryUnitTest,ScalarBindingsUnitTest,ScalarDefinitionUnitTest,RuntimeReceiptCompatibilityUnitTest,ServiceProgressionUnitTest,RuntimeCommandsUnitTest,VariableReadsUnitTest,ConsoleScalarServiceUnitTest,ConsoleWireUnitTest,ConsoleRuntimeUnitTest,ConsoleDeliveryUnitTest,ConsoleInstancesUnitTest,ConsoleInstanceGraphUnitTest,ConsoleAccountsUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

**48 core + 30 host tests passed** in the full selected run, zero failures/errors/skips. A final targeted run passed all 35 ScalarBindingsUnitTest cases (one added aggregate invocation/default-budget case) and both ConsoleScalarServiceUnitTest cases; the explicit oversized start-input collection assertion also passed. A further isolated JDBC adapter test passed with the existing UUID string convention and host-link checks (three ConsoleScalarServiceUnitTest cases now pass). The final distinct suite inventory is **49 core + 31 host = 80 backend unit cases**. All are plain unit tests using in-memory collaborators/repository/JDBC doubles, without Spring database contexts. Coverage includes AC03-AC08 scalar/token/default/presence/Unicode/type/binding cases; AC01/10 fixed fingerprints and four receipt snapshots; AC02 G04 logical event ordering (1-9 / 10-16), exact values, final managed-token capture and receipt-first replay without action/deployment calls; AC11 early flush/call ordering and existing fresh-transaction recovery orchestration; AC12 step 50 and rejection before action 51; AC13 complete event/command/map bounds and token-aware variable paging. Host tests retain stored-assignment masking, exact transport, startup failure validation, writes before sample failure/invalid output, account behavior, delivery and graph regressions. These checks do not prove proxy interception, real rollback, SQL races, provider token increments or migration execution.

Historical implementation attempts: initial 43-test core run had one DATETIME read-conversion error; fixed, then 46/48-test runs passed. Initial combined host run had one graph-limit regression among 28 host tests; fixed, 11 affected graph/account tests passed, followed by the final 30-test host run. The final adapter test initially needed missing explicit Mockito imports; compilation was corrected and all three adapter cases passed. Mockito/JDK dynamic agent and unchecked test-double compiler warnings remain non-failing.

### Frontend - PASS

From frontend: pinned node with --experimental-strip-types --test unit/*.test.mjs src/app/**/*.test.mjs: **56 passed**, zero failures/skips. New cases cover exact strings above JavaScript safe integer limits, original decimal/time spelling, absent versus null, duplicate/number/container rejection, nested freezing, legacy recovery envelopes, actor clearing and stale variable paging. Existing route/security/recovery/graph component tests remain green. Angular production compilation also passed.

### Builds and artifacts

- **PASS:** ./mvnw.cmd '-Dmaven.test.skip=true' package for both modules; ordinary core JAR and executable host JAR.
- **PASS:** ./mvnw.cmd -Pconsole-ui '-Dmaven.test.skip=true' package including pinned npm ci, Angular production build and existing CSP asset check. Final artifact inspection/check results are recorded below.
- Tests and packaging are separate evidence; neither starts the host nor proves database behavior.

### Runtime acceptance at the original handoff - historical NOT RUN

AC01-AC15 retain their real-provider/browser acceptance portions from the plan. No host startup, Liquibase execution, database query, SQL integration test, live HTTP session or browser walkthrough was performed. The task did not explicitly authorize executing migrations or database integration tests. Outstanding acceptance includes actual SQL scalar round trips/null rows; new migration and restart/checksums; real sample writes with BEFORE/AFTER/invalid-output/recorder rollback; overlapping starts/completions; final provider-managed versions; persisted outbox/receipt replay counts; retirement/catalog disable and missing-deployment cases; live role/CSRF protections; and the packaged browser/recovery flow. Unit successes are not acceptance PASS for these obligations.

## 5. Handover and process state

For a future authorized run, use the complete G04 fixture/commands and plan acceptance matrix. The sample action registers only with console enabled and capabilities=sample; capabilities=none registers no sample action. Both new failure-node settings default empty. Configure an exact node key at startup; fail-service-node throws after the host insert, invalid-service-output-node omits the required output after insertion. Do not configure both for one node; restart with empty settings to restore normal execution. Existing fail-event-recording remains independent. No cleanup, automatic fixture insertion or runtime switch endpoint was added.

Final review also restored the original fully qualified enum literals in untouched legacy JPQL queries after removing incidental import cleanup. No legacy repository query change remains.

At the original implementation handoff no application/server/database process was started and no preexisting process was stopped. Maven/unit/frontend build processes completed. The later T09 process lifecycle is recorded below. Changes remain **uncommitted and unpushed**. Initial unrelated F006 handover/navigation edits were preserved; separately appearing F008 documents were left untouched. Feature navigation and predecessor cross-references now distinguish implementation from open acceptance. No generated targets, logs, credentials or native DLLs are intended deliverables.

### Final static/artifact review - PASS

The ordinary core JAR contains both definition schemas and the workflow master, with no BOOT-INF or test-host content. The executable host JAR contains 003-console-service-effects.xml and the three production console assets. ScalarValue class major version is 61 (Java 17). Core migration resources and applied host changesets 001/002 have no diff. Exactly four F007 process documents remain; 133 local links across the reviewed changed documents resolve. Tracked git diff --check and a whitespace scan including new source/docs pass. Separate F008 content and the preexisting F006 handover were excluded from edits/reconciliation. The final package refresh uses -Pconsole-ui -Dmaven.test.skip=true -Dskip.npm=true after the full npm/Angular/CSP build already passed, reusing unchanged verified frontend assets.

## T09 manual runtime acceptance - 2026-11-01

**Authorization and scope:** the owner explicitly requested T09 after the implementation handoff. Manual host startup/migration, authenticated local console commands and independent SQL Server reads were performed against apolo_workflow_dev. No database integration test suite or database-backed Spring test was created/run. The manual command envelopes and observations are ignored local output under workflow-test-host/target/f007-t09; they are not committed artifacts. Existing data were retained.

**Environment/artifact:** Corretto JDK 21.0.13, SQL Server 16.0, Hibernate 6.6.13.Final, native Windows authentication and loopback port 8087. Initial manual cases used the implementation artifact; an invalid-date error translation found during this run was corrected, rebuilt and rechecked. The only production-code change in this task is the DateTimeException translation in RuntimeScalarBindings. Corrected executable host JAR SHA-256: b9b4064157021c27cb3e37c587af14f41e2d21a72b3092355389a5dfbc25854f. Frontend assets were unchanged and reused from the previous verified console build.

### Observed PASS results

- **Migration/restart (R12):** first startup applied only f007-001-create-service-effect, author workflow-test-host, logical path db/changelog/test-host/changes/003-console-service-effects.xml. Its stored checksum is 9:6505e028f5e4113e770039cde8861a84. Eight earlier changesets validated; repeated startups retained all nine identities/checksums. JPA schema validation/startup succeeded. Read-only metadata confirmed the request FK, positive-instance CHECK, unique(command_id,node_key) and instance/id index. No core migration ran.
- **AC02 G04:** fresh published definition version ID 10, instance 17, request 13, activity/task 14. Start committed history 1-9, paused at REVIEW with activation sequence 8/token 1 and one BEFORE effect. Reviewer completion committed 10-16, result COMPLETED/token 3 and a second AFTER effect. Both LONG variables and both SQL host rows contain exactly 9007199254740993. Each of 16 history rows has a corresponding outbox row. The original start snapshot remains ACTIVE/sequence 9 on replay after completion.
- **AC04/05/08 scalar storage and mappings:** version 11, instances 18 and 19 round-tripped LONG minimum -9223372036854775808, DECIMAL 9999999999999999999999999999.1234567890 exactly in SQL DECIMAL(38,10), DATE 2024-02-29, false Boolean, whitespace/decomposed Unicode/emoji STRING and offset DATETIME normalized to 2026-01-01T00:00:00.1234567Z. API decimal/time normalization preserved value. Optional absent default became fallback; explicit null did not default. SQL had no rows for uninitialized never/note on instance 18, while instance 19 had STRING discriminator rows with SQL NULL for explicitly null note/defaulted. Two variable-source mappings swapped a=1/b=2 to a=2/b=1 from one snapshot; reverse mapping order on completion restored a=1/b=2.
- **AC03/06 rejection/security:** missing/unknown inputs, null input object, JSON number token, LONG negative zero, decimal exponent and inexact timestamp returned 400 INVALID_INPUT. A 300,000-character body returned 413 PAYLOAD_TOO_LARGE. Publication rejected missing action, nonempty guard and cyclic graph with located diagnostics. Administrator completion could not bypass stored assignment (403 NOT_ASSIGNED); missing CSRF and reviewer effect access returned 403; anonymous variable read returned 401; a different retained reviewer received masked 404 for variables/history. Authorized exact submission/history/variable reads succeeded.
- **AC09/07 rollback:** instance 20 remained ACTIVE, task OPEN, token 1/history 9 after separate fail-service-node=AFTER, invalid-service-output-node=AFTER and fail-event-recording runs. Independent SQL after each retained exactly one original effect/receipt and nine history/outbox rows. Errors were SERVICE_ACTION_FAILED, INVALID_ACTION_OUTPUT and HOST_CONFIGURATION respectively. BEFORE failure left request 20 unlinked at version 0, with no instance/effect. Resetting settings and deliberately submitting the original failed commands succeeded: instance 20 completed/token 3 with exactly two effects; the original failed BEFORE start subsequently created instance 26. No successful receipt or business action was automatically retried.
- **AC10/01 replay:** identical G04 start/completion replay returned original snapshots without new effects/history/receipts/outbox. Changed quantity and absent-versus-null input conflicted with 409 COMMAND_ID_CONFLICT. Fresh minimal version 15/instance 28 retained history 1-4/5-8 and zero variables/effects; omitted inputs and {} replayed the same v1 start, while a nonempty map conflicted. Retained pre-F007 instance 1 start receipt ca8fdb7a-4851-4563-b45e-974d79f2eba5 and completion receipt 389f6bf5-8059-4e27-aca0-975f4f74bf0f replayed through read-only lookup under original f006.admin/f006.reviewer actors. Original snapshots ended at 4/8; SQL still showed eight history rows, two receipts and zero variables/effects.
- **AC11 overlapping commands:** two distinct completion UUIDs for instance 18 began on separate server threads at 20:02:29.154/.155 local; one returned 200 and one 409 STALE_VERSION. Exactly one AFTER effect, completion receipt and final state committed. Two identical starts began at .449/.450 on separate threads; both returned instance 20, one new and one replayed after fresh-transaction recovery, with one committed BEFORE effect. No mutation retry loop was added.
- **AC12 automatic budget:** a chain with START plus 49 services (step 50) paused successfully as instance 22, with 49 effects and history/outbox 153. Adding a 50th pre-human service returned 422 LIMIT_EXCEEDED; request 18 remained unlinked/version 0 with no effects or instance. This rollback/count observation and the isolated budget test support the fail-before-step-51 behavior; rolled-back effect identity gaps are not committed effects.
- **AC13 budgets/paging:** a valid 100-variable/49-service fixture exceeded the cumulative required-history budget and returned LIMIT_EXCEEDED. Request 19 remained unlinked/version 0; independent SQL found zero effects/instances, with no truncated committed evidence. Token-aware variable pages returned the correct continuation and exact scalar values; attempting a later page with the pre-completion token returned STALE_VERSION. A commit specifically between the two version queries was not forced/observed.
- **AC15 deployment/lifecycle:** disabled the existing SANDBOX_REVIEW authoring flag, started published version 10 successfully as instance 27, then restored original name/description/selectable=true (catalog technical token advanced 4 to 6). Retired version 10; the pinned instance completed while new selection returned INVALID_STATE. With capabilities=none, new start and pending completion failed HOST_CONFIGURATION without changing instance 19 (token 1/history 19/one effect/one receipt) or linking request 24. Successful G04 receipt replay still worked before capability/lifecycle checks. Pinned read APIs remained available. No published content/hash was rewritten.
- **R10 local durable handoff:** input/variable/service events 17:2, 17:3 and 17:5 were explicitly delivered to the existing local inbox. Stored JSON matched the outbox exactly; redelivering 17:5 reported duplicate=true. SQL found no inputs/outputs fields in new delivery payloads. The service payload contained only nodeKey/action; scalar values remained in authorized history.
- **R11/12 HTTP fallback/disabled mode:** extended graph endpoint failed closed with the established HOST_CONFIGURATION response; independent instance/history/variables/submission reads remained usable. The frontend's visual unavailable state is not claimed as observed. With console disabled, login/API/index routes each returned 404.

### Defect found and corrected

An invalid calendar DATE initially returned 500 HOST_CONFIGURATION: LocalDate.parse throws DateTimeException, outside the input decoder's IllegalArgumentException catch. RuntimeScalarBindings now translates both to the existing 400 INVALID_INPUT category. Added an isolated DATE/invalid-month DATETIME regression; all **36 ScalarBindingsUnitTest cases passed**. Rebuilt with root wrapper -Pconsole-ui -Dmaven.test.skip=true -Dskip.npm=true on JDK 21. Live requests against the rebuilt artifact returned 400 INVALID_INPUT for both calendar/timestamp cases. No specification/API change was needed, and no database-backed tests ran. The previous 80-case backend inventory remains historical; this adds one isolated regression case.

### Historical BLOCKED / NOT RUN at the manual-run handoff

**AC14 browser acceptance BLOCKED.** Browser inventory reported the browser access host unavailable. A native Edge window from inventory disappeared before binding. The available Chrome window could not be bound: Computer Use stopped because it could not determine the current browser URL confidently enough to enforce policy. No further UI action was attempted. Actual packaged start/pause/completion UI, variable/effect rendering, graph fallback, transmitted-value inspection and export/restore through reload remain unobserved. API success does not close these browser obligations.

**Provider cases NOT RUN:** a genuinely overlapping commit during variable-page materialization (as distinct from the observed stale subsequent-page token); deployment of duplicate/malformed or incompatible executable action registrations; optional/unknown-output variations beyond the sample's missing-required-output mode. Those remain covered only to their recorded isolated-test scope. No exhaustive combination of all scalar/evidence/request limits or all authorization variants is claimed from this finite manual run. The plan's full acceptance matrix remains the gate; T09 is not checked off merely because its major API/SQL paths passed.

### Final state and cleanup

Final SQL: instances 17/18/20/27/28 completed with history/outbox counts 16/26/16/16/8 and tokens 3; instances 19/22/26 remain intentional open sandbox fixtures with counts 19/153/9 and tokens 1. Effects are 2/2/1/2/49/1/2/0 across 17/18/19/20/22/26/27/28, respectively. Failed requests 18/19/22/24 remain unlinked/version 0. Fixture definitions/drafts, requests, effects, audits, receipts and local inbox observations were retained; no destructive DB cleanup was performed.

Temporary Java PIDs were 8624, 7496, 29560, 464, 30608, 27064, 16672, 26976, 19660, 11284, 15972 and 32284. Each was stopped after checking its Java command/JAR/8087 identity; no unrelated process was killed. One recorder probe arrived before startup and did not execute a command; that attempt was repeated only after read-only readiness, then its rollback was observed. Two process-helper retries encountered transient listener reports and started no duplicate host; identity/port inspection preceded continuation. Final host PID 32284 was stopped, port 8087 has no listener, and generated temporary credentials were removed. No host is left running by this task. No commit or push.

## T09 owner acceptance and closure - 2026-11-01

**CLOSED at the owner's explicit request: "Mark T09 closed".** This follows the manual API/SQL observations above and the owner-supplied browser screenshots and replay confirmation below. It is an owner acceptance decision, not a claim that every originally planned scenario was executed.

Observed owner browser evidence:

- Instance 29, request 25, task 22, definition sandbox.serviceReview.my version 1 (definition-version ID 19). The visible browser URL uses 127.0.0.1:4200/console/, so this is development-UI evidence; the screenshot does not identify the connected database or packaged artifact hash.
- History shows start sequences 1-9 under f006.admin and completion sequences 10-16 under f006.reviewer, ending with INSTANCE_COMPLETED.
- Both quantity and recordedQuantity render as initialized LONG values exactly 9007199254740993; the displayed instance token is 3. Variable inspection remains usable alongside the graph fallback notice.
- Administrator service-effect inspection shows records 123 (BEFORE/admin) and 124 (AFTER/reviewer), both for instance 29/request 25 with the exact quantity.
- Command recovery shows REPLAYED_ORIGINAL_RESULT for completion command 3ead4dcb-3cc9-467a-9431-775127a7b2a5, original token 1, original COMPLETED/DONE snapshot, history sequence 16 and final version 3. After being instructed to retry and refresh, the owner confirmed history remained at 16 and effects remained the same two records. This supports completion replay without duplicate effects/history.

Remaining unverified follow-up coverage, retained explicitly after closure: export/reload/restore was not separately confirmed; no DevTools capture establishes the transmitted string token; the packaged-UI walkthrough is not established by port-4200 screenshots. The previously listed concurrent commit during variable-page materialization, duplicate/malformed/incompatible action registrations and remaining optional/unknown-output cases retain their recorded NOT RUN status. A cosmetic question-mark separator in variable headings was observed and is not fixed by this documentation update.

These items no longer keep T09 open under the owner's closure instruction. F005/F006 acceptance and production/APOLO readiness are unchanged. This update changed documentation only: no application process, database operation, test, commit or push was performed.
## Historical preparation record

The following is retained from the earlier document-only revisions; its no-implementation/no-test statements describe those phases only.


### 1. Implementation Summary

**2026-11-01: documentation preparation and readiness resolution only.** Revision 1 was DRAFT; revision 2 is READY, not implemented. The owner requested resolution of G01–G04. T00/T01 are complete; T02–T10 remain open. The selected contracts are in [spec.md](spec.md).

### 2. Components and Files Changed

Created spec.md, plan.md, tasks.md and this handover under F007-typed-bindings-service-tasks. Navigation updates identify F007 revision 2 as READY. No Java, frontend, POM, migration or database changes.

### 3. Technical Decisions and Deviations

The selected bounded slice adds direct scalar bindings and service nodes around one existing SINGLE human task. The language already selects startInput/literal initialization and variable/actionOutput service bindings; expressions and direct fact/prior-submission bindings are unnecessary here. Nonempty guards and broader task contracts remain unsupported in this slice rather than silently ignored.

Inspected current minimal definition/result/capability APIs and typed variable mapping. Source confirms that variable absence and typed null have distinct existing representations, but provider write behavior has not been verified. Revision 2 resolves public API/receipt compatibility, evidence codecs and the sample action. Read-only inspection of the actual F001 variable/history/receipt DDL confirms no core migration is needed. A new host-only service_effect migration is specified but not created or run. No executable support or provider behavior is claimed from this inspection.

### 4. Verification

- **PASS — read-only baseline:** elevated git status was clean before preparation. Initial restricted git inspection could not recognize the work tree; the subsequent authorized inspection succeeded.
- **PASS — source/document review:** compared selected binding/value/progression rules with F004/F005 and existing core DTO/entity/service boundaries. Preserved newer F006 owner browser walkthrough evidence dated 2026-11-01.
- **PASS — revision 1 documentation checks:** exactly four feature documents; 56 local file links resolve across F007 and the two navigation documents; whitespace scan including untracked F007 files and git diff --check passed. Requirement IDs R01–R12, AC01–AC15 and T00–T10 are cross-referenced; G01–G04 explicitly gate readiness.
- **NOT RUN:** build, isolated unit tests, browser interaction, host startup, database access, migrations, rollback/concurrency checks and AC01–15. This is documentation-only work.

### 5. Known Limitations

No F007 implementation exists. G01–G04 are resolved; runtime/provider/browser acceptance remains NOT RUN. Historical F005/F006 acceptance gates are unchanged. The current chat only grants ordinary workspace writes to apolo-docs; writing these requested documents in the actual workflow-engine repository uses the platform's reviewed filesystem escalation.

### 6. Remaining Work

Start implementation with T02/T03 against revision 2, then proceed in dependency order. The complete fixture and engineering contracts are now specified. Record future actual results here without replacing earlier observations. No processes were started and no credentials or application data were accessed. No commit or push was performed.

### Revision 2 — Readiness resolution, 2026-11-01

Resolved G01 with compatibility-preserving service overloads, sealed scalar literals, normalized typed values, executable action descriptors, bounded variable paging and exact HTTP/error rules. Existing command records and the two-field DefinitionCapabilities constructor remain unchanged. The host SandboxStart gets an input field with a legacy convenience constructor.

Resolved G02 with v1 identity for empty starts/completion, v2 raw-input identity for nonempty starts, strict legacy result decoding and result schema 2 for extended sequence ranges. Source inspection found fixed 1–4/5–8 receipt sequence checks and activity sequence 3; the plan explicitly retains that verifier for old receipts. New history payloads/order and privacy-preserving durable payloads are specified, along with cumulative bounds.

Resolved G03 with a single append-only host migration/table, sandbox.recordQuantity action, admin effect read endpoint and startup-only failure injection. The sample writes before failure in the participating host transaction; correctness must still be demonstrated on SQL Server. Existing JSON and scalar columns, open history event types and positive receipt schema versions need no core DDL change.

Resolved G04 with the complete five-node definition, exact command workflow, expected history 1–16 and acceptance matrix. This adopts direct bindings only and preserves existing SINGLE/empty-task-input/forbidden-result behavior. No expression or guard engine is introduced.

The preexisting F006 handover and frontend edits visible at this turn's baseline were not changed. Readiness means the implementation contracts are specified, not that tests or acceptance have passed. All runtime checks remain NOT RUN. Revision 2 document inventory/link/fixture/whitespace results are recorded below after validation.

- **PASS — revision 2 documentation verification:** four feature files; 56 local file links across six edited documents; whitespace scan including untracked files and git diff --check. Three JSON blocks parse, the five-node/four-transition fixture has valid references and two service actions, and all 15 acceptance IDs appear in the plan matrix. Independently computed canonical request SHA-256 vectors are embedded in the specification. The first traceability scan rejected abbreviated combined IDs; expanded them and the repeat scan passed. These are document/fixture checks, not runtime tests.

## Subsequent graph-view extension - 2026-11-01

The owner later requested general graph support after observing the service-flow fallback. [F006 revision 6](../F006-test-host-definition-console/spec.md#revision-6---bounded-general-graph-viewer-owner-request-2026-11-01) now adds a bounded general topology/history projection and dynamic SVG layout. F007's execution, accepted contracts, owner T09 closure and historical fallback screenshot remain unchanged. Future checks of the revised viewer should expect the five-node G04 graph rather than its old unavailable display; new renderer browser acceptance is recorded separately under F006.
