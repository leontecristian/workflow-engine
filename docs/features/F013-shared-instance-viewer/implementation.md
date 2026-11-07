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
