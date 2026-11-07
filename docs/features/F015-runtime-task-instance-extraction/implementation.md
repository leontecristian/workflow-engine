# Implementation — F015

## Preparation — 2026-11-07

Revision 1 DRAFT; T01 only. Starting HEAD `f686284` (committed F014), starting git status clean. The request authorizes four specification documents and an inventory, explicitly excluding implementation. No F015 code exists.

Inspected remaining console routes, runtime/audit/event/sandbox/session controllers, runtime access policy, UI transport/DTOs and task/instance/activity/list/submission components. Read governing context and the completed F013/F014 boundary. The specification inventory assigns every current console page route and remaining controller group to this slice, already-shared code, deferred extraction or host ownership.

Material findings reconciled before dependent work:

- ConsoleRuntimeController combines generic reads with business-start/completion/replay and sandbox service-effects inspection. Only the selected GET mappings move.
- TaskPage combines inspection with command freezing/execution. F015 specifies a typed read-snapshot handoff to retain host completion controls; it does not silently remove actions or extract command semantics.
- InstancePage already embeds F013's viewer; variable inspection can move independently of sandbox effects.
- Existing instance-task paging filters one bounded core page using sandbox assignment policy. Spec R02 explicitly preserves empty-page continuation and host ownership instead of exporting that policy as generic shared filtering or changing pagination in an extraction.
- Pinned task contracts contain task/instance IDs beyond the current frontend TaskContract declaration. The new shared type must describe existing wire fields, not remove them.
- Generic task actions/command recovery are still extraction backlog after F015. Sandbox request data, auth/security, audit and local delivery stores are host concerns, not mandatory future library migrations.

## Evidence and verification

Prior records were inspected as planning inputs, not newly executed evidence: F012 `943a51f`, F013 `9a82eea`, F014 `f686284`. Plan P04 identifies sections and applicability to assess at T02. No F015 runtime PASS or REUSED acceptance is claimed by reading them.

| Check | Outcome |
| --- | --- |
| Source inventory and ownership reconciliation | Completed during document preparation. |
| Static document consistency, links and whitespace | PASS — delegated source/spec review, local-link and untracked-whitespace checks; details below. |
| F015 AC01–06 implementation/runtime acceptance | NOT RUN — no implementation authorized or present. |
| Tests, builds, package installation, browser/database checks | NOT RUN — documentation only. |
| APOLO compatibility/integration | NOT RUN — outside scope. |

## Handover

Only these four documents are created. AGENTS.md, source/configuration, earlier feature records, roadmap and constitution remain untouched. No database access, host startup, process cleanup, staging, commit or push occurred. Continue with T02 only after an implementation request; DRAFT status records preparation rather than a material unresolved business requirement. Keep future command extraction separate and follow the focused stopping rule.

## Delegated preparation review — 2026-11-07

PASS: bounded read-only comparison covered the console Angular route inventory and remaining controller mappings, including the nine runtime GET operations selected for extraction, retained commands/service effects, and the already-shared F013/F014 routes. The host-owned bounded source-page filtering/continuation in ConsoleRuntimeController matches R02; the mixed task inspection/completion and instance variables/service-effects pages match the specified composition boundaries. Pinned contract IDs/version, historical access and original variable-response export requirements match the current source. No source or configuration changed.

One wording defect was corrected during review: the first R03 draft implied unknown-query rejection on every exact GET. Current exact task/contract/submission/activity mappings do not validate query parameters. R03 now preserves that behavior and limits existing query rejection to the current query-validating routes; this prevents incidental behavior change during extraction. No remaining actionable preparation defect was found.

PASS: exactly spec.md, plan.md, tasks.md and implementation.md exist in the feature directory; all seven local Markdown targets resolve. Direct checks of these untracked documents found no trailing whitespace, missing final newline or replacement characters. `git diff --check` passed, and the final review-record edit was rechecked. Repository status contains only this four-document F015 addition. No tests, builds, browser/database checks, staging, commit or push were performed; no implementation is authorized.

## Revision 2 — Complete task UI and command recovery, 2026-11-07

The owner explicitly expanded F015 to the complete task UI, command handling and recovery extraction, with business policy and transactions host-owned. This supersedes revision 1's read-only split, deferred-command statements and snapshot-to-host-form plan above. Authorization remains documentation only; no F015 code is implemented.

Inspected CommandBuffer/freezeEnvelope/commandRequest, CommandRecovery, CommandsPage and ConsoleRuntimeCoordinator/Transaction in addition to the earlier inventory. The buffer currently mixes generic recovery with sandbox-specific start fields. Revision 2 moves the generic buffer/recovery and completion validation to workflow-ui while retaining a narrow host start-body codec/dispatch adapter. Existing exported start and completion envelopes must remain importable. Host start forms/parsing/request-data binding/linking stay outside the libraries; both command kinds still use shared recovery.

Completion/replay HTTP mapping now moves into an independently activated workflow-web group that requires host execution/replay adapters. The existing coordinator, transactional proxy, business validation, trusted context and replay access checks remain host-owned. No shared direct-to-core execution or new pre-receipt read check is permitted. This changes the transport/delegation boundary, so P04 now includes focused completion/replay and one existing failure-fixture rollback check rather than treating all command evidence as reusable.

The complete shared task component replaces the previous host form/snapshot-output split. Shared recovery retains explicit retry/find-result, immutable envelope export/restore and separate current-state reads. Session-generation and command-selection guards, plus retaining a committed result after refresh failure, address concrete risks in the moved implementation. No new persistent queue or automatic retry is selected.

| Revision 2 check | Outcome |
| --- | --- |
| Source inventory and ownership reconciliation | Completed; nine selected GET mappings plus two completion/replay POST mappings, complete task and commands UI. |
| Static document consistency/links/whitespace | PASS — delegated revision 2 source/spec and static review below. |
| Implementation / AC01–09 runtime acceptance | NOT RUN — documentation only. |
| Tests/builds/browser/database checks | NOT RUN — no implementation authorization. |

Only the existing four untracked F015 documents are revised. Earlier source/feature records, AGENTS.md, configuration and roadmap remain untouched. No process/database actions, staging, commit or push. Next authorized implementation would start at T02; DRAFT is retained. Revision 1 static results above remain historical and do not verify revision 2.

### Delegated revision 2 review — 2026-11-07

PASS: compared the expanded requirements/plan/tasks with TaskPage and its template, CommandsPage, CommandBuffer/freezeEnvelope/commandRequest, CommandRecovery, ConsoleRuntimeController and the coordinator/transaction proxies. Current sections consistently include full task interaction, completion/replay mapping and shared frozen-command recovery; revision 1 read-only/deferral statements remain explicitly superseded historical notes. No remaining actionable document conflict was found.

The specified envelope shape, canonical UUID and decimal-string tokens, completion body/null comment, raw start-input preservation and host-specific start codec match the existing source. Independent optional command activation and host execute/replay delegation preserve business policy, CSRF, receipt ordering and transaction ownership. Same-user session replacement, selected-command response isolation and success followed by refresh failure are explicit changed-boundary checks; focused P04 completion/replay/rollback coverage is proportional to the expanded slice. Concrete adapter/provider signatures remain a T02 implementation-readiness task, not authorization to implement.

PASS: exactly four feature documents, all seven local Markdown targets, direct untracked-file whitespace/final-newline/encoding checks and `git diff --check`. Rechecked the final revision 2 static-result edit. Repository status remains only these four untracked documents. No source/configuration edits, tests, builds, browser/database actions, staging, commit or push occurred. Revision 2 remains DRAFT and implementation/runtime acceptance remains NOT RUN.

## T02 readiness — 2026-11-07

READY against completed F013/F014 at f686284; concrete contracts and one-writer ownership are recorded in plan P01/T02. Owner implementation request supersedes preparation-only restrictions above. Requested GPT-6.1 Sol/medium workers are available and assigned backend and UI implementation. No test/build/database activity yet.

REUSED: F013 9a82eea final acceptance/package/NgModule sections for unchanged viewer and packaging conventions; F014 f686284 final acceptance for lazy secondary entry and host adapter conventions; F012 943a51f final acceptance/additional browser observations for unchanged outcome semantics and exact rendering; F006 implementation section 4 AC14/16/19 as present at f686284 for retained coordinator transaction, receipt replay and rollback architecture. Those historical observations do not verify new mappings or moved recovery; P04 requires fresh focused boundary checks. No APOLO compatibility claim.

## Implementation and integration — 2026-11-07

T03/T04 code is present. Two GPT-6.1 Sol/medium implementation workers delivered independently activated web reads/completion mapping and the lazy runtime component entry. The lead integrated root read/command contracts, shared immutable buffer/recovery, host start codec/dispatch, synchronous session bridge, route activation and removal of duplicate host read/command implementations. Host request forms retain business fields and now clear sensitive start state and suppress late command navigation on replacement sessions.

Concrete compatibility review preserved monitor authorization before query parsing and translated host ConsoleException/wrapped SandboxRequestConflict at the command adapter rather than leaking host exception types into workflow-web. Normal completion still calls the coordinator directly without exact-read precheck; replay retains its host task check. Core, coordinator/transaction production bodies, schema and APOLO sources are unchanged. Runtime components use flat files within the single secondary entry rather than speculative per-component directories; public package boundaries remain as planned.

Focused verification is centrally assigned to a separate GPT-6.1 Sol/medium verification worker. Builds/database runs are serialized; the worker owns checks and browser/temporary-process cleanup. Static implementation-worker checks are preliminary and do not substitute for final combined-state review. Runtime acceptance remains pending until the recorded P04 checks finish.

Verification feedback: the first library/console production build passed (three entries; 919.20 kB initial console bundle, existing 900 kB warning below unchanged 1 MB hard budget). An isolated coexistence fixture lacked its catalog-service double, and one retained task test still imported the removed host Buffer facade; these are test harness corrections, not waived assertions. Read-only integration review also found delayed host request list/create callbacks could restore start context after session replacement. The lead added scope guards and synchronous loading reset plus a focused regression. Only affected frontend checks/console packaging and failed Java selection need repetition; the unchanged successful library build remains applicable after its earlier selected-result fix.

Fresh isolated Java PASS: root Maven wrapper selected WorkflowRuntimeWebUnitTest, ConsoleRuntimeAdaptersUnitTest, ConsoleInstancesUnitTest, ConsoleSequentialStagesUnitTest, ConsoleScalarServiceUnitTest, ConsoleRuntimeUnitTest, ConsoleErrorsUnitTest and WorkflowViewerUnitTest under Corretto JDK 21.0.13. Final effective selection: 32 tests, zero failures/errors/skips (5 runtime web, 18 host, 9 unchanged viewer checks retained from the first run). A sandbox Maven cache-lock failure preceded an approved retry; the first runnable selection exposed the missing coexistence catalog mock, then only affected suites were rerun. No database was used.

Fresh sandbox request follow-up PASS: 9 tests after the delayed-session list/create fix. NgModule dependency installation passed on permitted retry after sandbox EPERM; its first compile exposed a fixture-only TypeScript status-literal inference mismatch, being corrected before the fixture build repeats. These failures do not establish acceptance of their failed checks.

Final host-start review found the shell mounts routes before session initialization. The new scope guard therefore also needs an initial identity-arrival load, while preserving an explicit initial request deep link. That lifecycle correction and a focused test are now included; later session replacement still clears the start context. This affects only the host request page. Its test/build and final asset copy repeat for that concrete change; unchanged library/Java checks and the guarded completion setup are retained.

## Final implementation and focused acceptance — 2026-11-07

F015 revision 2 is IMPLEMENTED. T02 reconciled completed F013/F014 before worker edits. T03/T04 deliver independently activated selected runtime GETs and completion/replay POSTs, required host adapters, the lazy `@workflow-engine/ui/runtime` entry and eight components, shared immutable buffering/recovery, host-specific exact start codec/dispatch and synchronous session invalidation. Sandbox pages are wrappers; variable inspection composes beside host service effects. Host business policy, trusted context, assignment enforcement, coordinator/transaction proxies, audit/durable handoff and start forms/endpoints remain host-owned. Read-only tasks need no command provider.

Fresh checks: 32 isolated Java tests; 56 unique focused frontend tests (the final affected selection includes 8 buffer, 3 scalar-input and 10 host-request tests); all three Angular library entries; NgModule production consumer at 856.68 kB initial; console production under unchanged 1 MB hard budget. Exact commands, browser/guarded integration/package outcomes and cleanup follow below. Historical failures and proportional reruns remain recorded above, rather than being presented as acceptance.

The guarded setup uses only localhost:1433/apolo_workflow_test and existing project migrations. Before the browser completion, owned request 80 / instance 619 was APPROVED after accepted completion, while request 81 / instance 620 remained IN_REVIEW after configured post-result rollback. Automated assertions separately inspect unchanged instance token, absent submission and absent completion receipt following rollback. These observations supplement the changed delegation boundary; they do not constitute a broad engine/concurrency/delivery campaign.

REUSED evidence remains as scoped in T02: F012 943a51f for unchanged task/outcome semantics and exact rendering, F013 9a82eea for unchanged viewer conventions/history/graph, F014 f686284 for shared helpers/lazy-entry composition and build conventions, and F006 section 4 AC14/16/19 at f686284 for retained host transaction/receipt architecture. Fresh checks cover new mappings, recovery state, session/selection races and changed consumer wiring. No prior record is relabelled as a fresh PASS. APOLO integration is NOT RUN and outside scope; no historical owning-feature acceptance is reopened.

### Final fresh results and commands

- PASS: **32 isolated Java tests**, Corretto 21.0.13 with Java release 17. Root command: `./mvnw.cmd -pl workflow-test-host -am '-Dtest=WorkflowRuntimeWebUnitTest,ConsoleRuntimeAdaptersUnitTest,ConsoleInstancesUnitTest,ConsoleSequentialStagesUnitTest,ConsoleScalarServiceUnitTest,ConsoleRuntimeUnitTest,ConsoleErrorsUnitTest,WorkflowViewerUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' test -B -ntp`. After the coexistence fixture correction, the affected rerun omitted only unchanged WorkflowViewerUnitTest; its 9 passing tests were retained from the first run. Runtime web 5 plus host 18 then passed with no failures/errors/skips. No database or blanket core suite.
- PASS: **56 unique focused frontend tests**, pinned Node 22.22.0/npm 10.9.4. From workflow-test-host/frontend, the combined command below yielded 43 passing tests plus two file-load failures from compiler import order. Correcting that harness and rerunning only buffer/scalar/request files yielded 21 passing tests (8+3+10); the later isolated commands suite passed 8 tests including the added delayed-copy check. Final unique total accounts for overlap rather than adding repeated tests. Earlier stale task Buffer imports were corrected without dropping assertions.
- PASS: library `npm run build` in workflow-ui, all primary/administration/runtime entries. Its one affected rebuild included the selected-result reset; later host/fixture changes did not rebuild the unchanged library. Console `npm run build` after final host-session correction: **920.37 kB** initial, below unchanged 1 MB hard limit; CSP check passes. Existing 900 kB warning and Sass deprecations remain. NgModule consumer `npm ci --no-audit --no-fund` and `npm run build`: **856.68 kB** initial. Its fixture status-literal type correction repeated only the affected fixture build.
- PASS: **SharedRuntimeIT, 1 guarded integration test**, no failures/errors/skips. Root command: `./mvnw.cmd -pl workflow-test-host -am '-Pconsole-ui,integration-tests' '-Dskip.npm=true' '-Dtest=NoSelectedUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=SharedRuntimeIT' '-Df015.browserHold=true' verify -B -ntp`. Only apolo_workflow_test, existing migrations, moved reads/access/CSRF, committed completion, frozen replay lookup, absent receipt without execution and post-result rollback. No engine/start-link/concurrency/delivery matrix. Backend/runtime sources were stable throughout; the independent host-start initialization correction was unit/build-checked without repeating this unchanged delegation setup.
- PASS: sandbox browser submitted task 608 through the shared form and navigated to recovery, preserving original UUID/comment/token. NEW_COMMITTED_RESULT and the separate current-work view rendered; Find result produced REPLAY_ONLY_LOOKUP. Envelope download/restore retained the original values and did not execute. Variables retained `9007199254740993.00`; activity navigation showed COMPLETED/APPROVE. Existing NgModule browser demonstrated alternate host headings/messages, custom command transport (call count 0→1), alternate commands navigation, exact ID/token and read-only task composition with no completion controls. Both browser warning/error logs were empty.
- PASS: final asset-only root `./mvnw.cmd -pl workflow-test-host -am -Pconsole-ui '-Dskip.npm=true' '-Dmaven.test.skip=true' package -B -ntp`. This incorporated the final independently corrected host-request assets without repeating npm/tests/database execution. All **23** packaged console files match final dist paths/SHA256 with no stale files; the included workflow-web JAR contains both runtime controllers and independent activation configurations.

Exact combined frontend selection:

```powershell
node --experimental-strip-types --test unit/command-buffer.test.mjs unit/scalar-inputs.test.mjs src/app/data/command-recovery.test.mjs src/app/data/runtime-read-adapters.test.mjs src/app/data/runtime-command-adapters.test.mjs src/app/data/console-api.test.mjs src/app/pages/runtime/inspection.test.mjs src/app/pages/runtime/instances.test.mjs src/app/pages/runtime/scalar-inspection.test.mjs src/app/pages/runtime/viewer-wrappers.test.mjs src/app/pages/tasks/task.test.mjs src/app/pages/commands/commands.test.mjs src/app/pages/sandbox/requests.test.mjs src/app/app.routes.test.mjs
```

Affected reruns: `node --experimental-strip-types --test unit/command-buffer.test.mjs unit/scalar-inputs.test.mjs src/app/pages/sandbox/requests.test.mjs` and `node --experimental-strip-types --test src/app/pages/commands/commands.test.mjs`. The latter proves copied frozen text and delayed-copy exclusion across command selection/session replacement. No production source changed for that test.

### Limits and cleanup

Browser clipboard access is **NOT RUN** because the automation tool reports its clipboard bridge was removed. Actual envelope download/restore passed, and isolated clipboard-value/lifetime checks pass; this narrow tool limitation is not a claimed browser clipboard PASS. Required focused AC01–09 observations are covered; APOLO integration remains NOT RUN/outside scope. Historical engine/integration gaps stay with their owners.

The test's scoped @AfterEach cleanup completed successfully for its owned requests, instances, variables/tasks/activities/history/receipts/outbox, definition/version and any created catalog row. No unowned catalog row was changed or removed. The held test JVM PID 38452 exited; its port 56126 and the owned fixture server port 4301 have no listeners. Owned static-server sessions were stopped, both created browser tabs closed, and the two owned Downloads envelopes removed after their equal hashes were checked and an ignored local test copy retained. Logs/exports remain ignored under .local; generated assets remain ignored. No development database access, normal-host redirection, unrelated process termination, reset/drop, staging, commit or push occurred. Core and AGENTS.md are unchanged.

The lead reviewed actual contracts, production sources and tracked diffs; a bounded independent integration review found and helped close the host-request session callbacks. Final verifier static results follow. T05 is complete; no unrelated acceptance or broader test campaign is selected.

### Final delegated static review

PASS: final combined-state review covered 106 changed/untracked text files and 163 local Markdown targets. No trailing whitespace, missing final newline, conflict marker or missing local path. `git diff --check` passes; existing CRLF normalization notices are unchanged. Replacement-character occurrences in the older README/index/roadmap text match HEAD exactly; no new encoding defect was introduced. Core and AGENTS.md are unchanged, cached diff is empty, and no generated artifacts are included. The verifier reviewed contract/scope/evidence consistency and the final results/limits above.

T06 is complete. All requested implementation and relevant focused checks are complete with the narrow browser clipboard limitation explicitly recorded. No further build/runtime checks are selected under the stopping rule. Changes remain uncommitted; no push was made.
