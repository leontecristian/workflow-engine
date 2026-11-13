# Runtime task display maintenance — 2026-11-13

The instance task list showed only assignment roster keys such as `DIRECT` and
`FINANCE`. Display the human-step name from the instance's pinned workflow and
the task-type catalog name in instance tasks, assigned tasks and task details.
Keep the roster key and exact task ID available to distinguish sibling tasks.
This is a read-only presentation fix; catalog metadata is not the pinned step name.

The runtime HTTP task projections add `nodeName`, `taskType` (the pinned code)
and `taskTypeName` (current catalog metadata) after host
authorization. Resolve only the authorized tasks' own pinned nodes, with lookup
reuse within each response; do not require broader instance or definition grants.
Core command DTOs and persistence remain unchanged.
Task-type names resolve by the retained task-type ID, including nonselectable
entries. A catalog rename is reflected on the next read; pinned step names and
type codes remain unchanged. Cache catalog lookups only within each response.

Planned verification: focused runtime MVC unit suite, Angular library/console
builds, and the existing instance 61 task-list rendering when the running host
contains the updated adapter. Stop after these relevant checks.

Actual results (2026-11-13, uncommitted worktree):

- PASS: JDK 21.0.13, root wrapper command
  `./mvnw.cmd -pl workflow-web -am -Dtest=WorkflowRuntimeWebUnitTest -Dsurefire.failIfNoSpecifiedTests=false test`;
  13 isolated tests, no failures/errors. The extended MVC scenario checks the
  detail, instance list and assigned list, retained keys/cursor, request-local
  definition lookup reuse and absence of broader host access requests. The first
  sandboxed attempt could not write Maven cache locks; the permitted rerun passed.
- PASS: `npm run build:viewer` and `npm run build` from
  `workflow-test-host/frontend`; shared Angular templates compile, console build
  and production CSP style check pass. Sass deprecations and the 900 kB warning
  budget remain warnings (936.25 kB initial bundle, below the 1 MB error budget).
- PASS: final diff review and `git diff --check`, including this new document.
- Browser observation: the supplied instance 61 list and task 64 detail reproduce
  the missing labels. Updated live rendering is NOT RUN: the user's existing
  backend/frontend processes still serve the old code and need restarting with
  the updated adapter/library. No host, database or temporary server was started;
  existing processes were left running.

No schema or command change. Changes are not committed.

Follow-up correction: the owner requested the catalog name instead of the type
code as the visible task-type label.

- PASS: fresh JDK 21 wrapper run with `-pl workflow-test-host -am
  -Dtest=WorkflowRuntimeWebUnitTest,ConsoleInstancesUnitTest,ConsoleSequentialStagesUnitTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed all 13 runtime MVC tests and
  four instance host-adapter tests. The sequential-stage test initially failed
  because its existing fixture omitted the previous-decisions policy. Updated
  that fixture and the controller's new catalog/label dependencies; the focused
  rerun with `-Dtest=ConsoleSequentialStagesUnitTest` passed its one test. No
  database was accessed. Catalog-name assertions cover all three task read routes
  and a nonselectable type; repeated sibling rows share a response-local lookup.
- PASS: fresh `npm run build:viewer` and `npm run build` in the frontend,
  including template compilation and production CSP check. Same warning classes
  and 936.25 kB initial bundle as above.
- PASS: diff/content/whitespace review. Prior live-rendering gap remains;
  existing processes were left running, and no temporary server was started.

## Previous-decision message layout

Owner-requested visual follow-up: present previous decisions as a compact ordered
list with reviewer initials, a separate outcome badge, step heading, subdued
actor/time metadata and readable proportional-font comment text. Preserve exact
comment whitespace, escaped text, ordering and existing paging/error/empty states.
Long names/comments wrap within the card; metadata stacks on small screens.
Outcome styling is neutral and does not infer approval semantics from custom keys.

Planned checks: Angular library/console compilation and a focused desktop/narrow
browser observation using retained task 67. No new engine/API behavior, database
fixtures or Java tests are needed.

Actual results:

- PASS: `npm run build:viewer` and `npm run build` in the frontend. Production
  templates and CSP style check pass; initial bundle 936.48 kB, retaining the
  existing warning budget/Sass warning classes. An earlier parallel build was
  interrupted during preview shutdown; the final standalone build passed.
- PASS: Chrome preview of retained task 67 using the existing authenticated
  backend, desktop and 390 × 844 viewport. All three comments remain exact in
  the DOM, outcomes are separate badges, metadata stacks on mobile, and every
  mobile card has equal client/scroll widths (308 px), without horizontal overflow.
  Viewport override reset and preview tab closed.
- Preview attempts using development/HMR builds exceeded the existing size
  budget; production settings with HMR disabled passed, without changing budgets.
  The sandboxed preview was unreachable from Chrome; the permitted local preview
  on port 4201 worked. All temporary preview sessions (including failed watch
  attempts) were stopped through their own command sessions. Existing host and
  port 4200 frontend were left running; no database fixtures or commands changed.
- PASS: final diff/content/whitespace checks. F022's comment-access and paging
  behavior is REUSED from its implementation record; this change only adjusts
  template markup/styles. Java/database tests NOT RUN for this presentation-only
  change. Changes remain uncommitted.

## Commit handover

The owner requested committing these UI improvements. Final scope includes task
step/type labels, their runtime read projection and focused fixtures, the previous
decision layout and this maintenance record. Earlier test/build/browser results
are REUSED for the unchanged implementation; no checks were repeated solely for
the commit. Final tracked/new-file diff review and staged whitespace checks are
performed before committing. No push is requested.
