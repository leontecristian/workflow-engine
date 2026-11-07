# Implementation — F013 shared instance viewer

## 1. Preparation summary

2026-11-07, initial preparation: specification revision 1 prepared as DRAFT; T01 only. No feature implementation exists. The user requested staging in `.local/F013-shared-instance-viewer/` to avoid interference with another Codex session implementing F012.

Inspected starting HEAD was `8a0ed2d` (F012 specification commit). The worktree was already dirty with active F012 records, dependencies, bootstrap and frontend component changes. Those are another task's work and were neither edited nor staged here. This revision is preparation context, not F013's future implementation baseline.

2026-11-07, review update: the owner moved the four documents to `docs/features/F013-shared-instance-viewer/` and authorized documentation-only fixes. Current HEAD is `8c8dfdc` (F012 implementation); F012 T06 browser acceptance remains open. Initial status for this update showed only the untracked F013 folder. Revision 2 remains DRAFT and changes only these four documents; it does not mark F012 accepted or begin extraction.

2026-11-07, final consistency review: revision 3 remains DRAFT at the same inspected HEAD, with F012 T06 still open. Only these four untracked documents were edited. The review resolves ambiguities within the accepted two-check policy; no new owner decision or permission is introduced.

2026-11-07, verification-scope update: revision 4 at inspected HEAD `943a51f`. F012's current final acceptance records AC01–AC10 PASS and T01–T07 complete, superseding the earlier blocker descriptions above. The owner requested focused extraction checks, reuse of relevant F012 evidence and no repeated idempotency tests. Only the four F013 documents were changed; this is not implementation or fresh runtime evidence.

2026-11-07, T02 readiness reconciliation: revision 5 is READY against HEAD `943a51f`. Starting worktree contained the owner's uncommitted focused-delivery updates to AGENTS.md, docs/specification-driven-development.md and docs/roadmap.md, plus this untracked feature folder. Those three files were read and preserved without editing/staging. No application/POM/manifest changes were present. Readiness has no blocking scope or policy decisions; T03–T07 remain unimplemented and need a subsequent request.

### Reconciled dependency and source baseline

Read from the current manifest/POMs and matched to F012 acceptance; these are configured/previously verified versions, not fresh installed-tool or build checks.

| Component | Baseline |
| --- | --- |
| Angular framework/compiler/compiler-cli | 19.2.25 |
| Angular CLI/build | 19.2.27 |
| Material/CDK | 19.2.19 |
| TypeScript | 5.8.3 |
| Node / npm | 22.22.0 / 10.9.4; manifest and Maven profile agree |
| RxJS / Zone.js | 7.8.2 / 0.15.1 |
| jsonc-parser / tslib | 3.3.1 / 2.8.1 |
| Maven baseline | Spring Boot parent 3.4.5; Java 17 compilation, JDK 21 build/runtime requirement; existing core/test-host modules |
| Console packaging | dist/console/browser copied to static/console by the opt-in profile |

Scoped source review confirms the four existing GET routes, core read-service boundary, graph pin/watermark/bounds logic, and host-owned variable/effect panels. The `943a51f` production changes are the definition editor's Material reload dialog, not runtime viewer/backend behavior. Thus the final F012 application evidence remains the appropriate pre-extraction baseline. The source currently has host-coupled navigation/transport and graph access policy; F013 specifies their extraction seams and focused fresh verification rather than assuming old evidence proves the new adapters.

F012 temporary fixtures were cleaned up; reused evidence does not imply its recorded instance IDs still exist. Implementation may prepare minimal test-owned state in the guarded harness as P04 allows, without repeating command/replay acceptance. Additional library build-tool pins and the local package consumption procedure are routine T04 choices; they do not require a new product decision or dependency installation during readiness review.

## 2. Observed source and boundaries

- Current backend combines selected reads with commands/tasks/variables in `ConsoleRuntimeController`; graph has its own controller and `ConsoleInstanceGraph` service. Extract only the four selected GET operations.
- `ConsoleRuntimeAccess` supplies sandbox admin/assignment policy, including completed assignments. Instance history is currently authorized at instance scope; generic row-level redaction is not implemented. Draft R03 specifies complete-history grants or denial, with separately authorized graph links.
- Graph reconstruction already checks pinned definition identity/hash, uses an initial history watermark, has bounded traversal/response size and projects only display topology. Its access filtering and failure behavior must survive extraction.
- The current instance page also contains variables and sandbox service effects. These remain in a host wrapper. History/event pages include exact evidence download and routing-evidence presentation; graph navigation currently hardcodes sandbox routes and requires a host seam.
- The current codec encodes long values as strings and byte-bounds pages. Stored event `detailsJson` remains exact text. A new library must not apply a smaller ordinary-page cap to exact event payloads.
- The original manifest inspection during active F012 work was provisional. T02 has now confirmed the final manifest/POM baseline and recorded acceptance above; implementation checks only subsequent relevant drift.

## 3. Decisions and limitations

Revision 2 retains root web/UI source locations, explicit read-only activation, one local UI package entry, sandbox composition and generic NgModule consumption. No real APOLO source change or production package distribution is included. Detailed member signatures/build tooling must be finalized against completed F012 before dependent implementation, within the selected behavior; material changes require reconciliation.

Owner decisions accepted on review: graph must require instance access; history is one full-evidence grant; all required F012 browser acceptance must finish before implementation. To keep access simple, INSTANCE covers metadata and graph and is required by all endpoints; HISTORY is the only additional check and covers both history endpoints. Links reuse existing host destination checks. No graph-specific permission, role catalog or permission-management UI is introduced.

Engineering review fixes: normalize read errors and silent cancellation without command-recovery wording; preserve F009's dual 4 MiB stored details and 8 MiB UTF-8 exact-event response limits; reuse existing detached core DTOs rather than mandatory copies; use reactive English-default message inputs rather than a translation service; keep optional correlation metadata outside access policy; select one reproducible built-package consumption procedure during implementation. The small NgModule fixture remains required.

Host-policy clarification: the real sandbox adapter preserves existing admin/assigned-reviewer rules for both read checks. Isolated adapter doubles test combinations such as allowed instance/denied history and hidden destination links. This tests delegation without simulating APOLO roles or adding runtime permission switches. Actual APOLO policy and business-link correctness remain separate integration evidence.

Revision 3 clarifications: graph construction can use internal history summaries under INSTANCE without exposing HISTORY-protected evidence; graph access denial clears the composite viewer, unlike ordinary graph construction/transport failure. The viewer loads only metadata/graph, and standalone history components load when mounted. Same-user session changes and overlapping reads invalidate late results; labels/evidence render as text. The no-activation test includes package scanning. T02 remains a documentation readiness review, with fresh runtime capture and routine signature/tooling choices assigned to later authorized work; T06 packaging and T07 browser evidence are separated.

Revision 4 supersedes the broad P04 regression instructions: no new baseline walkthrough, command recovery/idempotency/replay tests or engine rollback/concurrency campaign is required for this read-only extraction. Keep existing tests and guarantees; select focused viewer suites. Graph consistency uses controlled read tests rather than repeated command execution. The new package, adapters, viewer rendering and host composition still require fresh scoped evidence. Follow-up fixes repeat only the affected checks, not full acceptance. Permissions and feature behavior remain unchanged.

### F012 evidence available for reuse

Source: F012 implementation.md, final acceptance and acceptance-mapping sections, committed in `943a51f`. T02 confirmed baseline applicability by scoped source review and the absence of application changes since that revision. This is REUSED baseline evidence, not F013 runtime acceptance. During implementation, invalidate only entries affected by the actual extraction diff and perform the specified new-boundary checks.

| Existing evidence | F013 treatment |
| --- | --- |
| AC02/03/07 async read/graph failures; AC02/05 logout | Reuse unchanged transport/session behavior. New component cancellation, clearing and host adapter behavior receive targeted tests and one representative consumer observation. |
| AC04 scalar/JSON/null matrix and downloaded artifacts | Reuse request/start/variable input behavior. Fresh checks cover only extracted history serialization/display and one exact history download. |
| AC05 uncertain response/export/restore/retry and post-commit refresh | Reuse for unchanged excluded command behavior; no replay/idempotency suite or browser command walkthrough in F013. |
| AC06 two-stage/optional-stage execution | Reuse execution evidence and fixture definitions. Inspect representative stored projections after extraction instead of replaying stage completion through the UI. |
| Broad navigation/editor/dialog, logout and packaged serving | Reuse unchanged application behavior. New package serving/CSP and extracted viewer navigation/styles get a small fresh check; no repeat editor/dialog or full-console tour. |

No roadmap, feature index, architecture, source, POM, lockfile, F012 record or F011 evidence was changed by either preparation or this review update. Links now resolve from the actual feature folder.

## 4. Verification

Historical revision 1 preparation: source/governance inspection and requirement-to-plan/task review. Static verification PASS: exactly four Markdown documents; all 10 destination-relative local links resolved (sibling links checked in staging); no trailing whitespace or unmatched code fences. `git check-ignore` confirmed the original staging directory was covered by the existing `.local/` rule. `git diff --check` passed on the concurrent tracked worktree, with only Git LF/CRLF conversion warnings. The ignored draft files were checked independently; the same set of concurrent F012 modified paths remained. This describes the original local preparation, not the current folder's Git status.

| Check | Actual result |
| --- | --- |
| Feature implementation / AC01–12 runtime outcomes | NOT RUN — specification preparation only. |
| Java/frontend isolated tests | NOT RUN — no implementation changes. |
| Library install/build/package consumption and Maven packaging | NOT RUN — no implementation or dependency installation. |
| Database integration and sandbox/NgModule browser checks | NOT RUN — no server or database access. |
| APOLO integration | NOT RUN — outside F013 scope. |

Revision 2 static verification — PASS: exactly four process documents, all 11 local Markdown links resolve from the actual feature folder, no trailing whitespace or unmatched fences, and review of requirement/plan/task consistency. `git diff --check` passed; because the F013 directory is still untracked, its files were also checked directly. Final status contains only that untracked folder. No build, test, database or browser check was run for this documentation update.

Revision 3 final consistency verification — PASS: four documents, eight requirement groups, twelve acceptance scenarios and all 11 local links checked. Reviewed access/error/loading behavior across spec and plan, task dependencies and acceptance coverage, current F012 T06 status and historical versus current evidence. Direct file checks found no trailing whitespace or unmatched fences; `git diff --check` passed. Status still contains only the untracked F013 directory. No runtime verification was performed or claimed.

Revision 4 documentation verification — PASS: four documents and all 12 local links checked; no trailing whitespace or unmatched fences. Reviewed consistency of R08, AC07/11, P01/P04 and T02/T06/T07 with the owner's evidence-reuse/no-idempotency-retesting direction. Confirmed F012 final acceptance and current HEAD `943a51f`; preserved historical blocker entries as dated history. `git diff --check` passed, with direct checks of the untracked F013 files as well. Only that folder appears in status. No tests, builds, browser or database operations were performed.

Revision 5 / T02 verification — PASS (documentation readiness only): reconciled F012 final acceptance, current scoped source/manifest/POMs and the updated agent/workflow/roadmap rules. Four-document inventory and all 12 local links pass; direct whitespace/fence checks and `git diff --check` pass (Git reports an LF/CRLF warning for the pre-existing roadmap edit). Status retains the same three pre-existing guidance edits and untracked F013 directory. No source/tool installation, build, test, database or browser action was required. F013 runtime acceptance remains NOT RUN as recorded above.

No process was started or stopped. No database was accessed. No commit or push was made. F012 and earlier feature acceptance gaps remain owned by their records; this preparation closes none of them.

## 5. Handover

Revision 5 is READY; T01/T02 are complete and no blocking scope/permission decision remains. After a separate implementation request, start at T03–T07, first inspecting HEAD/status for relevant drift from `943a51f` and preserving unrelated work. Do not reopen F012 acceptance or repeat unrelated idempotency tests. Follow focused P04 checks and stop when they pass. Navigation/index changes were excluded from the readiness-only update; planned implementation handover updates remain in P04.

Subsequent owner request — 2026-11-07: commit the three updated guidance documents and these four READY F013 records only. This authorizes a documentation commit, not implementation or push. Earlier no-commit statements record their original preparation scope. No build, test, server startup or database operation is part of this handoff.

## 6. Authorized implementation (T03�T07)

Implementation request received 2026-11-07. Starting HEAD `2944287f20b5e6c230518cfda41e1341a15b8f8d`, clean worktree: the previously uncommitted guidance and READY documents are now committed. `git diff 943a51f HEAD --stat` contains only those seven documentation paths; no relevant application baseline drift. F012 evidence above remains REUSED for unchanged excluded behavior.

Toolchain freshly inspected: Corretto 21.0.13, Node 22.22.0, npm 10.9.4. Published npm metadata (`npm view ng-packagr@19.2.2 version peerDependencies --json`) confirms ng-packagr **19.2.2** supports compiler-cli ^19 and TypeScript >=5.5 <5.9, matching 19.2.25/5.8.3. This additional pin is recorded before UI dependency edits. No other tooling upgrade is selected.

Local consumption procedure: build partial-compiled `workflow-ui/dist/workflow-ui`, then install that directory as a copied npm file dependency using committed `.npmrc` `install-links=true` in both consumers. No TypeScript source aliases, symlinks or registry publication. Maven builds the library before consumer npm ci/build. Public inputs are instanceId, sequence (event only), sessionScope (opaque nonnull generation) and messages; reads accept AbortSignal; navigation returns UrlTree or null. Only the four read operations enter the library.

Activation uses a plain explicitly imported bean configuration and a viewer-only RequestMappingHandlerMapping. Neither the configuration nor controller has a component stereotype; package scanning does not activate them. Local controller exception handlers scope errors to the viewer and take precedence over host advice. This is a registration choice within P02, not a contract change.

### Implementation progress and focused checks

Web: the four GET mappings, graph projection, scoped long/ISO codec and local controller errors are extracted. Host activation remains gated by console.enabled. Core and changelogs are unchanged. Independent activity/task grants replace the old activity-implies-task link behavior as specified in R03. No command or recovery implementation was extracted or modified.

UI: one public package exports the four components and typed reads/navigation/messages/errors. Shared lifecycle guards cancellation, overlapping reads, input/session replacement and destroy. Graph metadata is authoritative on success; access denial clears the composite. History/event requests are mounted independently. Raw details downloads preserve original text. The sandbox retains variable/effect panels and its own transport/session/navigation. Reusable graph layout, routing helper and their assertions moved to the library.

Fresh isolated results so far: 6 web tests PASS; 7 general graph tests PASS; 11 host graph/access tests PASS (the original 10 plus independent exact-task grant coverage). UI 21 focused tests PASS; affected viewer/lifecycle subset 15 PASS again after the reactive error-message/metadata presentation adjustment. Changed-wrapper variable inspection 2 PASS. The NgModule production fixture build initially failed on a TypeScript union narrowing expression, then passed after its fixture-only correction (422.64 kB initial bundle). Integration fixture compile errors (compound var declaration and start-overload call) were corrected; test compilation now passes. No idempotency/replay or broad command suite selected.

The original-worktree Maven package attempt reached library build successfully, then failed during sandbox npm ci because unrelated ng serve PID 26176 holds esbuild PID 13852. These processes predate this task and were not stopped. The package check moved to an isolated source/build snapshot in .local/f013-package; this concrete environment failure justifies that packaging retry. The initial source tree's dependencies are not used as clean-install evidence. Library source changes before the final package command required its fresh compilation; earlier successful identical checks were otherwise reused.

Packaging discovery: the host-owned definition preview also imports the existing graph layout and stylesheet. Removing the old runtime files exposed those references in the clean sandbox build. Preserve one authoritative implementation by exporting the existing pure layout function/types and shipping its existing scoped stylesheet as a package Sass asset. Only the host preview imports/style reference change; authoring behavior stays host-owned. This concrete consumer need is the P03 exception to keeping layout private, not designer extraction. Verify its existing topology assertions and package resolution; no editor walkthrough or command tests are added. The failed sandbox build justifies repeating its affected packaging step after this fix.

### Final focused verification and handover � 2026-11-07

T03�T06 are complete. T07 is PARTIAL/BLOCKED, not accepted. Feature code is implemented; guarded runtime acceptance remains open.

Fresh PASS evidence:

- Java: 26 distinct focused cases: WorkflowViewerUnitTest (6 initial plus 2 scoped-host/Unicode checks), ConsoleGeneralGraphUnitTest (7), ConsoleInstanceGraphUnitTest (11). Commands selected these names explicitly with -Dsurefire.failIfNoSpecifiedTests=false; no core/command/idempotency suite ran. The host graph suite was repeated only after the independent-task access addition. The final import-only cleanup receives compile-only validation.
- Frontend: 21 library graph/layout/routing/lifecycle cases, 2 host variable-panel cases, 3 host topology cases 4 transport/navigation adapter cases and 2 exact-route/session wrapper cases (32 distinct focused cases). Existing graph/routing assertions were moved/adapted to their new owners; the host inspection file retains submission assertions while history/event assertions now live with the shared components. The affected 15-case viewer/lifecycle subset passed after reactive error-message presentation changed. The topology/panel and adapter suites used the isolated snapshot's clean dependencies after the original node_modules install encountered the locked executable. No broad frontend script was selected.
- Packaging: ./mvnw.cmd -Pconsole-ui '-Dmaven.test.skip=true' package -B -ntp passed in .local/f013-package. Both npm ci installs, partial library compilation, copied-package consumption, sandbox production budget and scripts/check-build.mjs CSP check passed. The first isolated attempt exposed the definition preview's shared helper dependency; the final retry includes only its import/style-resolution fix. Existing Sass deprecation warnings remain. No successful identical standalone sandbox build was repeated.
- Artifact/dependency inspection: ordinary workflow-web JAR has no BOOT-INF/executable bootstrap, application properties, migrations or host code. The executable host JAR contains workflow-web and five static/console assets. Core source/POM/changelogs are unchanged; Java compiler used release 17 on verified JDK 21. npm ls @angular/core --all in the clean sandbox shows one 19.2.25 runtime, with library/Material/CDK peers deduped. UI consumers install built package directories as copies with install-links=true and no TypeScript aliases into sources.
- NgModule fixture: production build passed against final component presentation (423.45 kB initial bundle). The later helper/Sass exports do not affect its component imports and invalidate no runtime observation. A focused native Chrome pass at http://127.0.0.1:4300 demonstrated AOT NgModule boot, host interceptor count 6, alternate violet theme, two same-instance viewers, text-only script-like labels, exact 9007199254740993/12500.00/null evidence, reactive French message replacement, independent 100%/50% graph zoom (including keyboard activation), alternate task navigation and its destination. Denied and failed fixtures cleared data and showed viewer errors; delayed reads invalidated before completion did not restore data. Same-user session replacement advanced intercepted reads from 24 to 30 and restored both views. A 175% browser zoom check produced a roughly 724-CSS-pixel single-column view with wrapping/scroll controls; browser zoom was restored. The owned fixture tab was closed, leaving the original two tabs. Detailed race/error cases remain isolated-test evidence rather than a second browser matrix.

Guarded runtime attempt � BLOCKED:

- Direct Failsafe goals first failed reactor artifact resolution before database access. The documented lifecycle was then used: ./mvnw.cmd -pl workflow-test-host -am -Pintegration-tests '-Dtest=NoSelectedUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=SharedInstanceViewerIT' '-Df013.browserHold=true' verify -B -ntp. This selected only SharedInstanceViewerIT; zero test cases completed.
- The existing IntegrationDatabaseInitializer accepted the dedicated integration-test configuration. Owned JVM 32428 reached Liquibase's SQL Server metadata read but received no response for several minutes. jcmd showed the main thread blocked in SQLServerPreparedStatement / Liquibase column metadata extraction. No F013 test method or fixture setup was reached; no completed migration or GET behavior is claimed.
- A temporary diagnostic reused the actual IntegrationDatabaseInitializer and its catalog-checked datasource, with the exact approved apolo_workflow_test URL and a five-second read query timeout. Its JVM 30500 stalled in SQL Server login before issuing the diagnostic query. This establishes the observed environment wait, not its server-side cause. No development database, alternative datasource, unrelated session termination or SQL Server restart was used.
- Both owned JVMs were stopped after PID/command-line identity checks. Failsafe consequently reports terminated fork / 0 completed cases, not a PASS. The owned fixture HTTP server 30260 was also stopped (PowerShell Stop-Process failed for that PID; verified .NET Process.Kill succeeded). A final process query found none of these owned PIDs. Unrelated ng serve 26176 and esbuild 13852 remain running. No test-owned fixture rows were created, so no fixture cleanup SQL was needed; no claim is made about unrelated database state.
- The guarded four-route/real-policy/unchanged-state observations and packaged sandbox viewer/browser/CSP-serving/history-download observations remain NOT RUN/BLOCKED. The isolated package's assets were copied into the owned test output for the intended browser pass, but startup never reached a usable host. These new-viewer observations cannot be replaced with F012 evidence. To resume, restore responsiveness of the existing test SQL Server, run only SharedInstanceViewerIT through the command above (omit browserHold for automated reads alone), then perform the one focused sandbox pass in P04. The test prepares sequential/optional retained state and cleans only its recorded IDs after the method finishes; browserHold is opt-in and bounded to 12 minutes.

Evidence reuse and acceptance scope:

- REUSED: F012 at 943a51f, final acceptance AC02/03/05/07 for unchanged application/session mechanisms; the new viewer lifecycle and transport receive fresh checks above.
- REUSED: F012 AC04 input/export matrices, AC05 command recovery and AC06 sequential/skipped execution for unchanged excluded behavior. No uncertain-response recovery, idempotency/replay test or workflow completion browser tour was selected.
- REUSED: F012 broad navigation/editor/dialog and original CSP/security contracts. Only the definition preview's helper imports changed, covered by its topology tests and production package build. Fresh packaged sandbox serving remains open.
- AC01/02/04/05/08/09 are supported by isolated/package/NgModule evidence for their demonstrated boundaries. AC03/06/07/10/11 retain the explicitly listed live sandbox/GET gaps. AC12 requires final diff/links/whitespace and cleanup review; cleanup is complete. APOLO ASM-01/02/03/09 production integration remains outside this feature.

No commit or push was made. The unrelated untracked F014 documentation folder was preserved. Scope review confirms unchanged core, migration, command-buffer/recovery/coordinator/transaction implementations and F011/F012 records. No new engine behavior, task/variable API, role system, persistence mapping or business policy was added.

Final compile-only check after explicit-import cleanup: PASS, ./mvnw.cmd -pl workflow-test-host -am test-compile -B -ntp (no tests, frontend rebuild or database startup). The build initialization removes original target console assets as documented; the passing isolated packaged JAR remains the asset artifact. Original frontend npm ci stopped partway through installation because the existing server held esbuild; reinstall those original dependencies after that unrelated server is stopped before using them as a clean local installation. Two focused wrapper tests restore exact route/session binding assertions at the new host/component boundary; both PASS.

Final review: PASS � 87 existing/new task files inspected (54 new, excluding the unrelated F014 folder); all scoped Markdown links, JSON syntax, direct trailing-whitespace and fence checks pass. git diff --check passes. Core/changelogs, command/recovery implementations and F011/F012 records have no diff. Only intended extraction/composition/tooling/verification and current-build documentation changed. T07 stays open for the two listed runtime gates; no broader acceptance work is required. All owned processes are stopped; no commit or push.

### Owner manual development-console check — 2026-11-07

**PASS, limited manual observation (owner-executed, screenshot/log reviewed by the agent).** This supplements the earlier blocked runtime attempt; it is fresh development-console evidence, not a rerun of SharedInstanceViewerIT or packaged-JAR acceptance. Source: the owner's startup output and successful browser screenshot supplied in this chat (`codex-clipboard-c551dc66-078c-4675-a5f1-ed346f982bed.png`). The screenshot is not stored in the repository. The owner ran the current uncommitted F013 worktree and IntelliJ/frontend-startup follow-ups; no committed-only revision or database catalog was independently established for this observation.

- The supplied `npm run start:console` output shows ng-packagr successfully building `@workflow-engine/ui` in Ivy partial-compilation mode (2,512 ms), refreshing the console's installed package copy, then invoking `ng serve`. This establishes the observed development startup sequence, not a clean dependency installation or a production build.
- In the authenticated administrator session, the owner opened `http://127.0.0.1:4200/console/#/instance/43`. The shared instance viewer displayed instance 43, definition `sandbox.requestDataReview`, version 1, status ACTIVE, current position REVIEW, instance token 1 and last history sequence 8.
- Browser Network shows HTTP 200 for `/console/api/instances/43` and `/console/api/instances/43/graph`. Together with the reviewed host composition, this supports workflow-web metadata/graph route wiring and workflow-ui metadata rendering through the development console. The graph section heading is visible below the metadata; the screenshot does not establish graph topology rendering, zoom/reset or graph-link navigation.
- This is partial fresh support for AC07's sandbox composition and the successful metadata/graph read path. It does not close AC03's authorization cases, AC04/05's history/event/download checks, AC06's representative graph/state cases, AC10's packaged serving/CSP gate or AC11's unchanged-state verification. No additional test, database query, fixture creation or command execution was performed by the agent for this record update.
- The preceding owner screenshot showed instance 11 failing with HTTP 500 `HOST_CONFIGURATION`. The owner identified it as an older instance and reported instance 43 working. No exception cause was captured; the older instance's failure remains unexplained and is not classified as an age-related compatibility failure or resolved defect.

T07 remains PARTIAL, with the guarded integration test and packaged sandbox browser gate still open. The earlier startup blockage remains the historical result of that guarded attempt; this successful owner session does not prove that harness now passes. History/event/download, remaining graph/navigation and policy checks remain unobserved by this manual check. Earlier unit/package/NgModule outcomes and REUSED F012 evidence retain their recorded scope.

Documentation-only reconciliation: updated this implementation record and the T07 handover in tasks.md without changing required behavior or planned verification. Reviewed the evidence against the supplied screenshot/log and AC03–11; Markdown links and whitespace were checked. No builds/tests or server/database operations were run for this update. The owner's running processes were left untouched. No commit or push was made.

### Agent runtime verification — 2026-11-07

Owner requested F013 testing against the backend/frontend already running from IntelliJ and the logged-in Chrome session. HEAD remains `2944287f20b5e6c230518cfda41e1341a15b8f8d` with the existing uncommitted extraction/startup changes and unrelated F014 records preserved. No production code changed during this verification.

**PASS — focused development-console browser checks (AC04–08, demonstrated portions).** Used the existing administrator session at `http://127.0.0.1:4200/console/` and retained records; no workflow commands or development-database fixture writes were performed.

- Instance 43: metadata and pinned graph render ACTIVE/REVIEW, token 1 and watermark 8. Completed start/service nodes and traversed edges, current human node and unreached branches agree. Keyboard Enter changes zoom from 50% to 75%; reset returns to 50%. Task navigation opens task 36, assigned to `f006.reviewer`; the host-owned variable/effect panels remain composed.
- Instance 44: completed sequential projection highlights manager APPROVE followed by partner REJECT, final REJECTED, token 5 and watermark 13. Historical task IDs 37/40 and activity IDs 37/40 remain linked. Activity 40 opens the completed PARTNER/REJECT record for instance 44.
- Instance 45: completed optional-stage graph highlights `skipPartner`, leaves PARTNER unreached and marks APPROVED as the final position, token 3 and watermark 15. With page size 10, history returns sequences 1–10, then 11–15 without overlap and with no further-page control. Exact event 13 shows the false `needsPartner` evaluation, default `skipPartner` selection and original routing JSON.
- Instance 46, event 2: displayed raw start-input evidence and the downloaded 121-byte UTF-8 JSON preserve `9007199254740993`, `12500.00`, their nested array occurrences and explicit null. The downloaded text was read directly from disk without a JSON parse/stringify round trip. Instance 43 event 2 also downloads its exact 29-byte evidence. Browser download-event detection timed out despite Chrome saving the files; direct file inspection established the download results.
- At a temporary 760 × 900 viewport, exact evidence wraps and graph controls remain usable; graph overflow stays inside its scroll area (document width 745, viewport width 760). Keyboard zoom works there. Viewport override was reset. The original tab was restored to working instance 43 at 50% zoom; the temporary API tab was closed.

**OPEN finding — older instance 11.** Reproduced the prior read failure. A direct authenticated browser read of `/console/api/instances/11` returns `{code: HOST_CONFIGURATION, message: HOST_CONFIGURATION, diagnostics: [], correlationId: cf8ba6f7-8efc-4559-8bde-bd8c0b604006}`. The instance viewer shows “Read failed. Refresh to try again.” without protected metadata. This establishes that the metadata read itself fails, rather than only graph construction. No root cause, age-related compatibility conclusion or resolution is claimed; no data was repaired or removed.

**PASS — guarded real-policy integration (AC03/04/05/06/11, scoped assertions).** Used a separate source/build snapshot at `.local/f013-verification` to avoid touching the owner's live output/dependencies. Verified Corretto 21.0.13 and Java release 17. Command from that snapshot:

```powershell
./mvnw.cmd -pl workflow-test-host -am -Pintegration-tests '-Dtest=NoSelectedUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=SharedInstanceViewerIT' verify -B -ntp
```

One integration test passed, zero failures/errors/skips; no unit/command/replay suite was selected. The guarded initializer restricted the datasource to `localhost:1433/apolo_workflow_test`; Liquibase reported 0 new and 12 previously applied changesets. The test verifies all four routes for anonymous (401), unrelated reviewer (404), current reviewer and completed-assignment historical reviewer (200), plus administrator reads. It verifies active sequential and completed optional graph projections, history continuation, exact retained number text, and unchanged instance metadata after reads. Test-owned fixtures were cleaned by the passing `AfterEach`, including zero remaining instance-row assertions.

The first sandboxed command could not write Maven cache locks and reached no tests. The permitted rerun then exposed a test setup defect: enabling the console adds `consoleCapabilities`, while the original fixture added a second `DefinitionCapabilityProvider`; core correctly rejected two providers. `SharedInstanceViewerIT` now explicitly replaces that named bean using `@TestBean` with bean-definition overriding enabled only in this test context. An intermediate retry lacked that test-only override setting and failed before fixture creation. The final command above passed after the correction. Production activation, adapters and core remain unchanged. Local logs and Failsafe reports stay under the ignored snapshot; final report is `workflow-test-host/target/failsafe-reports/io.github.leontecristian.workflow.testhost.SharedInstanceViewerIT.txt` within it.

**REUSED:** prior F013 isolated Java/frontend, production packaging and NgModule browser evidence above, and the mapped F012 evidence for unchanged excluded behavior. No production source or dependency change invalidates them. This command's ordinary Maven packaging is not a fresh console-ui build or packaged-assets browser pass.

T07 remains PARTIAL only for packaged sandbox asset/CSP-serving/browser acceptance; the guarded integration and focused development-console observations above now pass. The instance-11 finding remains unresolved separately. Actual APOLO integration remains outside scope. The owned integration JVM 27104 exited normally (exit code 0; subsequent process lookup found none); no temporary server remains. Owner-started backend/frontend were left running. No commit or push was made.

### 500 exception logging correction — 2026-11-07

Owner identified missing server-side exception diagnostics. Inspection confirmed the extracted viewer handler did not log failures, while the sandbox `ConsoleErrors` handler logged only code/correlation without the exception. Both now pass the original exception to SLF4J at ERROR for handled 500 responses, retaining its stack trace and cause chain alongside the existing safe category and trusted correlation ID. Expected 4xx responses do not gain ERROR logging; response status/envelopes remain unchanged. Both using modules declare `slf4j-api` directly, with the parent-managed version and host-owned logging implementation/configuration. R07 and P04 record this narrow correction.

Fresh PASS: Corretto 21.0.13, isolated snapshot `.local/f013-verification`, command `./mvnw.cmd -pl workflow-test-host -am '-Dtest=WorkflowViewerUnitTest,ConsoleErrorsUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' test -B -ntp`: 9 viewer tests and 1 sandbox error-handler test, zero failures/errors/skips. New logging assertions cover unexpected and configuration failures, retained exception/cause stack traces and correlation, unchanged sanitized client envelopes, and no ERROR entry for expected 404. The first run exposed a Mockito re-stubbing issue in the new viewer test (replacing a throwing stub invoked that stub); switched to `doThrow` re-stubbing, then both suites passed. Reports/logs remain in the ignored snapshot. No database, host startup, frontend build or browser rerun was needed for this handler-only change. Prior integration/browser evidence remains REUSED for unchanged reads/rendering; production packaging remains prior evidence, not a fresh package of this correction.

Owner processes were not rebuilt/reloaded/restarted: the running IntelliJ backend must load the updated classes before a repeated instance-11 request can establish its live exception cause. That original failure and packaged browser acceptance remain open. No commit or push.

### Owner logging retest and failure diagnosis — 2026-11-07

The owner supplied the backend log from 15:15:45 +02:00 after retesting. **PASS, owner-executed live logging observation:** `WorkflowInstanceViewerController` logs ERROR, correlation `5796441a-6b77-4cd1-9290-ead8e9adb1a2`, HOST_CONFIGURATION and the full `WorkflowExecutionException` stack trace. The original exception category is UNSUPPORTED_VERSION at `RuntimeDefinitionPins.verify`, line 34, reached by `WorkflowRuntimeQueryService.findInstance` through the access adapter. Source inspection locates that line at the exact requirement `fact.getDetailsSchemaVersion() == 2` for history sequence 1. Thus the failing request has an unsupported retained start-event schema, rather than a graph-rendering failure. The log does not include the requested instance ID or actual stored schema value; the conversation associates this retest with the earlier instance-11 failure, without an independent database read.

This diagnoses the previously unexplained failure. F009 revision 2/D17 and the F009 baseline exception in constraints.md explicitly exclude pre-F009 readers/conversion for existing test data; no compatibility implementation or data rewrite is implied. The current viewer maps the core UNSUPPORTED_VERSION category to its specified safe 500 HOST_CONFIGURATION response. The existing record remains unsupported and was not repaired. No additional build, tests, browser actions, database access, process changes, commit or push were performed for this log/source-only diagnosis; packaged browser acceptance remains open.

### Backend-served production assets and final acceptance — 2026-11-07

**PASS — remaining P04 packaged-console browser gate; T07 complete.** Reused the five production assets from the successful `.local/f013-package` console-ui build. Copied them into the isolated guarded host's source resources and verified every copy by SHA-256. No frontend source/dependency changed after that build, and no duplicate frontend build was run. This verifies backend serving of those production assets; it is not a new executable-JAR launch or production deployment.

Started only `SharedInstanceViewerIT` with its existing `-Df013.browserHold=true` option in `.local/f013-verification`, using the earlier guarded integration command. This repeat was needed to retain test-owned browser fixtures and an HTTP listener for the remaining asset-serving check. The host used only `apolo_workflow_test`, PID 31816, temporary port 61768, active fixture 617 and skipped fixture 618. Chrome used `localhost` and the fixture administrator account, keeping the owner's `127.0.0.1` session separate.

The production console login and Angular application booted from the backend. The shared viewer rendered instance 617 at PARTNER, token 3, history watermark 9, with manager completion and current partner highlighting. Ordered history and exact TASK_COMPLETED event 5 rendered through the packaged components. Its downloaded 103-byte JSON retains the exact comment spelling `9007199254740993` and `12500.00`. Chrome's captured warning/error logs were empty on the viewer and event pages, including no observed script/style policy violations. REUSED: the unchanged sandbox security-header/CSP contract from F012, confirmed by source inspection of the active `ConsoleSecurity` configuration. A separate raw-header capture was not obtained: the sandboxed local HTTP probe was denied, and the later normal-host probe found port 8080 unavailable. Neither probe establishes a policy failure; no policy/configuration was changed, and the production-assets browser check itself passed.

Signalled the fixture's existing completion file after the browser check. Failsafe reports 1 test, zero failures/errors/skips, BUILD SUCCESS at 15:19:35 +02:00; the fixture's scoped cleanup passed. The test JVM exited normally, and a later process lookup found no PID 31816. The temporary browser tab was closed. No owner process was stopped; no development data was changed. Local reports/logs remain under the ignored isolated snapshot.

Final scoped evidence combines the prior isolated/package/NgModule results, real-policy guarded integration, development-console interaction checks, owner logging retest and this production-assets pass. All F013 tasks are complete. Instance 11's unsupported legacy start-event format is diagnosed and outside the agreed F009 compatibility scope; APOLO policy mapping/production integration remains separate. No additional F013 test is required absent a relevant change or newly observed defect. No commit or push.
