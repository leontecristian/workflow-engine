# F039 implementation record

2026-11-24 · Revision 1 implementation authorized by the owner. The starting worktree at `628d6be` contained the F039 feature documents and their feature-index and roadmap entries.

The shared inbox uses the task header, Task Administration filter and footer styles, and a host-mapped task-name link. Its count, applied chips and rows represent the same successful page. The authorized personal read uses retained closure facts and current task-read permission, with full-precision creation time and numeric ID as the descending keyset. The HTTP cursor carries that position and is scoped to the actor and applied criteria. Existing ID-ascending Java reads remain available. The rollout requires compatible core, web and UI packages. Custom UI providers must accept the filter argument and supply `createdAt`. See the [web](../../../workflow-web/README.md#f039-personal-inbox) and [Angular](../../../workflow-ui/README.md#runtime-components-and-recovery) integration guides.

## Verification

- **PASS — core unit.** Corretto JDK 21.0.13, Java release 17. `PersonalTaskReadUnitTest` (5) covered current task-read permission regardless of status, current-assignee and membership handling, exact filters, literal pinned-name matching, precise cursor validation, 500 denied candidates and byte-limit resumption. `AuthorizedRuntimeReadUnitTest` (14) and `SharedAssignmentUnitTest` (6) passed during the initial reactor run. The new cursor-precision assertion initially ran against an earlier compiled revision and passed after recompilation.
- **PASS — web unit.** `WorkflowRuntimeWebUnitTest` (18) and `WorkflowReassignmentWebUnitTest` (11) covered endpoint validation, delegation, exact IDs and creation timestamps, empty continuation, actor/filter/order scoping, old cursor rejection and existing administration behavior.
- **PASS — HTTP adapter.** `node --test http-adapter.test.mjs` in the NgModule consumer, 5 cases. The My Tasks calls sent exact filters and passed through the seven-digit creation timestamp and an empty-page cursor.
- **PASS — Java packaging.** The scoped Maven verify run compiled and packaged core, web and the executable host before the initial SQL fixture failed. No schema change was required.
- **PASS — Angular builds.** Node 22.22.0. The shared package built with ng-packagr. The sandbox frontend synced that package and passed its production build and Content Security Policy style check. The sandbox build reported Sass deprecations and its 900 kB bundle warning threshold.
- **PASS — controlled NgModule browser fixture.** `ng build --configuration myTasks --output-path dist/f039` compiled the production fixture under the existing hard bundle limit. Headless Chrome 157.0.8103.79 exercised the shared components against controlled reads at 1280, 800 and 320 px. A temporary local assertion script drove the checks. The final CSS change gave the title the full width at 320 px and moved the count to the right of the eyebrow. The rebuilt package and fixture passed the affected narrow checks. The normal sandbox browser launch failed at Chrome Crashpad. The isolated browser launch completed, and its temporary browser, profile and listener were removed.
- **PASS — guarded SQL Server and personal endpoint.** Two `SharedAssignmentIT#personalInbox*` cases passed on `localhost:1433/apolo_workflow_test`. Across two HTTP pages, an earlier ID was newest by 100 ns, equal creation timestamps straddled the cursor in descending ID order, and a recently completed older task appeared last with its original creation timestamp. An unclaimed completion was visible to its completer, hidden from a peer, and returned the recorded decision and comment from the existing submission endpoint. A directly assigned cancellation appeared under CANCELLED and ALL for its reviewer. It was absent from OPEN and from the administrator's personal CANCELLED results even though the administrator cancelled it. The initial ordering fixture altered the task timestamp independently of creation evidence and was rejected by completion. The passing fixture supplies precise clock values at start, so stored tasks and their evidence agree. Liquibase ran zero new changesets, and the test cleanup was scoped to its own records.

The corrected SQL run used `./mvnw.cmd -o -B -ntp -pl workflow-test-host -am -Pintegration-tests '-Dit.test=SharedAssignmentIT#personalInbox*' '-Dfailsafe.failIfNoSpecifiedTests=false' test-compile failsafe:integration-test failsafe:verify`. It reused the unaffected unit and packaging results.

## Acceptance

- **AC01 PASS.** One heading and the right-aligned count remained visible with the long title at narrow width. Horizontal scrolling stayed with the table, while the footer remained accessible. The same task displayed the same Created date and time in Task Administration. The task-name link opened the host-mapped route by keyboard.
- **AC02 PASS.** The initial OPEN selection, responsive outlined filter grid, literal name and exact reference, explicit Apply, chip removal, dependent reference clearing and missing-kind validation were exercised. A failed read cleared the results and chips. A delayed response could not restore them after session replacement.
- **AC03 PASS.** Browser paging preserved the supplied ordering. SQL Server and the HTTP cursor proved the full-precision creation order, ID tie-break across pages, and placement by creation time after a later closure.
- **AC04 PASS.** The count and footer agreed on populated and empty pages. Next followed an empty-page continuation. Previous re-fetched the visited page. Refresh and size changes returned to page one. Keyboard and boundary disabled states were exercised. The core unit check separately proved continuation at the 500-candidate limit.
- **AC05 PASS.** The personal endpoint proved the shared completion, assignee cancellation and exclusion of an unrelated peer or administrator. Controlled UI reads exercised COMPLETED, CANCELLED and ALL, a peer session and revoked access. The mapped completed detail showed the decision and retained comment with no completion controls. The policy unit check proved current read denial excludes a personal candidate.

## Review and handover

Independent review found two issues. The ownership projection now uses the direct USER target for a legacy closed task without a retained assignee. The guarded cancellation case passed again with that legacy shape and confirmed “Assigned to you” on both the inbox and detail reads. The 18 runtime web unit tests and Java packaging passed after the projection correction.

The existing read-projection assertions moved into `SharedRuntimeIT#amountReviewReadProjectionsExposeCreatedOnlyInThePersonalInbox`. It passed against the guarded database, proving that the inbox exposes the exact creation instant and the task and instance-task reads retain their existing projections. The broader completion/replay method remains open against a pre-existing assertion rejecting `workflow.instanceId`, which the [F034 task-header contract](../F034-shared-task-page/spec.md) explicitly permits. It stopped before reaching the F039 assertions during the review follow-up.

The final diff, new-file whitespace and feature links passed inspection. The SQL harness JVMs exited, and no temporary test host or browser listener remains running. The browser checks used the dedicated fixture. A live APOLO walkthrough was outside this feature's verification scope. Changes are uncommitted.

## Toolbar and sandbox title-band follow-up

2026-11-24 · The count moved to the table toolbar's right edge and stays on the Refresh row as filters wrap. The sandbox inbox now supplies its title to the same white shell band as the individual task page. The shared inbox suppresses its own header when the host opts into this layout.

- **PASS — affected builds.** The Angular library, controlled inbox fixture and sandbox production frontend built. The sandbox also passed its existing Content Security Policy style check.
- **PASS — toolbar.** Chrome verified the count and Refresh were aligned with the table edge at 1280, 800 and 320 px. At 320 px the alignment held with the advanced panel open, on an empty page and after following its continuation.
- **PASS — sandbox shell.** Chrome served the rebuilt sandbox with controlled HTTP responses at 1280 and 320 px. My Tasks and the task detail used the same white title band and heading typography, each with one H1. The inbox title aligned with the table card, `13 on this page` aligned with Refresh, and the task link and breadcrumb return worked. Temporary browser processes and listeners exited.

## My Tasks subtitle follow-up

2026-11-24 · Restored the previous My Tasks subtitle in the shared inbox and sandbox title band. Both use the same default copy.

- **PASS.** Built the Angular library, the NgModule `myTasks` configuration, and an isolated sandbox frontend with Node 22.22.0. The first sandbox fixture build used an old package declaration file and passed after refreshing the complete package copy.
- **PASS.** Ran the focused Chromium probe for each header at 1280 and 320 px. Verified one H1, the exact subtitle immediately below it, title alignment, placement above the table card, and no horizontal page overflow. The temporary browsers and listeners exited. Live sandbox verification was outside this check.
