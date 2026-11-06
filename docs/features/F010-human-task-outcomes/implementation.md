# Implementation record — F010

## Current state

Revision 1 is IMPLEMENTED following the owner request on 2026-11-06. T02–T05 are complete. The subsequent interactive Chrome walkthrough below resolves the earlier browser-control blocker and records its coverage and limits. No commit or push. Historical preparation and prior-feature evidence remain unchanged.

## Preparation — 2026-11-06

Inspected the clean working tree, roadmap, feature records, governing context and language outcome rules. Reviewed current schemas, MinimalDefinitionLoader, ExecutionValueDefinitionModel, RoutingDefinitionModel, MinimalWorkflowDefinition, WorkflowExecutionService, RuntimeServiceProgression, the host task-contract endpoint and task/graph UI references.

Findings shaping the plan: the language already selects at most 16 outcomes and exactly one route per outcome. Current Java Human stores one Outcome, loaders read outcomes[0], schemas cap outcomes at one, non-gateway graph paths are linear-only, and traversal chooses the first outgoing edge. The console contract already enumerates outcomes, but its form uses a selector/generic completion action. These are implementation targets, not defects against the earlier single-outcome feature scopes.

Created the four process documents, including a complete future minimal fixture, requirements/errors, graph rules, history/replay semantics and acceptance coverage. Updated feature index, roadmap and governing source/constitution pointers. No API/schema/runtime code, dependency, migration or data changed.

## Preparation verification (historical)

PASS: reviewed the specification/example and integration targets; checked all 110 local links across eight changed/new Markdown files, whitespace, 12 unique acceptance rows and exactly four feature documents. Parsed the embedded JSON example successfully; this establishes JSON syntax only, not acceptance by the future validator. git diff --check passed. Unit tests, builds, database integration and browser scenarios: NOT RUN — documentation only. No host or temporary server started; no database accessed. No commit or push.

## Implementation — 2026-11-06

The three structural schemas support 1–16 outcomes. MinimalDefinitionLoader sends all supported definitions through ExecutionValueDefinitionModel and the shared RoutingDefinitionModel graph validator, including graphs without gateways or variables. One START, one SINGLE human stage, complete exact-key routes, reachable acyclic topology and human-path requirements remain enforced. Diagnostics locate duplicate declarations, invalid routes and missing outcome routes; early text/key checks now retain JSON pointers. Validation provenance uses wf-human-outcome-validator-v1 and the selected schema digest, invalidating prior validation contexts. Publication and runtime capability checks require support for every declared outcome.

MinimalWorkflowDefinition.Human retains an immutable ordered outcome list and exact lookup. Completion resolves the submitted key and its own comment rule. Runtime traversal receives that key explicitly and requires exactly one matching human edge; other automatic edges also require uniqueness. Task/activity facts, human transition history and delivery carry the selected key. Existing command/result formats, full fingerprints, receipts, pinned reads, early aggregate claim and host transaction remain in place. No migration, public command change or compatibility shim was added.

The task page renders one labeled action per pinned outcome, marks required comments, blocks blank required comments and pending/non-OPEN actions, and freezes the explicit key/comment/token in the existing command buffer. It clears stale task data on route changes. Sample capabilities and the capabilities endpoint advertise COMPLETE/APPROVE/REJECT. Existing graph and task-contract readers already enumerate edges/outcomes; focused tests and real HTTP checks verify them without changing their formats.

Runnable fixtures: [approve-reject.json](approve-reject.json) has no gateway, variables or services. [booking-outcomes.json](booking-outcomes.json) adds distinct branch service nodes using sandbox.refreshBooking and retains F009 JSON/gateway behavior. Use start inputs `{"booking":{"amount":12500.00,"department":{"code":"SALES"},"obsolete":true}}` for the latter. Only the approved path evaluates its gateway; rejected completion reaches REJECTED normally.

## Verification — final worktree, 2026-11-06

- **PASS — isolated Java:** JDK Corretto 21.0.13, Java 17 release target, root wrapper. The final `./mvnw.cmd -B -ntp '-Pintegration-tests,console-ui' '-Dskip.npm=true' verify` passed 183 core and 50 host unit tests, plus 46 database integration tests, and packaged both modules with the fresh isolated console assets. npm was installed/built/tested separately as below. The preceding Java-only verification passed 183 core/49 host unit tests and the same 46 SQL tests before adding the capabilities-response test. Surefire remains unit-only; Failsafe discovers the guarded database tests.
- **PASS — SQL Server:** only localhost:1433/apolo_workflow_test, via DatabaseIntegrationTest/IntegrationDatabaseInitializer. RestrictedRoutingIT includes ten F010 invocations: plain and JSON approve/reject routes with reordered versions, full completion/submission/transition evidence, exact comments, replay/conflicts, invalid/blank/null submissions, competing outcomes, rollback after selected-branch host/JSON effects, wrong assignment, immutable publication and future start after catalog disabling. Existing F008/F009 tests also pass, including exact scalar/JSON values, native expression behavior and budgets. The final managed instance version matches the stored version. Liquibase reported ten existing changesets and zero new changesets; no reset or unrelated cleanup occurred.
- **PASS — frontend:** pinned Node 22.22.0/npm 10.9.4; `npm run test:unit` passed all 75 tests. This includes explicit outcome capture, per-action comments, invalid/non-OPEN commands, pending click exclusion, retained frozen recovery payloads and route reuse. An isolated copy under ignored .local used `npm ci --no-audit --no-fund`, `npm run build` and the same unit suite. Production build and CSP checks passed. Initial bundle 930.94 kB exceeds the 900 kB warning budget by 30.94 kB; the build succeeds.
- **PASS — packaging:** root wrapper `-Pconsole-ui -Dskip.npm=true -Dmaven.test.skip=true package` packaged both modules using freshly built isolated frontend assets copied into the normal generated dist directory. npm was skipped only in Maven; its pinned install/build had already passed in isolation.
- **PASS — real host HTTP:** temporary ignored F010BrowserHarness used the integration-test profile and IntegrationDatabaseInitializer, binding only 127.0.0.1:18081. Four test-owned instances exercised both supplied fixtures and both outcomes. Session/CSRF login, unauthenticated 401, missing-CSRF 403, wrong-assignee 403, blank comment and unknown/case-mismatched outcome 400, ordered pinned contract, completed result, exact submission, authorized replay, actual/untaken graph edges and only the selected service effect were checked. Host ConsoleRuntimeTransaction also read the final submission inside the completion transaction. The fresh production bundle was served. This is HTTP evidence, not browser rendering evidence.
- **BLOCKED — interactive browser:** Computer Use found Chrome, but stopped before interaction because it could not determine the current browser URL confidently enough to enforce policy. No browser rendering, clicking, keyboard/responsive or real browser recovery acceptance is claimed. Unit/HTTP coverage does not close that part of AC11.
- **PASS — cleanup:** temporary host PID 11636 recorded F010_BROWSER_FIXTURES_CLEANED, then gracefully shut down and closed its EntityManagerFactory. Only its tracked instances, requests, definitions and newly created catalog row were removed. No normal development host was started, restarted or stopped. No dev database was accessed.

Verification corrections retained: the first new unit run exposed missing outcome delivery metadata and early pointer-free text diagnostics; both were fixed and subsequent suites passed. The initial sandboxed Maven wrapper failed before Maven started; approved wrapper execution succeeded. Shared frontend Maven npm-ci attempts failed with Windows EPERM on the in-use esbuild executable. No other process was stopped; isolated installation/build resolved verification. A temporary HTTP script initially expected a relative login redirect; accepting the equivalent absolute Location fixed that script before the scenarios ran.

The additional capabilities-response unit fixture initially omitted its reviewer configuration; supplying that required fixture value fixed the test, and the final full run passed. Final review passed `git diff --check`, added-file whitespace checks, 160 local Markdown links and both fixture JSON syntax checks. Both fixtures were also published and executed on the guarded host, beyond syntax checking. Exactly four feature process documents remain; the two JSON files are runnable technical artifacts.

Local dependency repair after handover, 2026-11-06: the failed shared npm-ci attempts had left frontend/node_modules incomplete, causing the owner's `ng serve` to report a missing @angular/build:dev-server package. Reinstalled the unchanged lockfile with pinned npm using `ci --include=dev --no-audit --no-fund` (409 packages). Verified `ng serve --port 14210 --no-open` compiled successfully and served `/console/` with HTTP 200; stopped that temporary server afterward and confirmed the port was no longer listening. No package/lockfile change, backend startup or database access was required.

Coverage: AC01–03 have isolated graph/schema evidence; AC04–06 and AC08/10 have SQL plus HTTP evidence; AC07/09 have SQL rollback/concurrency evidence; AC11 has component/graph/HTTP evidence with interactive acceptance BLOCKED; AC12 has capability/context, immutability/catalog/assignment and existing scalar/JSON regression evidence. This is local test-host verification, not APOLO provider/deployment acceptance.

## Remaining work

The interactive portion of T05/AC11 was subsequently exercised as recorded below. Retain the normal host/database authorization boundary for any later walkthrough. APOLO dependency alignment, transaction/provider integration and production acceptance remain separate gates. No additional runtime scope is implied; no commit or push was made.

## Interactive Chrome acceptance — 2026-11-06

The owner requested testing F010 in the already-running app. Chrome exposed `http://127.0.0.1:4200/console/#/definitions` with the administrator signed in. The existing F010 worktree changes were preserved. No runtime code was changed, host started/restarted/stopped, database reset, or credentials/private configuration read. The owner supplied the reviewer login for assigned-task completion.

### Setup and results

Imported the supplied fixtures with only identity/name changes: family 21/version 25, `sandbox.f010.browser.plain.20261106` (`F010 browser approve or reject`), and family 22/version 26, `sandbox.f010.browser.booking.20261106` (`F010 browser booking outcomes`). Both published with Validation PASSED. Four new sandbox requests/instances were created; existing definitions and instances were untouched.

- PASS — plain APPROVE: request 32, instance 36, task 29; no comment, COMPLETED / APPROVED, final token 3 and history sequence 8.
- PASS — plain REJECT: request 33, instance 37, task 30; comment `  F010 browser rejection: insufficient detail.  `, COMPLETED / REJECTED, final token 3 and history sequence 8. Rejection is a normal successful completion, not cancellation/error.
- PASS — booking APPROVE: request 34, instance 38, task 31; no comment, COMPLETED / SALES, final token 3 and history sequence 15.
- PASS — booking REJECT: request 35, instance 39, task 32; comment `F010 JSON branch rejected by reviewer.`, COMPLETED / REJECTED, final token 3 and history sequence 13.

Both booking starts used raw inputs `{"booking":{"amount":12500.00,"department":{"code":"SALES"},"obsolete":true}}`. Both final variable reads retained the exact service replacement `{"amount":12500.00,"department":{"code":"SALES"}}`, with obsolete removed. These observations cover AC03/04/06 and the browser portion of AC11 for the supplied plain/JSON paths.

### UI, identity and evidence

- PASS (AC05/11) — pinned buttons render in declaration order: Approve, then Reject - comment required. With an empty comment, Reject is disabled and Approve enabled. Filling three spaces keeps Reject disabled and Approve enabled. Supplying a reason enables Reject. No rejected blank command was sent by the UI; server-side blank validation remains established by the earlier HTTP/SQL checks.
- PASS (AC11) — while the Reject command was pending, the accessibility snapshot showed both actions disabled. After completion, refreshing task 30 showed both actions disabled. Visual inspection confirmed the explicit actions/shared comment field and required-comment wording.
- PASS (AC08/10/11) — recovery retained task 30's explicit REJECT key, original token 1, UUID `bf0b4800-d55f-46c0-bdac-7b8cc418b06e`, and the exact untrimmed reason. Retry original returned REPLAYED_ORIGINAL_RESULT with the same REJECTED result/token/history range. The accepted-submission technical response retained both leading and trailing spaces, outcome REJECT and history sequence 5.
- PASS (AC08) — in a fresh browser page session, restored a completion envelope with that same UUID/task/token/comment but outcome APPROVE. Retry returned HTTP 409 COMMAND_ID_CONFLICT, correlation `1c545e4a-4bb6-451b-8332-fc54fb046bbb`. The existing session's frozen command stayed REJECT when an altered envelope was attempted there; that attempt alone was not counted as a server conflict test.
- PASS (AC06/10/11) — instance 38's graph marks approve/RECORD/ROUTE/sales traversed and the rejection branch not reached. Instance 39 marks reject/REJECT_RECORD/REJECTED traversed and RECORD/ROUTE/SALES not reached. Visual inspection at 50% graph zoom distinguished green completed and gray unselected nodes/edges. Instance 39's history has one SERVICE_TASK_COMPLETED at sequence 10 for REJECT_RECORD and no gateway event. Exact service evidence retains the original input and replacement. This is browser history evidence, not an independent query of the host effect table.
- PASS (AC10) — instance 39 history sequences 7/8/9 agree: TASK_COMPLETED contains REJECT and the reason; ACTIVITY_COMPLETED contains TASK_OUTCOME/REJECT; human TRANSITION_TAKEN contains REJECT with source REVIEW, target REJECT_RECORD and transition reject.

An overly batched history-navigation attempt timed out and reset automation state; the tab was recovered and the three history records above were inspected individually. This was an automation failure, not a failed workflow command. No runtime defect was observed in the exercised scenarios.

### Scope and handover

T05's previously blocked interactive walkthrough is complete for these scenarios, alongside the earlier automated/HTTP evidence. This session did not rerun unit tests, builds or SQL integration suites; prior results remain dated evidence. Browser-only unknown/case-mismatched submissions, changed-comment replay, competing commands, rollback injection, 16-outcome presentation, keyboard-only navigation, responsive sizes and actual host-effect/outbox tables were NOT RUN here. Their existing automated/HTTP coverage is separate; no APOLO or production acceptance is implied.

The four completed instances and two published fixtures are retained for inspection. Chrome was left on instance 39 in the reviewer session. The owner's application remains running; no temporary verification process remains. Final documentation diff/whitespace and local-link checks passed. No commit or push was made.
