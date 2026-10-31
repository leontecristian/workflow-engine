# Implementation — F006 Test-host workflow console

## 1. Implementation Summary

Revision 3 source implementation completed, 2026-10-31; browser acceptance remains BLOCKED. Baseline `cb698f8` had a clean worktree and the required F005 APIs. No unrelated changes were discarded. Changes are uncommitted.

Source includes bounded family browsing, enabled-only two-account session/CSRF security, private exact-Long HTTP encoding, management routes and transactional audit, host request/outbox/inbox migrations and JDBC stores, real sandbox resolver/capabilities/recorder, runtime command and replay boundaries, scoped detached reads, local event delivery, and Angular management/execution/recovery pages. The manual API/database checks below are distinct from unavailable browser acceptance; T08/T09/T12/T13 remain open for those required observations.

## 2. Components and Files Changed

The only new core API is DefinitionQuery/DefinitionPage and the bounded family read; established JSON JDBC bindings were also corrected as documented below. Host changes stay under its configuration/controller/dto/service/repository/exception layers. Angular sources and locked dependencies live in `workflow-test-host/frontend`. The `console-ui` Maven profile installs its own toolchain, builds assets into target and packages them; ordinary builds remove stale console assets and require no Node.

Host master includes five new F006 changesets after the unchanged core master. No core schema changes, production adapters, seeds, background dispatch or database-backed tests were added.

## 3. Technical Decisions and Deviations

Selected Angular 21.2.25, CLI/build 21.2.29, TypeScript 5.9.3, RxJS 7.8.2, Node 22.22.0, npm 10.9.4, frontend-maven-plugin 1.15.1, JDK 21 with Java 17 release target. Sources: [Angular compatibility](https://angular.dev/reference/versions), [Node release checksums](https://nodejs.org/dist/v22.22.0/SHASUMS256.txt). The downloaded Node Windows x64 archive matched SHA256 `c97fa376d2becdc8863fcd3ca2dd9a83a9f3468ee7ccf7a6d076ec66a645c77a`.

The private HTTP/evidence mapper preserves Long strings and does not replace global/core configuration. Custom login preserves exact account references. JDBC joins the existing JPA transaction. A separate nontransactional coordinator invokes transactional command/replay beans, so receipt recovery happens after rollback and never retries a mutation. Local inbox duplicate comparison uses Java string equality.

No behavioral contract change is intended. Revision 3 supersedes historical revision 1 management-only/validation-fixture descriptions and older blanket testing restrictions. Isolated unit suites are selected explicitly; the existing database-backed application context test is preserved and excluded.

Manual import exposed an existing core mapping defect: `@JdbcTypeCode(LONG32NVARCHAR)` attempted SQL Server `setNull(...,4002)` for the nullable validation JSON. All five JSON string mappings now use standard JDBC NVARCHAR binding while retaining their explicit `nvarchar(max)` column definitions, Unicode data, budgets and migration identities. This is a provider compatibility correction, beyond the planned family-read addition but necessary to exercise F004/F005 through the console. It adds no schema or behavior contract. See [Hibernate type-code documentation](https://docs.hibernate.org/orm/6.5/javadocs/org/hibernate/type/SqlTypes.html) and [Microsoft JDBC basic types](https://learn.microsoft.com/en-us/sql/connect/jdbc/using-basic-data-types).

## 4. Verification

### Unit, build and static checks

- **PASS:** 15 isolated backend tests, four classes: `./mvnw.cmd '-Dtest=*UnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`. Covers wire precision/strict fields/bounds/Unicode, clock precision, deterministic fingerprints, scoped assignment access, recovery call order/no mutation retry, durable delivery decisions and repeated exact-username authentication. No database or Spring context was used. Surefire includes only `*UnitTest.java`; the original database-backed `WorkflowTestHostApplicationTests` remains unchanged and excluded.
- **PASS:** eight isolated frontend tests with pinned Node/npm: `npm run test:unit`. Covers frozen UUID/body/token, multiple retained commands, envelope recovery, actor clearing, large decimal strings, raw authoring text and retry versus replay-only request construction. No host/browser/database dependency.
- **PASS:** JDK 21.0.13 verified; Java release/bytecode remains 17 (major 61). Both `./mvnw.cmd '-Dmaven.test.skip=true' package` and `./mvnw.cmd '-Dmaven.test.skip=true' -Pconsole-ui package` passed. Successive profile/default builds showed index/JS/CSS present, then **zero** console entries in the ordinary JAR. The final delivery artifact was rebuilt with the profile. Core remains an ordinary library JAR with no BOOT-INF; two Maven modules remain.
- **PASS:** Angular production compilation; final bundle approximately 292 kB raw. No generated assets, node_modules, native DLLs, logs or credentials are tracked. Private codec/boundary scans found no host imports of core entities/repositories/helpers. Existing test source and core migration resources have no diff.
- **PASS:** production npm audit reported zero vulnerabilities. Full dependency audit reported 16 moderate development-tool findings; no automatic dependency upgrades applied. Node archive hash matched the official release list.
- **PASS:** 85 local Markdown file links resolved; tracked `git diff --check` and whitespace scans of all untracked source files passed. No staged files, core migration changes or accidental generated/dependency files were found.

Initial attempts are not hidden: a wrapper run under the restricted shell failed before Maven; the escalated wrapper succeeded. An initial frontend run used the older PATH Node 22.14.0/npm 10.9.2, then passed again on the pinned toolchain. Unit checks found nullable cursor unboxing; manual startup/login found a bean-name collision, login GET interception and shared credential erasure. These were corrected and rechecked. The first real import exposed the JDBC type-4002 defect described above; subsequent nullable and 10,000-character JSON roundtrips passed. The manual sample initially renamed its resolver accidentally and failed host-capability validation; correcting the sample produced a successful publication, without weakening validation.

### Post-review frontend corrections — 2026-10-31

Fixed the three P2 review findings in `frontend/src/main.ts`: catalog mutations now retain the returned snapshot and current lock version; “Use in start form” carries the request ID/version through query parameters to the destination component, preserving decimal strings; router subscriptions use Angular destruction-scoped cleanup so destroyed pages no longer load data on later navigation. Failed catalog saves retain their original token and unsaved input. These corrections do not change the HTTP or runtime contracts.

- **PASS:** all 12 isolated frontend tests on pinned Node 22.22.0, using `../target/frontend/node/node.exe --experimental-strip-types --test unit/*.test.mjs` from `workflow-test-host/frontend`. Four new regression cases execute the actual page methods with real Angular injection/destruction and RxJS, using HTTP/router I/O doubles: consecutive saves, rejected-save retention, request selection across component replacement with large decimal IDs/tokens, and navigation after repeated page destruction. No browser, host or database was used.
- **PASS:** production Angular build using `../target/frontend/node/node.exe node_modules/@angular/cli/bin/ng.js build`; total initial output 292.09 kB. The generated frontend bundle includes these fixes; the previously packaged host JAR predates this correction and requires the documented `console-ui` package build before running the fixed UI.
- **NOT RUN:** browser acceptance and database checks during this correction. The existing browser acceptance gaps remain open; the new unit checks do not close them. Changes remain uncommitted.

### Environment and migration evidence

Read-only prerequisites identified `apolo_workflow_dev` at localhost:1433, Windows authentication, server `LAPTOP-OR9OG58H\SQLEXPRESS`. Baseline catalog, versions, instances and receipts were empty. Disabled startup applied five host-only changesets and inserted no application rows. Subsequent restarts reported eight previously applied changesets and zero new executions.

Unchanged core ledger: F001 schema `9:a759c434c85142e959b6b927a4949d90`, F001 tables `9:3333ca75046b0857a7a23556e03e0d52`, F004 `9:71ec11105b310c821a80ce7977a96b89`.

F006 ledger, author `workflow-test-host`, stable logical paths from the plan: 001 schema `9:b9a9cd9b76cd100a76227d7630e5ddf6`; 002 audit `9:0d33296b06c38408b523529298fc09fc`; 003 request `9:0f3b5809862c69738cc26575b35d6a25`; 004 outbox `9:557308985eb0c630153c48161bc26a0e`; 005 inbox `9:eb7f1ff5e26792ab32b8034c7bd15c58`. Applied migration files were not subsequently edited.

Manual checks used separate authenticated HTTP sessions for `f006.admin` and `f006.reviewer`, plus a temporary restart using `f006.otherReviewer` to establish unrelated-assignment reads. Passwords are not recorded. Calls were explicit manual HTTP operations with independent read-only SQL observations, not database integration tests or an automated database scenario suite.

### AC01–AC22 observations

PASS below describes the **API/persisted-state portion**. Browser interaction is separately BLOCKED and is not implied by that label.

- **AC01 — PARTIAL:** disabled console returns 404; missing credentials fails enabled startup; API unauthenticated requests return 401; absent CSRF returns 403 without mutation. Separate roles authenticate repeatedly. Packaged authenticated index returns 200. Visual login/navigation in separate browser profiles NOT RUN.
- **AC02 — PASS API:** explicitly created SANDBOX_REVIEW, disabled/re-enabled it, reread current state, rejected stale update, and observed durable changed-result audit. No seed supplied it.
- **AC03 — PASS API:** imported `f006.manualReview` explicit versions, browsed persisted families/versions, exported, reused equivalent content unchanged, rejected different content at the same identity and read retired content. Final exact key/version lookup returned retired version ID 1; an unknown family's version page returned empty items/null continuation.
- **AC04 — PASS API:** invalid transition produced persisted FAILED graph diagnostics; correction advanced content revision and cleared old evidence. Fresh publication under real support succeeded. No runtime rows were created by validation/publication.
- **AC05 — PASS API:** omitted initialize and `{}` passed; `[]` failed structure; nonempty object included WF_DEF_UNSUPPORTED_FEATURE and did not publish.
- **AC06 — PASS API:** publication after catalog disable failed its fresh check; re-enable allowed publication. Restarting capabilities=none produced missing-support diagnostics; sample support was restored.
- **AC07 — PASS API / browser NOT RUN:** published replacement rejected; retirement preserved hash/export; repeated retirement unchanged and stale token conflicted. UI lifecycle interaction remains unobserved.
- **AC08 — PARTIAL:** sequential stale draft replacement returned 409 with unchanged stored content. Browser two-tab unsaved-text retention/reload confirmation BLOCKED. This is distinct from the real overlapping runtime commands below.
- **AC09 — PASS:** fail-audit edit of version ID 6 returned sanitized 500. Independent fresh HTTP/SQL reads retained revision 1/token 0/hash and audit count 28. Normal-mode retry committed revision 2/token 2 after the separately recorded none-mode validation.
- **AC10 — PASS API/unit:** exact designer integer `900719925474099312345` exported intact; duplicate keys rejected; 16 KiB envelope and 4 MiB fixed/chunked uploads rejected with 413. Compressed/wrong-media bodies returned 415; nonempty lifecycle-action body returned 400. A 10,000-character designer value persisted intact through NVARCHAR. Initial PowerShell chunked-client failure was superseded by an explicit HttpClient chunked request returning 413. Browser editor behavior is covered by source/unit inspection, not a visual walkthrough.
- **AC11 — PASS API:** family pages 4→5; version IDs 1,2/3,4/5,6/8; catalog 1→2; audit 1–31 traversed in bounded pages with terminal null cursors. Missing/unknown reads returned sanitized 404.
- **AC12 — PARTIAL:** both packages, index/asset delivery and repeated migrations/startup passed. Browser route reload/visual acceptance BLOCKED; cleanup/data state below.
- **AC13 — PASS:** request 2 linked to instance/activity/task 1 before the real resolver accepted it; ACTIVE state, one OPEN task for reviewer, history 1–4, one receipt, four pending outbox facts. Start UUID `ca8fdb7a-4851-4563-b45e-974d79f2eba5`.
- **AC14 — PASS:** reviewer completion UUID `389f6bf5-8059-4e27-aca0-975f4f74bf0f` completed task/activity/instance with result COMPLETED, typed submission/history sequence 5, history through 8, two total receipts/eight outbox facts. Returned final managed token **3** matched a fresh read. The host facade successfully read the authoritative submission inside the command transaction before commit.
- **AC15 — PASS API:** administrator completion returned NOT_ASSIGNED; forged actor rejected; reviewer management denied. Alternate reviewer could not read original task/instance (404), completed its own assignment, and the restored normal reviewer could not read that alternate task. Completed original assignments remain readable. Client assignee query rejected.
- **AC16 — PASS API:** start replay returned original ACTIVE/token 1 after completion/retirement; completion replay returned original token 3. No additional effects. Deliberately discarded successful response bodies were recovered using the same UUID/payload/token; actual network-fault injection was NOT RUN. Changed input/host intent or another actor produced COMMAND_ID_CONFLICT.
- **AC17 — PASS:** two instance-2 completion requests began at 18:40:22.682 UTC on separate server threads before either ended: one 200, one 409 STALE_VERSION, one committed completion. UUIDs `b3fbc7ab-75ec-49c0-b3bc-b1c65f3bdf2f` / `3ac87a4f-b2bd-46f2-bfd7-d42f48a69e57`. Identical concurrent starts and completions returned one new and one replayed result; instance 3 retained eight history/outbox facts and two receipts.
- **AC18 — PASS:** instance 2 started while catalog was nonselectable, then completed after its pinned version 2 retired. Retired version rejected a new start without linking request 1. None mode failed new runtime support with no partial effects, while successful receipt replay still worked.
- **AC19 — PASS for starts:** each startup-only failure (after link, after real outbox batch insertion, after new core result) returned 500. Independent SQL reads retained 4 instances/28 history/7 receipts/28 outbox; requests 6–8 stayed unlinked/token 0. Restoring normal mode and retrying original UUIDs linked them to instances 10–12. Replay bypassed the post-result failure. Completion-specific injected failures were not separately exercised.
- **AC20 — PASS:** pending events survived multiple restarts; first explicit dispatch inserted ten inbox effects. Identical redelivery retained one effect and incremented delivery count. After final restart, dispatch inserted the remaining 50 effects; event `1:1` has delivery_count 3 and unchanged first delivered/received timestamp `2026-10-31T18:47:39.2484448Z`. No history/receipt changes came from delivery.
- **AC21 — PASS API:** request IDs 1–10, assigned OPEN tasks 4/7/8/9, history 1–8 and exact SINGLE submission traversed with small limits. All 60 outbox rows traversed across identity gaps (1–28,37–68); inbox IDs 1–60 traversed, terminal cursors null. SINGLE provides only one submission per activity, so a multi-submission activity page is outside this slice.
- **AC22 — PASS:** overlapping distinct starts for request 5 returned 200/409 SANDBOX_REQUEST_CONFLICT; one link/instance persisted (instance 5). Identical start UUID recovered the original link/result; changed expected request token conflicted. Persisted INSTANCE_STARTED evidence includes `wf-test-start-v1` SHA256 `610e45dbef77ad8cea723cfde1412d42d46deac09994ce5235a18714a48447b6` for request 5/token 0, independently checked against canonical UTF-8. Receipt normalization version is `wf-runtime-request-v1`.

These observations add evidence for matching F005 start/completion/token, receipt-first replay, same-transaction link/submission visibility, losing-command rollback and durable-recorder cases. They do **not** automatically close F005 T11 or certify other hosts/providers. F004's null/unknown-constraint-name provider branch remains **NOT RUN**; no schema corruption or public fault endpoint was introduced.

## 5. Known Limitations

Browser access host and native computer-use pipe were both unavailable. Therefore visual/layout/accessibility inspection, real browser route reload, separate browser profiles, two-tab editing and browser recovery-envelope interaction remain **BLOCKED/NOT RUN**. API sessions and unit tests do not replace those observations; T08/T09/T12/T13 stay open. No functional API failure remains from the checks executed.

Malformed third-party providers, outer database commit faults, actual network disconnection, completion-specific failure-switch injections, broader resource limits and production APOLO/broker delivery remain unverified. The durable local inbox does not establish distributed exactly-once delivery. Development-tool npm audit findings remain noted above. No behavioral contract changed; the JDBC provider correction and small management/DTO organization choices are documented deviations.

## 6. Handover, data and process state

Launch/configuration/credentials and manual use: [README](../../../README.md#local-workflow-console-f006). Generated source/assets and credentials remain external/ignored. The final UI-enabled JAR is under the host target directory; ordinary packaging deliberately removes the UI.

Retained database records: two families (`f006.manualReview`, ID 4, versions 1–7; `f006.pageFamily`, ID 5, version 1), eight definition-version rows (IDs 1–8), catalog IDs 1/2 (`SANDBOX_REVIEW`, selectable again; `F006_EXTRA`, nonselectable), audit IDs 1–31, requests 1–10. Request 1 is intentionally retained unlinked; its misleading probe name reflects an early PowerShell client reusing a CSRF header, not a server CSRF bypass. A clean-header retry was denied with no write.

Requests 2–10 link respectively to instances 1,2,3,5,10,11,12,13,14. Nine activities/tasks, 60 history rows, 15 receipts, 60 delivered outbox rows and 60 immutable inbox rows remain. Instances 10–12 remain ACTIVE/token 1 with OPEN reviewer tasks; the other six are COMPLETED/token 3/history 8. Instance 13 belongs to the temporary `f006.otherReviewer` actor and is completed. Rolled-back identity gaps remain; nothing was reset/deleted. Final pending outbox count is zero.

All failure flags restored false, capabilities=sample and normal reviewer restored. Temporary Java processes were recorded by executable/command/creation identity and stopped. After runtime smoke PID 6492 was stopped, a final disabled restart (PID 42024) confirmed API/index/assets all returned 404; it was stopped. Final formatted-asset/read-only smoke PID 40180 served index plus `main-THBJIRNZ.js`, then was stopped and a fresh process query returned zero matches. Ignored local evidence is under root `target/f006-*` (build/unit/SQL/HTTP/overlap logs and saved original command envelopes); DPAPI-protected local credentials are not source or committed configuration. No temporary process is left running. No staging, commit or push occurred.

Remaining work is the blocked browser acceptance plus the explicitly unrun integration/production cases above. Historical revision-1/2/3 documentation preparation did not implement or verify runtime; the actual source baseline for these results is `cb698f8` plus this uncommitted F006 worktree.

### Subsequent owner-requested IntelliJ launch

The owner requested launching through IntelliJ and creating a run configuration. Added shared `Workflow console (local)` with the host main class, JDK `corretto-21`, native DLL path and ignored `.local/workflow-console.properties`. Existing development account references were retained so assignments remain usable; passwords stay outside Git. IntelliJ launched PID 28588 on port 8080 using Java 21.0.13; startup reported eight existing migrations and zero new changesets. The login page returned 200. This owner-requested application is left running under IntelliJ, superseding the earlier temporary-process cleanup state. No application-data mutations or tests were needed for this launch.
