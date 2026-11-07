# Implementation record — F016

**Date:** 2026-11-07. **Specification:** revision 2, IMPLEMENTED. **Runtime implementation:** complete; scoped verification passed. The earlier sections retain documentation preparation and contract-closure history.

## Actual changes

T01 recorded the owner's selection in revision 1: one shared SINGLE task, current group/role membership, direct completion without claiming. Constitution 6.1.0, constraints, design/source-precedence notes, assumptions, roadmap and feature index reflect the extension. Revision 1 left the contracts open; revision 2 closes them through T02. Claiming/releasing remains the next separate feature; no ID or claim lifecycle is invented here.

Inspected current resolver/capability validation, task entity, completion ordering, strict receipt/evidence codecs, task queries, sandbox access/coordinator/transactions, shared web contracts, task UI and migration identities. They currently assume assignedUserRef; documentation does not imply shared assignment is already supported. This task changed no production code, schema, tests or runtime configuration. Existing untracked `.agents/` content is unrelated and preserved. A formatting-only edit to SingleAssignmentResult.java appeared independently during readiness work and was left untouched.

## Revision 2 contract closure

The owner subsequently confirmed O05's two-column table design, preserving USER migration and separate completing actor. The specification and database design now record that explicit confirmation. Revision 2 remains READY; no contract change or implementation authorization is implied.

The owner answered two behavioral questions: a successful fresh membership check allows the in-flight command to finish despite subsequent revocation, and former members may recover their own successful result while current host business access permits it. The owner also clarified that command recovery screens are for testing, not final users. O02/O04 record these decisions; backend idempotency remains intact.

Engineering closure covers typed targets/provider interfaces and declared resolver capabilities (O01), authoritative per-invocation membership and sandbox fixtures (O02), bounded source scanning plus read/display policy (O03), same-actor recovery and diagnostic opt-in UI (O04), and preserving migration with strict legacy/current evidence and receipt readers (O05). Existing command normalization remains v3; new result snapshots use v4. Plan, tasks, design notes, roadmap and feature index now identify revision 2 as READY. These are specified changes, not executed changes.

## Verification

- Revision 1 PASS (historical): documentation content/scope review; 14 changed/new Markdown files and 228 local link targets checked with no missing paths, trailing whitespace or missing final newline. `git diff --check` passed; normal LF/CRLF normalization notices remained. New F016 files were included explicitly because they were untracked.
- Revision 2 PASS: reviewed settled contracts against the current code paths and SQL definitions; checked 15 changed/new Markdown files and 238 local links (no missing paths, whitespace defects, conflict markers or missing final newlines). `git diff --check` passes. Status/closure scan confirms OPEN occurrences describe task state and historical draft status is labelled. These static checks establish specification consistency only.
- Unit tests, builds, database/migration execution and browser acceptance: NOT RUN, documentation-only scope. AC01–07 remain unexecuted.
- No host/test process was started; no database was accessed. No commit or push requested or performed.

## Remaining work

T02 is complete: no material F016 contract remains open. The subsequent implementation run below supersedes the earlier T03–T05 pending status. At readiness closure, migration/provider/transaction/browser behavior was NOT RUN; APOLO's authoritative membership adapter and production business access remain integration work. Historical feature acceptance is unchanged; this task closes no runtime or APOLO verification gate.

## Implementation run — 2026-11-07

The owner requested T03–T05 after readiness closure. All O01–O05 contracts remain approved; no material scope decision is open. Existing unrelated architecture/documentation and SingleAssignmentResult formatting changes are preserved. Work is split across core storage/evidence, host/web, frontend, and lead-owned eligibility/execution/query integration. No commit or push is authorized.

Fresh verification is focused on the changed assignment, migration, access, receipt and task-page boundaries. Final results and scoped limitations follow.

### Delivered code (T03/T04)

- Typed USER/GROUP/ROLE target DTOs, resolver kind descriptors and publication provenance; mandatory read-only host membership provider with separate missing-configuration/lookup-failure categories. Completion checks current membership once after the early instance write and pinned creation/outcome validation, before closure/effects; successful replay remains ahead of state-dependent checks.
- Preserving core 004 migration, explicit JPA fields and two OPEN indexes; strict retained v2/current v3 creation history, retained v3/current v4 receipts, and v2 TASK_CREATED event payload. Command fingerprints remain v3.
- Bounded eligible inbox and filtered instance/submission pages, exact submission/history authorization, actual-completer historical access, fresh coordinator replay authorization, host GROUP/ROLE fixtures and second reviewer. Added Finance GROUP and approver ROLE import samples.
- Shared task UI uses typed targets, canComplete/assignmentLabel, default-hidden diagnostics and in-place success/conflict refresh. Sandbox opts into diagnostics; read-only usage needs no command provider.

### Verification results

- PASS: Corretto JDK21.0.13 selected; Maven compiler release17. Initial core run exposed receipt entity's old schema-version guard and stale test expectations; corrected, with affected reruns passing. Reactor unit phase then passed core209/web28/host75 tests (zero failures/errors/skips).
- PASS: final focused core run, `./mvnw.cmd -B -ntp -pl workflow-core '-Dtest=SharedAssignmentUnitTest,RuntimeCommandsUnitTest' test`, 6 cases. Added the resolver/provider coverage after identifying that the earlier tests did not exercise actual descriptor disagreement/missing support; no unrelated suites were repeated for this addition.
- PASS: frontend task tests8; Angular library build and host production build. Existing Sass deprecations/bundle-budget warning remain.
- PASS: Terra's browser observation in the NgModule consumer: default-hidden task diagnostics, read-only task without completion form, one command call, successful confirmation and no diagnostic navigation. Its server was stopped.
- PASS: independent fresh Sol Medium read-only review of assignment/evidence, execution ordering, bounded cursor logic, provider/capability agreement, host replay/access, web mapping and task UI; no actionable defects.
- PASS: guarded integration startup applied `004-shared-task-assignment` to localhost:1433/apolo_workflow_test, one new +12 existing changesets, released migration lock. This alone does not establish provider/concurrency/rollback behavior.
- Historical BLOCKED, subsequently resolved: reactor command `./mvnw.cmd -B -ntp -Pintegration-tests '-Dit.test=SharedAssignmentIT' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Df016.browserHold=true' verify` reached Hibernate schema validation but SQL Server stopped responding. Thread dump shows JDBC getTables waiting; TCP and shared-memory read-only login probes timed out. At diagnosis, no test method had started. SQL Server subsequently recovered while cleanup was pending; PID26936 finished initialization and briefly created fixtures before the verified owned JVM was stopped, causing fork termination and an unsuccessful overall verify. Final inspection identified and removed only its exact marker/definition/instance fixtures. Owner chose to restore SQL Server; no service restart or unrelated process termination was performed by agents.
- SharedAssignmentIT source covers preserving SQL backfill against connection-local representative pre-F016 task rows, retained USER creation/receipt decoding and completion, GROUP current membership and after-check revocation, rollback, own replay, different-actor conflict, empty/deleted targets, concurrent ROLE completion and a guarded browser hold. All three scenarios passed in the restored run described below.

No commits, staging or pushes. Local logs/fixtures remain ignored under .local/ and target/.

### Final acceptance and handover

- PASS: restored focused reactor `./mvnw.cmd -B -ntp -Pintegration-tests '-Dtest=SharedAssignmentUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=SharedAssignmentIT' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Df016.browserHold=true' verify`: 5 focused unit cases and all3 SharedAssignmentIT scenarios, zero failures/errors/skips, all modules/package phases successful. Liquibase reported0 new/13 existing changesets, Hibernate schema validation passed. Covers AC01–04/06/07 storage/transaction/actor boundaries, including different-outcome competing ROLE commands (one committed result/receipt/completion), GROUP revocation before/after the single fresh decision, rollback of version/task/submission/receipt after post-result failure, valid empty/deleted targets and strict legacy USER evidence/receipt compatibility.
- PASS: targeted follow-up `./mvnw.cmd -B -ntp -Pintegration-tests '-Dtest=NoF016RepeatedUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=SharedAssignmentIT#retainedUserEvidenceAndMigrationBackfill' '-Dfailsafe.failIfNoSpecifiedTests=false' verify`: one compatibility scenario, zero failures/errors/skips. Added v2 TASK_CREATED outbox/inbox round-trip and representative queued v1 payload delivery without rewriting bytes; no repeated browser or unrelated unit suites. It also reran its affected preserving-backfill/legacy-USER scenario.
- PASS AC05/R08: Terra observed backend-served production console on the guarded test fixture. My tasks and task614 displayed GROUP: sandbox.finance; the eligible reviewer could Approve directly, saw the sandbox diagnostic committed result, and returning to My tasks removed614 while retaining other pending work. Instance627 reached APPROVED/COMPLETED. The independent NgModule observation above covers default non-diagnostic success and read-only composition. The later stale “directly assigned” list description was corrected to “eligible”; only its affected library build was repeated.
- PASS AC02/05/06: isolated core/web/host checks cover current membership replacement/deletion, missing/duplicate/failing providers, exact USER identity, declared-kind mismatch, no lookup during capability checking, source500-budget empty page/continuation, access-before-membership, no skipped eligible row, task/completer/admin access, sibling submission/history protection, lookup503 mapping and business-access denial before coordinator receipt lookup. Core and host checks use doubles; actual SQL completion/replay/rollback evidence comes from the guarded scenarios above.
- REUSED: F015 revision2 handover for unchanged frozen-envelope/recovery-page internals and independent HTTP activation (CommandRecovery/envelope normalization remain unchanged); fresh tests/browser checks above cover changed task-page diagnostics and typed transport. REUSED: F011 revision1 handover for unchanged sequential routing algorithm/pinned-stage semantics; this does not claim a new multi-stage shared-target integration walkthrough. Existing sequential unit suites passed in the209-case core run.
- PASS: final intended-diff review, `git diff --check`, explicit untracked-file whitespace/content checks, local documentation links and both import-sample JSON shapes. No conflict markers or broken links.

T03–T05 are complete. No material F016 decision or required scoped verification remains open. APOLO membership/business-access implementation, production dependency/transaction acceptance and claim/release remain outside F016. Production deployment must stop old writers before applying004; mixed old/new writers and downgrade are unsupported by this slice. Only localhost:1433/apolo_workflow_test was used; the development database was not accessed.

Owned Failsafe JVMs26936/34748/38308 have exited; browser tabs and the independent NgModule server are closed. Successful tests cleaned their fixtures; exact interrupted-run fixtures were removed after identity/timestamp checks. Final read-only probe found0 F016 definitions, requests, audits and instances, and0 held migration locks. No unrelated process was stopped or SQL service restarted by agents. No changes were staged, committed or pushed.

## Owner-started Chrome follow-up — 2026-11-07

The owner requested Chrome verification against the already-running console at127.0.0.1:4200, initially prohibiting another server process. The existing admin session imported/published sandbox.f016ChromeGroup v1 (definition29/version33), then created request43. Start command d9b51b86-9925-4c45-99e9-11c9bf9e74e1 returned500 HOST_FAILURE; the existing IntelliJ backend log identified HttpRequestMethodNotSupportedException (POST). Replay-only lookup found no committed receipt.

Diagnosis: the shared viewer high-priority GET /instances/{id} mapping rejected POST /instances/start before the normal host controller could run. New isolated MVC regression reproduced405 instead of the host200. WorkflowViewerConfiguration now falls through on HTTP-method mismatch, preserving host route ownership; its10-test viewer suite passes with no listener/database. Commands/logs: .local/f016-chrome-route-before.log (expected FAIL), .local/f016-chrome-route-after.log (PASS). No broad matrix was repeated.

The owner then explicitly authorized restarting from IntelliJ. Rerun of the existing Workflow console (local) configuration stopped PID37532 and started replacement PID41748 on8080; the existing frontend was not restarted and no parallel backend was launched. The original command envelope was preserved, but a retry encountered the expected stale-session/CSRF403 after restart. Chrome was refreshed to the login page; admin sign-in is pending before restoring/retrying that same command.

The running frontend still shows the older directly-assigned inbox wording after reload. This observation is separate from backend assignment support (both shared resolver IDs are advertised); further UI behavior remains under investigation. This follow-up remains IN PROGRESS and does not overwrite the earlier guarded/browser evidence.

### Chrome follow-up outcome

- PASS: after admin sign-in, restored the preserved start envelope and explicitly retried the same UUID/payload. The corrected host start route committed instance47/task41 for request43. No replacement command ID was generated for the failed start.
- FAIL (stale running frontend artifact): port4200 continued to render the pre-F016 task template (blank assignee and admin Approve enabled) after refresh. Installed package/source already contain the new fields; the running dev server is serving an older bundle. This was not treated as a current-source PASS.
- PASS (current production console on the same existing backend): copied already-built ignored frontend assets into target/classes/static/console and navigated the same Chrome tab to127.0.0.1:8080/console. No build/server process was started for this fallback. Current UI displayed GROUP: sandbox.finance and disabled completion for admin inspection.
- PASS: f006.reviewer and f006.reviewer2 independently discovered the same OPEN task41/instance47. Reviewer two submitted APPROVE with a test comment; completion command300188f1-4b82-4801-8f96-0cfedff745e7 returned NEW_COMMITTED_RESULT, COMPLETED/APPROVED, endedBy f006.reviewer2. Both refreshed inboxes then contained0 open tasks.
- PASS: the non-completing first member received404 for exact completed-task access. The actual completer retained exact task/submission access: assignment remained GROUP sandbox.finance, closedBy and submission actorRef remained f006.reviewer2, outcome APPROVE and history sequence9. Completion controls were disabled on the completed task.
- PASS: isolated viewer regression was red before the fix (405), then all10 WorkflowViewerUnitTest cases passed with the fix. Command: ./mvnw.cmd -B -ntp -pl workflow-web -am -Dtest=WorkflowViewerUnitTest -Dsurefire.failIfNoSpecifiedTests=false test, Corretto21.0.13/Java17, no listener/database. Other F016 evidence above is REUSED for unaffected membership/replay/ROLE/concurrency paths; those were not rerun during this focused Chrome check.

T06 is complete with the explicit environment limitation that port4200 still serves a stale dev bundle; this follow-up did not restart that frontend. The already-running backend was restarted only after explicit owner permission through its existing IntelliJ run tab, with no parallel backend. Replacement backend41748 and the owner frontend remain running. Chrome is left on the completing reviewer’s task41 page at port8080 for inspection. Definition29/version33, request43, instance47/task41 and their evidence are retained browser examples; unrelated records were untouched. No staging, commit or push.

### Restarted frontend cache follow-up — 2026-11-07

The owner reported a restart and requested continuation. Chrome port4200/task41 still showed a blank assignment and old completion description. Inspection found that exact pre-F016 template (including assignedUserRef) in frontend/.angular/cache/19.2.27/console/vite/deps/@workflow-engine_ui_runtime.js, while the installed package contains assignmentLabel/canComplete and the current template. The existing IntelliJ frontend run uses start:console.

An attempted stop of that selected frontend run was rejected by automatic approval review: prior explicit permission covered the backend, not frontend interruption, and the coordinate stop target was flagged. No frontend was stopped or cache removed. Explicit approval for stopping/restarting only the existing frontend and clearing only its generated .angular/cache is pending; the backend is untouched. The cache target was resolved and checked to be inside workflow-test-host/frontend before any deletion.

The owner explicitly approved frontend cache cleanup/restart. The selected IntelliJ frontend run was stopped (console reported process finished), the exact resolved frontend/.angular/cache directory was checked inside the frontend workspace and removed, then that same IntelliJ frontend configuration was rerun. No backend control was invoked. Port4200 verification is in progress; earlier denied actions remain historical.

#### T07 final result

PASS on Chrome port4200 after the approved cache cleanup and restart of the existing IntelliJ frontend run. The frontend console reported watch mode and the existing4200 URL. The existing backend remained usable and no backend control was invoked. On retained task41, the current template now displays GROUP: sandbox.finance, COMPLETED/APPROVE, closedBy and submission actorRef f006.reviewer2, the retained comment and disabled completion actions. My tasks now says “Your eligible open tasks” and returns0 open tasks. This resolves the prior4200 stale-bundle limitation. No new workflow was created or completed for this check.

Screenshot evidence: ignored .local/f016-chrome-4200.jpg. Earlier shared-member discovery/completion/access, unit and guarded integration evidence is REUSED unchanged; no unrelated matrix was repeated. Only generated frontend cache and feature handover records changed in this follow-up. Existing frontend/backend are left running for the owner, with the Chrome tab on4200/My tasks. No staging, commit or push.

## Commit authorization — 2026-11-07

The owner subsequently requested committing all working-tree changes, including the feature-lead skill and pre-existing decision/documentation changes preserved during implementation. Earlier no-commit statements describe their historical handover points. No push was requested.
