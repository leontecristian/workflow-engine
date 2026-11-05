# Implementation — F008 Restricted expressions and exclusive routing

## 1. Implementation Summary

**Current delivery — 2026-11-05, spec revision 3.** Restricted scalar SpEL evaluation, exclusive graph validation, ordered first-true/default progression, routing/read evidence and readable console history are implemented. F007 revision 3 variable batches, strict legacy receipt/history compatibility, pinned definitions, early instance claims and transaction-owned handoff are retained. No migration, new module, host infrastructure or expression endpoint.

**Owner correction governs:** native SpEL arithmetic, promotion, rounding, floating-point behavior and integral overflow are accepted. DECIMAL variables enter as BigDecimal from exact stored text; literals keep native types. ExactArithmetic was removed; NumericWork checks work/allocation capacity only. Input/storage/submission exactness is unchanged. Historical rejection-of-inexact-arithmetic claims below are superseded and are not the current contract.

T01–T07 are complete; the final native-policy unit/build/guarded SQL checks passed. T08 remains open for the packaged authenticated browser walkthrough and new-event inbox dispatch/restart acceptance. Provider evidence is recorded separately from UI/unit/build results.

## 2. Components and Files Changed

- Core: separate exclusive-routing structural schema, mandatory-human DAG/reachability validation, conditional/default transitions, multiple terminal nodes and profile-aware validation provenance. Older structural schemas/validator identities remain unchanged.
- Expression packages: immutable plan descriptions and read/evaluation DTOs, direct scalar-read leaves, fresh stock SpEL delegates/strict context, local operation/evaluation budgets, bounded LRU and an explicit verified Spring 6.2.6 compatibility gate. No mutable AST/context/snapshot is cached.
- Runtime: gateway visits share command automatic-step/evaluation allowances; traversal returns the actual reached END. GATEWAY_ROUTED precedes the selected transition and preserves ordered actual evaluations/reads. Delivery contains only node/selected-transition keys. Existing history/event budgets enforce atomic overflow failure.
- Host/console: existing HTTP error mapping now includes routing/evaluation categories. History details render selected route, results and exact typed reads; raw legacy evidence remains available. Existing graph display/fallback, start inputs, sandbox action, roles, CSRF and recovery surfaces are reused.
- Tests: preserved readiness probes; expression/profile/resource/cache, graph, routing and retained legacy unit suites; guarded RestrictedRoutingIT with test-owned publication, host effects, durable handoff and scoped cleanup. Core now directly declares spring-core for the TypeDescriptor API; managed versions are unchanged.

## 3. Technical Decisions and Deviations

The explicit owner correction supersedes the earlier numeric interpretation. Native behavior is retained, including decimal division rounding, approximate literals/promotion, primitive Infinity/NaN and integral overflow. Numeric limits are independent of accuracy. No exact reference result or literal rewriting is used.

Actual-read evidence preserves declared input values, not computed intermediates. Direct key access cannot fall through to Map methods, reflection or scalar properties. Native Boolean/conditional/Elvis evaluation preserves skipped subtrees; native between reads both bounds but may skip its upper comparison. Static validation still inspects every subtree/route.

Spring dependencies remain 6.2.6 under Boot 3.4.5. Other evaluator dependency versions fail closed pending golden/compatibility verification. APOLO compatibility and host-specific production obligations remain open; local evidence does not close ASM-01–05.

## 4. Verification

### Current native-policy worktree

**PASS — combined backend/build/SQL:** `./mvnw.cmd -B -ntp '-Pconsole-ui,integration-tests' verify` completed on the reconciled native-policy source: **138 core unit tests, 47 host unit tests and 28 guarded integration tests**, zero failures/errors/skips. The integration inventory is **21 RestrictedRoutingIT + 3 VariableBatchIT + 3 DatabaseInfrastructureIT + 1 retained context-load test**. JDK **21.0.13**, compiler release **17**, Hibernate **6.6.13.Final**, Boot **3.4.5** and Spring **6.2.6**. No development database access.

**PASS — added resource boundaries:** a subsequent isolated `./mvnw.cmd -B -ntp -pl workflow-core '-Dtest=RestrictedExpressionUnitTest' test` passed **56 cases** after adding three focused tests for AST 256/257 nodes, depth 32/33, Unicode source 2,000/2,001 code points and bounded decimal power allocation. Production code was unchanged after the combined build. The final distinct unit inventory is **141 core + 47 host = 188**, including the retained nine readiness probes; this is not a claim that all 188 ran in the earlier combined command.

**PASS — frontend:** pinned Node **22.22.0**, `--experimental-strip-types --test 'unit/*.test.mjs' 'src/app/**/*.test.mjs'` from frontend: **62 tests**, zero failures/skips. New display cases retain persisted order, exact BIGINT strings and legacy/raw fallback; existing security/recovery/graph/page tests remain passing.

**PASS — packaged artifacts:** combined verify built the ordinary core JAR and executable host with production Angular/CSP checks. Inspected archive contents: NumericWork present, ExactArithmetic absent, no BOOT-INF in core, packaged console index and routing view present in host. Expression class major version **61** confirms Java 17 bytecode. No migration or dependency-version upgrade; core's direct spring-core declaration retains the managed version. Angular reports a non-failing initial-bundle warning (about 905 kB versus the 900 kB warning budget); no unrelated bundle changes were made.

**PASS — real SQL observations (AC02/03/04/05/08/09/10/11/12/13):** publication and real commands select high/positive/default for 20/5/0 with ordered Boolean/read evidence and metadata-only outbox payloads. Native DECIMAL rounding, LONG overflow, approximate literal comparison and lossy promotion choose their native routes after SQL round trips. All scalar projections, initialized null and the actual final managed lock token are checked. Commands failing on integer division by zero, uninitialized read, no-route or work limit roll back current task/effects/variables/history/receipt/outbox while preserving the prior start. Recorder failure after durable insertion and oversized complete routing evidence also roll back. New published priority order leaves an earlier instance pinned. Start and completion replay invoke the evaluator zero times. Overlapping completion transactions commit one effect/route. Gateway/START/service/END step 50 can finish or pause; step 51 rolls back, including starts with no committed instance. Assertions observe state outside the command transaction.

**PASS — cleanup:** read-only sqlcmd inspection explicitly targeted apolo_workflow_test: zero remaining f008.it.* definitions, zero F008_EFFECT audit fixtures, zero held migration locks and **9** applied changesets (0 new). Scoped test cleanup removes only fixture-owned IDs/correlations. The guarded contexts never opened an HTTP listener.

**PASS — final review:** reconciled active guidance/spec/plan/tasks with native arithmetic and preserved historical records explicitly as quotations. Verified **164 local file links across 12 documents**, exactly four F008 process documents, new source/fixture whitespace and `git diff --check`. Reviewed the final source/documentation diff; changelog files, normal datasource settings, root scanning marker and module count are unchanged. All work is uncommitted and unpushed.

Earlier serial runs in this session passed targeted expression/graph/routing/legacy suites and complete SQL transaction suites. Concurrent numeric corrections changed source/tests during the earlier verification interval; those results do not substitute for the final native-policy run. The owner separately reported the corrected 71-test isolated selection passing. Current SQL tests explicitly distinguish accepted native arithmetic from genuine evaluation/absence/limit failures.

### Corrections during implementation

Initial compile attempts exposed fixed-arity native unary/ternary node constructors; these were corrected. The old partial-model test constructors allow a null END, so their retained overload now supplies an empty terminal map for those isolated fixtures. Prior strict-policy tests had incorrect expectations for decimal division scale and decimal spelling of minimum LONG; those were corrected before the owner superseded that policy. The earlier strict-policy results are historical, not native-policy acceptance.

A PowerShell profile argument required quoting; an initial unquoted attempt failed before Maven execution. A generated JSON formatting command also failed before writing because of quoting, then succeeded through standard input. A documentation replacement mistakenly selected characters instead of full replacement strings in two guidance files; restored the recorded tracked baseline plus the preserved owner correction and inspected the resulting focused diff. No application or test behavior was affected. Reviewed wrapper/cache access was used where the restricted shell could not access Maven's user cache.

### Browser and process scope

Computer-use inventory found an existing Chrome window; it was not activated or navigated. No guarded authenticated HTTP browser session was started. Browser roles/CSRF/recovery and the complete sandbox fixture walkthrough are NOT RUN for F008; existing unit coverage is not browser acceptance. Integration contexts use a mock web environment and open no HTTP listener.

All owned Maven/test/Node processes exited; no owned runtime listener remains. Pre-existing Java PIDs **2072** and **22852** and user windows were left running; the latter was identified as an IDE Maven server rather than another integration run. Process metadata inspection exposed no command-line values. No commit, push, staging or branch switch (checkout remains main).

## 5. Known Limitations

- T08 packaged browser acceptance remains open, including exact history rendering, authenticated roles/CSRF, recovery and the complete sandbox fixture. The SQL tests exercise trusted core commands and test-owned adapters rather than the browser.
- New-event inbox dispatch/restart recovery was NOT RUN. Persisted atomic outbox handoff and retained delivery unit checks do not establish that separate acceptance.
- The verified expression dependency is Spring 6.2.6 only. Broader APOLO dependency, transaction, deployment and delivery compatibility remains unverified.
- Published data/evidence and retained legacy readers are not migrated or rewritten. Feature completion does not close historical F005/F006 acceptance or establish production readiness.

## 6. Remaining Work

T09 handover covers delivered scope, check outcomes and remaining acceptance. Complete T08's manual browser and delivery observations in an authorized test-host session; do not use the normal development database without task-specific authorization. No unresolved numeric policy decision remains.

## Historical preparation and investigation records

The quoted records below preserve earlier outcomes, failed attempts and handoffs. Their DRAFT/pending/rejected-accuracy-policy statements describe their original dates/worktree, not current behavior. The owner correction and current sections above take precedence.

> ## 1. Implementation Summary
>
> **2026-11-05: T01 completed, revision 2 READY.** Owner selected rejection of inexact native arithmetic. Finalized exact AST/access/type/literal/null policy, local pre-execution work and allocation limits, bounded immutable-plan caching, error mappings and golden routing evidence/legacy contracts. Expanded the preserved readiness probe with native promotion/exactness and parser-boundary observations: **9 tests pass** under JDK 21.0.13, release 17 (`./mvnw.cmd -B -ntp -pl workflow-core '-Dtest=SpelReadinessProbeUnitTest' test`). No database/context/host used. Spec/plan are READY for T02 onward; this records contracts and feasibility, not production safety or acceptance. Earlier pending-decision entries below remain historical.
>
> **2026-11-05: readiness execution clarification.** Updated spec/plan/tasks to make DRAFT an actionable T01 starting state, permit the evidence-backed transition to READY and continued implementation without redundant approval, and distinguish status from unresolved behavioral decisions. Preserved the existing probe source, investigation results and pending numeric question. No gate is closed by this clarification.
>
> **2026-11-05: T01 dependency probes and integration contracts.** Added isolated `SpelReadinessProbeUnitTest` against actual Spring Expression 6.2.6. Seven probes pass, covering native numeric hazards and overloader bypass, AST inventory, detached temporal/null semantics, absence of a native operation-budget API, and a local guarded-node prototype including the 10,000-operation boundary and short-circuit behavior. Selected concrete G03 DTO/event/error/schema/provenance/receipt contracts from current F007 revision 3 code. T01 remains open and spec/plan remain DRAFT: the observed arithmetic exactness conflict requires the narrow owner decision recorded in the spec. No production evaluator, gateway execution, dependency upgrade, migration or UI change is implemented.
>
> **2026-11-05: G01 documentation reconciliation.** Recorded the owner's confirmation of scalar #vars and one human stage, with data supplied through existing start/service bindings and no direct #facts. Linked inherited L10/L12, presence/null and strict-error decisions to their authority, and separated the remaining AST/access, typed projection, numeric/comparison and golden-case work. Updated plan/tasks accordingly. G01–G03 remain open; this records existing decisions and confirmed scope, not an implemented or verified evaluator.
>
> **2026-11-05: dependency notes refreshed, revision 1 remains DRAFT.** F007 is implemented through revision 3; its original revision 2 T09 is closed by owner acceptance. Updated F008's current dependency notes and navigation to use that baseline, including batched variable-write evidence and retained legacy history/receipts. G01–G03 and T01–T09 remain open. No F008 code or fresh F007 runtime verification is claimed. The 2026-11-01 preparation observations below are historical.
>
> **2026-11-01: documentation only, revision 1 DRAFT.** User requested a branch and next-feature specifications. Created and switched to `codex/f008-restricted-expressions-routing` from main, preserving existing work. T00 is complete; T01–T09 remain open. No code or expression-safety claim.
>
> ## 2. Components and Files Changed
>
> Current T01 investigation adds `workflow-core/src/test/java/io/github/leontecristian/workflow/core/service/definition/SpelReadinessProbeUnitTest.java` and updates these four F008 documents. The pre-existing feature-index/roadmap edits are preserved. No production source, POM, schema or migration change.
>
> Created spec.md, plan.md, tasks.md and this handover under F008-restricted-expressions-routing. Navigation identifies the draft and F007 dependency. No application source, POM, migration or test-source changes.
>
> ## 3. Technical Decisions and Deviations
>
> Roadmap increment B is next: restricted expressions and exclusive routing. The owner confirmed scalar #vars and the bounded one-human-stage scope on 2026-11-05; the acyclic graph preserves F007's result shape. Direct facts, JSON navigation, guards, loops, no-human paths and additional human stages remain outside this slice. The selected scope does not remove broader agreed capabilities. Readiness still requires the concrete evaluator and integration contracts.
>
> First-match/default/strict-error and SpEL semantics are inherited. Exact AST/numeric behavior, operation enforcement/dependency support and integration codecs remain G01–G03. No counter is claimed merely because spring-expression is present. The inspected root POM uses Boot 3.4.5 management; effective dependency compatibility/runtime safety were not tested.
>
> Historical preparation baseline (2026-11-01): F007 revision 2 was READY. Its implementation source files appeared concurrently during that task; they were not edited or verified there, and completion was not asserted. Its untracked documents and the existing modified F006 handover/index/roadmap were preserved. The current dependency baseline is recorded in the [specification](spec.md#outcome-authority-and-dependency); the [F007 handover](../F007-typed-bindings-service-tasks/implementation.md) retains its actual delivery, verification and gaps.
>
> ## 4. Verification
>
> ### Readiness execution clarification — 2026-11-05
>
> - Inspected the working tree and current T01 records before editing; preserved the existing documentation changes and untracked SpelReadinessProbeUnitTest source. This update changes only the four F008 process documents.
> - **PASS — documentation checks:** checked clarification wording against the open gates and numeric decision, verified 19 local file links across the four process documents and ran `git diff --check` without whitespace errors.
> - Documentation-only clarification: no builds, tests or probes rerun; no host/database access and no process started or stopped. Earlier probe results remain the investigating agent's recorded evidence. Changes remain uncommitted and unpushed.
>
> ### T01 isolated investigation — 2026-11-05
>
> - **PASS — baseline:** inspected status before edits. Six documentation files were already modified (the four F008 files, feature index and roadmap); preserved them. Read project guidance, current F008 documents, F007 revision 3 contracts/handover and relevant definition/runtime/storage/host code.
> - **PASS — environment:** `JAVA_HOME` selected Corretto JDK 21.0.13; `java -version` confirmed it. Root POM uses Boot 3.4.5; the executing probe verifies Spring Expression implementation version 6.2.6. `./mvnw.cmd -B -ntp -pl workflow-core dependency:tree '-Dincludes=org.springframework:*'` passed and confirmed expression, core, context, beans, tx, aop, orm and jdbc all resolve to 6.2.6. Compilation used release 17. No dependency was changed.
> - **PASS — isolated probes:** `./mvnw.cmd -B -ntp -pl workflow-core '-Dtest=SpelReadinessProbeUnitTest' test`: final run **7 tests, zero failures/errors/skips**, Maven BUILD SUCCESS. No Spring application context, database, host or external service is used. Reports remain generated under core target/surefire-reports. The AST inventory records concrete parser forms; it does not claim they are all permitted. Native numeric probes intentionally assert dependency behavior, including unsafe results; they are readiness observations, not F008 acceptance tests.
> - **PASS — local budget feasibility only:** guarded stock OpAnd and BooleanLiteral nodes charge a per-evaluation ExpressionState before delegate work. Two permitted visits exhaust an allowance of two, a skipped operand is uncharged, and a separately exercised allowance permits visit 10,000 and rejects 10,001. This does not prove a complete evaluator's operation/resource bounds, parser safety, arithmetic guards or cache isolation.
> - **Observed numeric conflict:** BigDecimal 1 / 2 yields 0 under stock scale/rounding; LONG max + 1 wraps; literal 0.1 + 0.2 yields Double 0.30000000000000004. Numeric operations bypass OperatorOverloader. Inspected bundled Spring source for OpDivide, OpPlus, OperatorPower, SpelNodeImpl and ExpressionState to check the cause and local guard feasibility. Asked the owner only for the numeric error-versus-result policy; syntax, routing order, scalar scope and short-circuit/null decisions were not reopened.
> - **Historical attempts in this investigation:** restricted-shell wrapper startup failed before Maven execution (`Cannot index into a null array` while inspecting the user wrapper cache). Reviewed execution with cache access succeeded. The first 3-test run failed because the probe expected DECIMAL division to round 1/2 to 1; actual HALF_EVEN behavior is 0. Corrected the observation, then 3-test, 5-test and final 7-test runs passed. No application fix or success claim was inferred from the initial failure.
> - **Static G03 reconciliation:** RuntimeJson schema 2 already permits bounded variable-length command spans; schema 1 remains fixed. RuntimeDefinitionPins verifies unchanged INSTANCE_STARTED identity. RuntimeHistoryBatch enforces per-event/cumulative dual-encoding budgets. Raw authorized history retains legacy and batch variable events. Existing SQL history event_type/version/JSON columns accommodate GATEWAY_ROUTED. RuntimeServiceProgression and completion currently assume one END; the spec now requires traversal to return the actual reached terminal. These inspections do not establish real rollback or SQL behavior.
> - **NOT RUN — production verification:** F008 evaluator/routing tests, guarded database feature integration, packaging and browser acceptance remain dependent on readiness and implementation. No database was accessed, migration run or HTTP host launched. All Maven/test processes from this investigation exited; no owned runtime process remains. No commit or push.
> - **PASS — handover checks:** reviewed the changes, confirmed exactly four process documents, checked 17 local file links across those documents and ran `git diff --check` without whitespace errors. The newly added test source was also checked for trailing whitespace. Existing navigation edits remain uncommitted with the investigation changes.
>
> ### G01 documentation reconciliation — 2026-11-05
>
> - Reviewed language design §§10.2–10.3/11.1–11.2, architecture §5.3, F007 scalar contracts and ScalarValue source against F008. Confirmed that transport text normalization does not establish an implemented typed expression context. No concrete SpEL behavior conflict was demonstrated or inferred as resolved.
> - **PASS — documentation checks:** reviewed the diff and settled/open status references, verified 63 local file links resolve across the six changed documents, and ran `git diff --check` without whitespace errors.
> - Preserved the existing six-file dependency-note changes. No application code, dependency, migration or test changes; builds, unit/integration tests and evaluator probes NOT RUN for this documentation-only reconciliation. No process was started or stopped. Changes remain uncommitted and unpushed.
>
> ### Dependency-note refresh — 2026-11-05
>
> - Reviewed F007's current task/handover records and the F008 dependency references; the starting working tree was clean.
> - **PASS — documentation checks:** reviewed the six-file diff, verified 62 local file links resolve, checked for stale pending-implementation wording and ran `git diff --check` without whitespace errors.
> - Documentation-only change: builds, tests, host startup and database access NOT RUN. No process was started or stopped. Changes are uncommitted and unpushed.
>
> ### Historical preparation — 2026-11-01
>
> - **PASS — baseline/branch:** inspected main and created codex/f008-restricted-expressions-routing without discarding or staging existing work.
> - **PASS — contract inspection:** read routing/graph/expression rules, F007 status, governing guidance and POM declarations.
> - **PASS — documentation checks:** exactly four feature files, 58 local file links across six edited documents, valid inline JSON, six nodes/six transitions with valid references and intended conditional/default ordering, all 15 acceptance IDs mapped in the plan, whitespace scan including untracked files and git diff --check. These checks do not execute SpEL or establish runtime graph validation.
> - **NOT RUN:** builds, unit/integration tests, dependency resolution/download, expression execution/probes, host/browser startup, database access, migrations or runtime acceptance. No process was started.
>
> The initial combined file-write command exceeded Windows process command-length limits before execution; split it into smaller writes. No partial files or failed application changes resulted from that attempt.
>
> ## 5. Known Limitations
>
> Current T01 finding: exactness guards cannot rely on native numeric operators or OperatorOverloader alone. The pending numeric policy and complete AST/type/resource/cache contracts block READY. G03 contract text is selected, while golden integration fixtures and implementation acceptance remain open.
>
> G01–G03 remain open; F008 is DRAFT. F007 revision 3 is available, but F008's integration contracts still need reconciliation. Safe expression execution needs actual structural/access/counter enforcement and compatibility evidence during implementation. Historical F005/F006 checks and F007's scoped verification are not fresh F008 acceptance.
>
> ## 6. Remaining Work
>
> Resolve T01 against implemented F007 revision 3, then implement F008 in dependency order. Preserve historical results and record actual checks here.
>
> Historical preparation handoff (2026-11-01): the initial branch was created in the shared checkout, so concurrent F007 work followed that checkout onto the F008 branch. After the user reported that Codex had finished, both branches were verified at the same commit and the checkout was switched back to main without discarding any work. At that handoff, this documentation was prepared for a separate F008-only commit, including only its navigation changes; F007 implementation and documentation changes remained uncommitted. No push was performed.
>
> ## Native arithmetic correction implementation — 2026-11-05
>
> Removed ExactArithmetic and its literal/promotion/reference-result accuracy checks. RestrictedExpressionService now uses native numeric results; NumericWork retains operand-work, exponent and intermediate-size limits without computing reference answers. DECIMAL projection remains BigDecimal from exact stored text. Updated regression expectations for floating literals, overflow, numeric promotion, decimal division and between comparisons; added a BigDecimal variable addition case. Existing concurrent F008 implementation and tests were preserved.
>
> Verification: JDK 21.0.13 selected, Java release 17. Initial restricted Maven attempt failed on external cache lock access before compilation; authorized cache-access rerun compiled successfully and ran 53 expression cases, with one obsolete between-promotion expectation failing. Updated that expectation to native comparison behavior and reran expression/routing suites. Python launcher attempts failed before editing; the installed Python 3.11 executable performed the focused edits. No database, migration or host was started; no commit/push.
>
> **PASS:** `./mvnw.cmd -B -ntp -pl workflow-core -Dtest=RestrictedExpressionUnitTest,GatewayRoutingUnitTest,RoutingDefinitionUnitTest test` completed: 71 tests, zero failures/errors/skips. `git diff --check` passed. Maven/test processes exited. Packaging and database/browser acceptance NOT RUN for this correction; prior feature gaps remain open.
>

### Owner-started Chrome frontend walkthrough — 2026-11-05

Owner requested testing the running frontend and explicitly excluded idempotency tests. Reviewed the current implementation/handover and routing evidence UI, then used Computer Use against the existing Chrome console at http://127.0.0.1:4200/console/. Saved administrator/reviewer credentials were selected through Chrome; the owner assisted during the reviewer account switch after the browser connection refreshed. No credentials were read from local settings or exposed. Running backend artifact identity and database target were not independently inspected; this was the owner-authorized existing development UI, not the guarded integration environment or packaged-UI acceptance.

Created retained fixture sandbox.f008.browser.20261105, family 17 / version ID 21, using the complete quantity-routing topology with default before conditional transitions in the source array. Import, validation PASSED and publication succeeded through the administrator UI. Started three separate requests: 27 (quantity 20, instance 31/task 24), 28 (quantity 5, instance 32/task 25), 29 (quantity 0, instance 33/task 26). Each start paused at the assigned reviewer task. Only these newly created tasks were completed.

- PASS: quantity 20 completed at HIGH; history 31:13 selects high with only high=true and recordedQuantity LONG "20". The overlapping positive condition is absent from evidence, confirming first-match stopping.
- PASS: quantity 5 selects positive; history 32:13 displays high=false followed by positive=true, each with LONG "5".
- PASS: quantity 0 selects fallback (default); history 33:13 displays high=false then positive=false with LONG "0" and the default explanation. The earlier source-array location of default does not override conditions.
- PASS: routing details display event metadata, selected transition, ordered results, typed actual reads and exact JSON. No persistent false empty-record message was observed after loading.
- PASS: completed instance 31 shows HIGH/COMPLETED, token 3 and last history sequence 15. Its six-node graph renders the traversed path through the exclusive gateway to HIGH and marks fallback/positive/LOW unvisited. Zoom-in/reset controls worked and reset to 100%.
- PASS: all three new tasks disappeared from the reviewer's open task list; the original tasks 8/9/13 remained untouched.

No defect was found in the tested paths. Some initial snapshots captured normal asynchronous loading; an early outcome selection timed out before the contract loaded and succeeded after waiting for the pinned contract. No failed command was retried or replayed. A heading-focus attempt was unsupported; the existing zoom controls brought the graph into view.

SKIPPED by owner request: all idempotency/retry/replay tests; command recovery was only the application's automatic new-command result screen, and its Find result/Retry original actions were not used. NOT RUN in this walkthrough: unsafe-expression rejection, no-route/evaluation-error rollback UI, numeric boundary inputs, narrow-screen/keyboard accessibility, new-event dispatch/restart, packaged artifact/browser verification and full security matrix. T08 remains partial; earlier SQL/unit/build evidence retains its separate scope.

Screenshots are ignored artifacts under workflow-test-host/target/f008-browser-20261105/: high-graph.png and high-evidence.png. Test definitions/requests/instances are retained for inspection. Chrome remains signed in as reviewer on My tasks. No application/server process was started, stopped or restarted; the owner's servers remain running. Only this handover was edited for the browser test; pre-existing implementation changes were preserved. No commit/push.
