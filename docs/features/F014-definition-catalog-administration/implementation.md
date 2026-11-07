# Implementation — F014 definition and catalog administration

## Preparation — 2026-11-07

Revision 1 DRAFT; T01 only. No F014 implementation exists. Starting HEAD: `2944287`, the F013 specification/focused-verification documentation commit. Another agent is implementing F013; the starting worktree already contained its edits to F013 implementation.md, root/host POMs, runtime controllers, graph sources and new viewer/web files. These were not modified, staged, reverted or tested here. This task creates only the four files in this F014 folder.

Inspected the current ConsoleTaskTypeController, ConsoleDefinitionController, ConsoleManagement, ConsoleContext, management audit store, catalog/definition pages, graph/fragment helpers, reload dialog integration, core service contracts and governing specifications. Important observations:

- Existing administration consists of catalog list/create/update and family/version list/read/metadata/import/replace/validate/publish/retire/export operations. Existing core contracts already provide the required behavior.
- Both controllers require the sandbox administrator role. A single host administration check preserves that simple access model without introducing a permissions subsystem.
- ConsoleManagement already owns the transaction, trusted actor/time and changed-result audit. Extraction can delegate through a typed host adapter and preserve this implementation. The audit table/store and audit screens are host-specific.
- Current UI has four main page groups, exact-text document handling, saved-document graph/fragment editing and F012's Material reload dialog. SANDBOX_REVIEW is currently a catalog-page default and must become host-supplied when shared.
- F013 source/build APIs are changing. The draft specifies reuse rather than inventing a parallel codec, security, navigation, localization or packaging framework. T02 must reconcile with its completed result.

## Decisions and evidence reuse

Selected scope is extraction of existing administration, not new engine/designer functionality. One administration grant covers all operations. The mutation adapter is a real host boundary, not a mirrored core service interface; the existing sandbox transaction/audit implementation remains authoritative.

Planned reuse: F012 `943a51f` final administration/dialog observations, relevant F006 management transport/audit evidence and completed F013 package/consumer evidence. No fresh acceptance claim follows from reading them. Readiness and actual reuse applicability are confirmed in T02/implementation against the final source. Runtime idempotency/replay and unrelated engine test campaigns are excluded; core invariants and existing tests remain intact.

## Verification

Documentation preparation only. Static inventory, local links, requirement/task alignment and whitespace are checked for these four files. Existing dirty paths belong to active F013 work; their runtime behavior is not verified here.

Static review PASS: exactly four documents and all 12 local Markdown links resolve; no trailing whitespace or unmatched fences. Scoped `git diff --check` passed; new untracked documents were also checked directly. Source review corrected an initial route-guard assumption: the existing editor has dirty feedback/reload confirmation, but no general route-leave guard, and F014 does not add one. Final worktree inspection showed ongoing F013 changes accumulating independently; this task wrote only its new F014 folder.

| Check | Result |
| --- | --- |
| F014 code / AC01–07 runtime acceptance | NOT RUN — no implementation. |
| Tests, builds and package installation | NOT RUN — documentation only. |
| Host/browser/database checks | NOT RUN — documentation only. |
| APOLO integration | NOT RUN — outside this scope. |

No process was started/stopped, no database accessed and no commit/push made. Shared roadmap/index, F013 documents and application files were left to the active agent.

## Review — revision 2, 2026-11-07

Documentation-only review of the four F014 files against the current controller/ConsoleManagement boundary and focused-verification rules. Clarified full lifecycle paths, preserved existing error mappings rather than suggesting a new generic 422 rule, and distinguished committed writes from later response-delivery failures. Kept the single administration check and host transaction/audit service; no new permissions, retry/recovery mechanism or transaction abstraction.

Reduced fresh database coverage to one changed-write/audit boundary check and moved access/error forwarding to focused controller/security checks. Narrowed the browser pass to extracted page wiring, one draft save, graph/export and reload integration; reuse unchanged administration/dialog evidence instead of manually repeating lifecycle and fragment-editor acceptance. Reuse must identify actual supporting sections and limits. T02 blocks only on relevant F013 contract/evidence gaps; unrelated historical debt does not become an F014 prerequisite.

Only these four documents were edited. No implementation, tests, builds, database access or application processes; no commit or push. Review static checks PASS: four documents, 12 resolving local links, balanced fences, no trailing whitespace, and `git diff --check`. Untracked F014 files were checked directly because Git's diff does not include them.

## Handover after review

Wait for F013's completed handover, then reconcile its actual shared interfaces/build layout and relevant evidence under T02. No additional owner decision is currently needed for the simple administration scope. F014 remains DRAFT until that reconciliation; implementation needs a separate request. Use the focused P04 checks and stop when they pass.

## T02 readiness and authorized implementation — 2026-11-07

Starting HEAD `9a82eea` (completed F013); git status contains only these four untracked F014 documents. No active F013 source changes. Its final handover records all tasks complete including guarded test-database integration and backend-served production assets. Revision 3 is READY; no material blocker or additional owner decision.

Actual conventions: Spring Boot 3.4.5, Java release 17/JDK 21, three Maven modules; Angular 19.2.25, CLI 19.2.27, Material/CDK 19.2.19, TypeScript 5.8.3, Node 22.22.0/npm 10.9.4, ng-packagr 19.2.2. Local UI consumption builds `workflow-ui/dist/workflow-ui` then installs copied file dependencies (`install-links=true`). Explicit plain viewer configuration/controller plus dedicated mapping makes scanning inert; administration follows this convention independently. UI uses injected typed transport, AbortSignal, opaque sessionScope, UrlTree navigation and messages inputs.

REUSED baseline: F013 implementation.md final acceptance/package/NgModule sections at `9a82eea` establish unchanged viewer and build conventions; fresh F014 checks still cover added exports/routes/composition. F012 final acceptance and Material dialog follow-up at `943a51f` establish unchanged catalog lifecycle, fragment editing and detailed dialog keyboard/focus behavior. F006 management transaction/audit evidence is applicable only where ConsoleManagement retains the same transaction/audit implementation; F014 freshly checks delegation and one changed write/audit. No runtime replay/concurrency or unrelated historical gaps are reopened.

T03/T04 now authorized. Shared contracts and ownership are recorded in plan.md before parallel work. No tests/builds/database/process actions yet; readiness is source/document reconciliation, not fresh runtime acceptance.

## Implementation and focused verification in progress

Two requested GPT-6.1 Sol/medium workers implemented the bounded Java and UI slices with separate ownership; the lead owns integration/configuration. Java extraction preserves ConsoleManagement's transactional audit body, changing return typing and splitting the previous action dispatcher into typed lifecycle methods. Shared scoped codec parsing/paging reuses ViewerWire without changing viewer semantics; core/schema/APOLO unchanged.

Fresh PASS: JDK Corretto 21.0.13; root wrapper `-pl workflow-test-host -am -Dtest=WorkflowAdministrationUnitTest,AdministrationWireUnitTest,ConsoleAdministrationUnitTest,WorkflowViewerUnitTest -Dsurefire.failIfNoSpecifiedTests=false test -B -ntp`: 6 wire + 7 administration MVC + 9 viewer + 1 real-security isolated tests, no failures/skips. Initial sandbox attempt failed before compilation on Maven cache locks; permitted retry passed. Subsequent Java cleanup only made test imports explicit and will compile in integration packaging. No database was used by these tests.

Fresh PASS: pinned Node 22.22.0, six focused frontend files (catalog, definitions, fragment, topology, administration transport, pretty-document): 30 tests, no failures/skips. Original assertions retained and adapted to the extracted source; new checks cover late mutation/upload exclusion, denied clearing, retained successful result after refresh failure, exact CSRF/token/raw transport and replacement-session protection. A log path typo prevented the first command from starting tests; corrected invocation passed. Logs remain ignored under `.local/f014-*`.

Production packaging initially built the library successfully, then exposed Angular deep imports through local component reexports. The host now imports shared presentation helpers directly from the public package. Its next build compiled but exceeded the unchanged 1 MB hard initial budget by 9.3 kB. Lazy host administration routes alone did not split the primary library FESM (9.4 kB over). The existing Material dialog already remains lazy; no dialog framework or budget increase is needed. A concrete administration component secondary entry is being introduced to keep these four pages lazy while retaining root contracts/tokens and shared pure helpers. This is a build-driven entry-point choice, not another library/module or new behavior; planned P03 now records it. Failed attempts do not establish package acceptance.

## Final implementation and acceptance — 2026-11-07

F014 revision 3 is IMPLEMENTED. T02 established readiness against completed F013 before edits; the current request authorized the remaining implementation. Earlier DRAFT/NOT RUN records above describe historical preparation, not the final state.

`workflow-web` now owns the explicitly activated administration mapping, scoped wire/error handling and required host adapter. The sandbox supplies one administration access check and delegates typed mutations to its existing transactional/audited ConsoleManagement service. The two duplicate sandbox controllers are removed. `workflow-ui` owns the four administration pages and existing graph/fragment/reload aids; root contracts/tokens and the `@workflow-engine/ui/administration` component entry are consumed by thin sandbox wrappers and the existing NgModule fixture. Hosts retain credentials, CSRF, navigation, sample defaults, transactions and audit. No engine semantics, migrations, APOLO sources, permission framework or authoring functionality changed.

### Fresh verification

| Check | Final outcome |
| --- | --- |
| Focused Java isolated suites | PASS: 23 tests (6 wire, 7 administration MVC, 9 shared viewer, 1 host security). Command recorded above; no database. |
| Focused frontend suites | PASS: 33 tests: 30 administration/fragment/topology/transport/exact-text checks, 1 route-wiring check and 2 applicable existing API/query checks. |
| Angular library and consumers | PASS: primary and administration secondary entry; sandbox production initial bundle 953.71 kB, below unchanged 1 MB hard budget; CSP script check passes. Existing 900 kB warning and Sass deprecations remain. NgModule production fixture passes, 806.08 kB initial. |
| Guarded host integration | PASS: SharedAdministrationIT, 1 test, zero failures/errors/skips. One extracted catalog write commits with its host audit. Only localhost:1433/apolo_workflow_test; Liquibase reports 12 existing changesets and zero new. |
| Final Maven integration/package | PASS: command below, stable combined sources and already-built frontend assets. Eleven packaged console assets byte-match dist; workflow-web JAR included and old controller classes absent. |
| Focused sandbox browser | PASS: catalog/list/family/version wiring; saved draft label and revision advance; saved graph selection and exact export; dirty reload cancel retains edits and explicit discard restores saved text. Browser warning/error log empty. |
| Existing NgModule consumer browser | PASS: administration list rendered with host heading/theme, host HTTP interceptor and alternate host navigation; exact ID 9007199254740993 retained. Warning/error log empty. |

Final integration/package command (JDK Corretto 21.0.13, root wrapper):

```powershell
./mvnw.cmd -pl workflow-test-host -am '-Pconsole-ui,integration-tests' '-Dskip.npm=true' '-Dtest=NoSelectedUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=SharedAdministrationIT' '-Df014.browserHold=true' verify -B -ntp
```

The frontend used the repository-installed Node 22.22.0/npm 10.9.4. `skip.npm` deliberately reused the successful library/sandbox production output rather than repeating identical builds. Failsafe reports and temporary logs/export are ignored build/local artifacts, not new evidence documents.

Relevant reruns were limited to concrete failures/changes: public imports replaced invalid Angular deep imports; the secondary entry and physically separate source root fixed the unchanged bundle budget and ng-packagr source-boundary failure. The focused frontend suite was rerun after the entry move; only the route test needed another run after adding its secondary-entry loader mapping. The first integration run completed the representative write/audit but browser-fixture setup rejected an unowned sample task type; the fixture now binds its human nodes to its own catalog row, cleans up on failure, and the same single integration check passed on rerun. No broader engine campaign followed these failures.

### REUSED evidence and limits

- **REUSED — F013**, implementation.md final acceptance/package/NgModule handover at `9a82eea`: unchanged viewer behavior and shared activation/build/consumer conventions. Fresh checks above establish the added administration exports, composition and assets.
- **REUSED — F012**, final acceptance and Material reload-dialog follow-up at `943a51f`: unchanged catalog/lifecycle operations, fragment editing and detailed Escape/backdrop/focus behavior. Fresh focused tests and short browser observations cover the extracted wiring, session disposal and reload integration.
- **REUSED — F006**, implementation.md section 4 AC01–AC22 evidence (AC02 catalog audit/stale behavior; AC09 audit-failure rollback), as present at `9a82eea`: applicable to the retained ConsoleManagement transaction/audit body. Fresh adapter/security tests and the representative committed write/audit cover the changed boundary. No fresh rollback, replay, concurrency or general engine acceptance is claimed.

APOLO integration is NOT RUN and remains outside F014. No required F014 runtime check remains blocked. Historical acceptance gaps remain with their owning features.

### Ownership and cleanup

Two GPT-6.1 Sol workers with medium reasoning completed bounded backend/UI tasks; shared configuration and feature documents remained lead-owned. Builds/database work were serialized. At the user's later instruction, remaining review/checks were delegated to the backend worker; no additional runtime checks were needed.

The guarded fixture removed only its random catalog/family/version/audit records and asserted no owned catalog/family rows remained. The test JVM (PID 29428) exited normally. The lead verified the identity of its temporary static server (PID 27516), stopped it, and confirmed both owned processes were absent. Temporary browser tabs were closed. The owned export was copied to ignored `.local/f014-export.json` and its exact Downloads file removed. No normal host was redirected, no development database accessed, no unrelated process stopped, and no database reset performed.

Final independent diff/document review is recorded below. No commit or push was made.

### Final delegated static review — 2026-11-07

PASS: the delegated verification worker reviewed the final tracked diff and new sources/fixture against F014, including explicit activation/access, typed host transaction/audit delegation, raw transport/error compatibility, administration entry-point boundaries, session/dialog disposal, consumer wiring and test-owned fixture cleanup. No actionable defect or concrete new regression gap was found. README, feature index, roadmap, constitution and the four F014 records remain consistent with the delivered scope and distinguish historical preparation from final results.

PASS: final `git diff --check`; direct checks covered all 55 untracked text files as part of 94 changed text files, with no trailing whitespace or missing final newline. Local Markdown validation covered 10 changed/new Markdown files, 162 local target links and 11 heading links, with no missing targets. No new replacement characters were introduced; pre-existing encoding artifacts in older navigation text were left unchanged. Rechecked these final T06/result edits. Git emitted only existing LF/CRLF conversion notices.

No runtime, database, build or behavior checks were repeated during this final review. The fresh focused outcomes and REUSED evidence above remain applicable; APOLO integration remains NOT RUN and outside F014. T06 is complete. Changes remain uncommitted; no push was made.
