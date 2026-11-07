# F012 — Angular 19 frontend alignment

**Status:** READY, revision 1 — 2026-11-07. **Owner:** Workflow project owner. **Constitution:** [6.0.0](../../constitution.md). **Authorization:** specification preparation only. READY describes the specified scope; implementation requires a subsequent request.

## Purpose and authority

Align the existing sandbox console with the adopted Angular 19.2.x target before extracting reusable frontend code. Preserve the console's delivered behavior while establishing a reproducible dependency and runtime baseline. This feature does not create workflow-ui or workflow-web.

Authority: constitution C01/C03/C09/C10; [CON-01–03/08–12/14](../../constraints.md); [ASM-01/08/11](../../assumptions.md); [design-source precedence](../../design-sources.md); [UI/web integration record](../../architecture/workflow-ui-web-integration.md). Existing console behavior is governed by [F006](../F006-test-host-definition-console/spec.md) and the extensions through [F009](../F009-json-execution-variables/spec.md), [F010](../F010-human-task-outcomes/spec.md) and [F011](../F011-sequential-human-stages/spec.md).

## Scope

Selected: dependency/compiler alignment, explicit Angular 19 application bootstrap, compatible Material styles/templates, existing test harness compatibility and unchanged standalone-console packaging. Retain the current source layout, standalone components and hash routes. Do not convert the application to NgModules merely because APOLO uses them.

Excluded: library extraction, new Maven modules, shared web endpoints, backend behavior/API/schema changes, APOLO source changes, authentication replacement, new designer functionality, structured submissions and broader engine features. No migrations, fixture rebaseline or data conversion is needed. F011 specifications, fixtures and historical evidence remain intact; frontend compatibility fixes may touch code used by F011 without changing its behavior.

## Requirements

### R01 — Reproducible Angular 19 dependency baseline

Use Angular framework/compiler/compiler-cli 19.2.x with mutually compatible CLI/build tooling and Material/CDK 19.2.x. Pin direct dependency versions and commit the matching npm lockfile; one resolved Angular framework runtime must serve the console. Do not use force installation, legacy peer-dependency resolution or overrides to conceal incompatibility.

Select TypeScript 5.8.3. Retain Node 22.22.0, npm 10.9.4 and RxJS 7.8.2 unless demonstrated incompatibility requires revising this specification before dependent edits. Keep package engines and the Maven console-ui profile consistent. Retain jsonc-parser and exact-value helpers. Choose the exact compatible 19.2 patch set and Zone.js version from published package metadata during T02, recording it before changing dependencies; these are bounded engineering choices, not evidence of APOLO's installed versions.

Angular's [compatibility matrix](https://angular.dev/reference/versions) lists TypeScript >=5.5 and <5.9, Node 22.x and RxJS 7.x within its Angular 19.2 ranges. The current TypeScript 5.9.3 is therefore outside that range. The matrix now lists Angular 19 as unsupported; the owner-selected compatibility target remains 19.2, and this feature makes no supported-production-baseline claim.

### R02 — Explicit, functioning bootstrap and rendering

Use Angular 19's conventional Zone.js-backed application bootstrap, with a compatible Zone.js dependency loaded once and explicit zone change-detection configuration at the application boundary. Do not introduce experimental zoneless APIs: Angular 19 documents them as [experimental](https://v19.angular.dev/guide/experimental/zoneless). This is a sandbox bootstrap decision, not a provider contract for the future library or a change to APOLO.

Preserve standalone component composition, strict TypeScript/template checking and current routing. Async fetch responses, loading/error states, signal updates, form changes, navigation and Material overlays must render without manual page reloads. Retain cleanup on component destruction/logout; do not introduce duplicate requests or subscriptions to compensate for rendering failures.

### R03 — Preserve delivered workflows and exact data

Preserve the current routes, role-specific navigation, catalog/definition editing and publication, sandbox requests, instance/task/activity/history views, graph behavior, event/inbox/audit tools and command recovery. Retain existing validation, conflict handling, unsaved-input protection and unavailable/error states. Compatibility work must not weaken CSP, CSRF, session handling or server-authoritative authorization.

Keep IDs/versions exact strings, scalar encodings and JSON numeric text unchanged through editing, transport, display, copy and download. Preserve omitted/null distinctions. A pending or exported command retains its original ID, expected version and payload; retry/import must not silently replace them with current state or automatically resubmit after a failure.

For F011, completion receipts identify the submitted task, while separate authorized reads identify the current stage. Preserve pinned per-stage outcomes/comments, historical task contracts and correct graph highlighting through successive and skipped stages. A rendering or refresh error must not imply that a committed completion failed or trigger another completion.

### R04 — Preserve usable Material presentation

Adapt only Angular/Material-incompatible APIs, templates and styles. Preserve readable forms, validation messages, tables, dialogs/overlays, navigation and graph controls, including keyboard focus and narrow-screen usability. Pixel-identical output is not required; missing controls, clipped actions, invisible overlays or unreadable state distinctions are regressions. Keep current styling intent and do not add a new widget framework or redesign the console.

### R05 — Preserve build, serving and packaging contracts

Retain npm start, build and test:unit entry points. The production build must continue producing dist/console/browser for the Maven console-ui profile to copy under static/console. Preserve /console/ base URL, hash-route reloads, development proxy behavior and explicit opt-in frontend packaging.

Retain the production HTML/CSP check in scripts/check-build.mjs and existing build budgets. Do not relax security, type checks or budget limits to obtain a successful build. A reproducible npm ci, production build and Maven packaging check must succeed without changing backend endpoints or normal database configuration.

### R06 — Evidence distinguishes alignment from integration

Record the starting code revision and existing F011 browser gap before dependency edits. Capture the focused pre-change browser baseline when available; if blocked, retain the blocker and use existing contracts as the expected behavior rather than claiming a successful comparison. Post-change browser observations remain required for UI acceptance even when isolated tests/builds pass.

Do not rewrite historical feature evidence or declare prior acceptance gaps closed without matched observations. An Angular 19 sandbox build does not establish NgModule library consumption, APOLO authentication/theme/localization compatibility or production integration; those remain later feature gates under ASM-01.

## Acceptance scenarios

| ID | Scenario and required observation |
| --- | --- |
| AC01 | Clean lockfile install resolves the pinned Angular 19.2 framework/compiler, compatible build/Material/CDK and TypeScript 5.8.3, with no invalid peer dependencies or second framework major. Tool versions match R01. |
| AC02 | The console boots under Angular 19; async success/failure/loading changes and Material overlays update visibly without reload. Navigation away and logout leave no stale private view or duplicate request behavior. |
| AC03 | Existing administrator and reviewer routes work, including direct hash links, reload, unknown route and unauthorized/error states. Catalog and definition editing retain validation, stale-version feedback and unsaved-input protection. |
| AC04 | Request creation preserves exact JSON data; start inputs and variable/history display and downloads preserve the supported LONG/DECIMAL/JSON encodings, including 9007199254740993 and 12500.00 inside JSON, plus null/omission distinctions. Task completion separately preserves exact IDs/versions, the selected outcome and comment text. Scalar task submissions and structured task results remain unsupported under F011's FORBIDDEN result contract. |
| AC05 | Frozen command export/import/retry preserves original identity, version and payload after an uncertain response; replay displays the original receipt and separately refreshed current state, without duplicate submission. Logout clears protected in-memory state as before. |
| AC06 | F011 two-stage and optional-stage fixtures display the reached stage and pinned outcomes/comments correctly. Completing stage one retains its completed-task receipt while refreshing successor work; skipping a stage and final completion render correctly. Earlier task/history links retain their original identity. |
| AC07 | Instance/history/graph views, pagination and event/inbox/audit/capability screens remain usable; denied reads, unavailable graph and refresh failures remain explicit. Keyboard operation and narrow-screen checks find no inaccessible primary actions or hidden overlays. |
| AC08 | Production npm build passes strict compilation, budgets and check-build.mjs. The console-ui Maven package contains the expected console assets; serving them preserves /console/, hash routing and existing CSP without asset failures. |
| AC09 | Existing isolated frontend tests run with recorded discovery/counts and retain their assertions; any harness adaptation is explained. No tests are deleted or weakened just to accommodate the dependency change. Browser verification is recorded separately. |
| AC10 | Final diff contains no new library/module, backend contract, migration or F011 feature-record change. Handover records the version matrix, checks, remaining browser/APOLO gates and cleanup of owned temporary processes. |

## Decisions and readiness

The major/minor target, scope, toolchain, change-detection approach and observable behavior are selected by this specification. Exact compatible patch pins remain T02 engineering work; resolve them before dependency changes and retain the matrix in implementation.md. If the selected contract proves infeasible, revise spec/plan/tasks before dependent implementation. APOLO's exact installed packages and future library contracts remain OPEN and do not block this bounded console alignment.
