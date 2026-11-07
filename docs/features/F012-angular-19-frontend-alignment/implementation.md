# Implementation — F012 Angular 19 frontend alignment

## Implementation run — 2026-11-07

The subsequent owner request authorizes T02–T06. Start: **8a0ed2d**, clean working tree; no F011 feature record/fixture changes. Installed tools verified before edits: Node **22.22.0**, npm **10.9.4**, Corretto JDK **21.0.13**. The Maven profile already pins those Node/npm versions and `dist/console/browser`; no POM change is planned.

### T02 baseline and resolved pins (before dependency edits)

Pre-change `npm run test:unit`: **84 passed**, zero failed/skipped. Discovery: four `unit/*.test.mjs` files and nineteen `src/app/**/*.test.mjs` files. Pre-change `npm run build` and CSP check passed; initial bundle **961.80 kB**, above the existing 900 kB warning threshold but below its unchanged 1 MB error limit. Logs stay ignored in `target/f012-baseline-*.log`.

F011's historical interactive-browser gap remains untouched. A fresh Chrome window could be controlled after the existing window was found minimized. On a temporary guarded test host (loopback 18112; `IntegrationDatabaseInitializer`; only `apolo_workflow_test`), the Angular 21 production snapshot booted and showed administrator navigation and async-loaded instance **596** / version **589**, ACTIVE at MANAGER with token 1 and history watermark 4. Its graph showed MANAGER current, BEGIN completed and PARTNER/END branches unreached, with exact task/activity links. This is a focused baseline, not retrospective acceptance of every F011 scenario. Post-alignment checks will cover the remaining F012 browser scenarios.

Published npm registry metadata was fetched on 2026-11-07, before changing package.json or the lockfile. Selected exact patches:

- Framework/common/compiler/forms/platform-browser/router and compiler-cli: **21.2.25 → 19.2.25**. Framework peers require the same exact patch; compiler-cli requires TypeScript `>=5.5 <5.9`.
- CLI/build: **21.2.29 → 19.2.27**. Build peers admit Angular compiler/compiler-cli 19.x and TypeScript `>=5.5 <5.9`; optional SSR/server/localize/packagr/test peers are not installed for this application.
- Material/CDK: **21.2.14 → 19.2.19**, paired exactly. Their peers admit Angular 19/20 and RxJS `^6.5.3 || ^7.4.0`.
- TypeScript: **5.9.3 → 5.8.3**, as specified. Zone.js: absent → **0.15.1**, satisfying core's `~0.15.0` peer.
- Retain Node **22.22.0**, npm **10.9.4**, RxJS **7.8.2**, tslib **2.8.1** and jsonc-parser **3.3.1**. Node/npm satisfy framework/tooling engines (`^18.19.1 || ^20.11.1 || >=22.0.0`, npm `^6.11.0 || ^7.5.6 || >=8.0.0`). This is sandbox compatibility evidence, not APOLO's installed package set.

Primary metadata: [core](https://registry.npmjs.org/@angular/core/19.2.25), [compiler-cli](https://registry.npmjs.org/@angular/compiler-cli/19.2.25), [build](https://registry.npmjs.org/@angular/build/19.2.27), [CLI](https://registry.npmjs.org/@angular/cli/19.2.27), [Material](https://registry.npmjs.org/@angular/material/19.2.19), [CDK](https://registry.npmjs.org/@angular/cdk/19.2.19), [Zone.js](https://registry.npmjs.org/zone.js/0.15.1). Full resolved peer/engine metadata stays ignored in `.local/f012-package-metadata.json`. The [Angular compatibility matrix](https://angular.dev/reference/versions) independently agrees with these TypeScript/Node/RxJS choices. Initial sandbox registry access was denied; authorized read-only registry access succeeded.

The selected Material 19.2.19 published sources for the console's controls were inspected before edits. They use native CSS animations and do not import `@angular/animations`; expansion/select/tooltip/sidenav use optional `ANIMATION_MODULE_TYPE`. No animation package or provider is selected without a control requiring it. Explicit Zone.js loading and `provideZoneChangeDetection` will live at the application bootstrap boundary; existing isolated tests do not bootstrap that boundary.

### T03/T04 alignment and compatibility results

Manifest/lockfile now use the recorded pins. `npm install --package-lock-only --no-audit --no-fund` resolved normally, then pinned `npm ci --no-audit --no-fund` installed 417 packages. `npm ls` for all Angular framework/compiler/tooling/Material/CDK, TypeScript, RxJS and Zone.js dependencies exited zero, with framework **19.2.25** deduped and no invalid peer dependencies/second framework major. No force, legacy peer resolution or override was used. npm reported transitive tar/glob deprecations; no unrelated override or dependency upgrade was added.

`main.ts` imports Zone.js once and explicitly supplies `provideZoneChangeDetection()` with default options, retaining hash routing and standalone bootstrap. The selected Material controls use CSS animations, so no unnecessary animation provider/package was added. Builder/serve schema, strict compilation, Material theme/tokens, source templates and test-loader imports were audited. The application builder, styles, tsconfig, proxy and CSP guard need no compatibility edits.

The first strict compile identified `DestroyRef.destroyed` as unavailable in Angular 19's public API. Fourteen pages now use the shared `destroyedState` predicate in existing `ui/page-state.ts`, driven by stable `onDestroy` callbacks. Existing route/generation checks and RxJS `takeUntilDestroyed` cleanup stay intact. The plan records this discovery before adaptation; no observable behavior/specification change is selected. The first added regression used a fake DestroyRef provider, which Angular's environment injector does not substitute for its built-in destruction scope; it was corrected to exercise real injector destruction. The resulting pending-task-read test verifies private task/contract state remains unset after destruction. No existing test/assertion was removed or weakened; the harness itself is unchanged.

### T05 automated and packaging verification — PASS

After adaptation, pinned `npm run test:unit`: **85 passed**, zero failed/skipped (same 23 files, original 84 cases plus one pending-task lifecycle case). `npm run build`: **PASS**, strict TypeScript/templates and existing CSP check. Initial bundle **972.79 kB**, above the unchanged 900 kB warning but below the unchanged 1 MB error threshold. Angular Material 19 Sass emits upstream deprecation warnings with the selected builder's Sass; no checks/budgets were relaxed and no vendor code was modified.

With `JAVA_HOME` selecting the verified JDK 21, root `./mvnw.cmd -B -ntp -Pconsole-ui '-Dmaven.test.skip=true' package`: **BUILD SUCCESS**, all reactor modules. The profile repeated its pinned clean npm install and production build. Initial sandbox wrapper/cache access failed; the same authorized operation succeeded with elevated sandbox access. Java unit tests and the Maven SQL integration suite were **NOT RUN** for this frontend-only change; packaging intentionally skips them. Packaging itself did not start a host or access SQL. Logs remain ignored in `target/f012-package.log`, `target/f012-dependencies.log` and frontend-check logs.

Packaged JAR inspection: `BOOT-INF/classes/static/console/index.html` retains `/console/` and references `main-QMQZZXMM.js` and `styles-SWLPIRQY.css`. Both assets exist and their SHA-256 hashes match `dist/console/browser` exactly. No inline event handlers were present. The live browser used those same Maven-copied assets from the host classpath under unchanged host security/CSP, rather than a development server. Dev-proxy execution itself was **NOT RUN**; its configuration and serving entry points are unchanged.

The baseline host PID **28016** was verified as F012BrowserHarness, signalled through its own stop file, and exited after scoped fixture cleanup/graceful shutdown. No owner host/process was stopped. No commit or push is authorized.

### T06 post-alignment browser evidence — PARTIAL / acceptance open

The second ignored F012BrowserHarness ran as PID **27644**, loopback **18112**, with `IntegrationDatabaseInitializer` and the integration-test profile. Startup explicitly reported **apolo_workflow_test**; Liquibase reported 12 already-applied and zero new changesets. Neither run accessed `apolo_workflow_dev` or changed the normal host datasource. This is database-backed manual browser evidence, separate from the isolated suite and packaging. No new rollback, concurrency or engine safety claim follows from it.

The harness imported unchanged F011 sequential/optional/three-stage JSON and the existing F006 request-status fixture under unique F012 workflow keys. It created only scoped test-owned requests/instances: post-run instances **602–609**, initial tasks **585–592**. Two-stage published version **592**, instance **603**, was exercised in Chrome with the Angular 19 production assets:

- Reviewer login rendered the restricted My tasks / Command recovery navigation and eight assigned task rows asynchronously without reload. The rows-per-page Material overlay was visible/readable; keyboard selection and Tab/Return pagination changed the row from task 585 / instance 602 to task **586 / instance 603**, retaining exact identity and visible focus.
- Task **586** displayed its pinned outcomes; APPROVE was enabled and REJECT disabled without a required comment. No scalar-submission or structured-result controls appeared. APPROVE committed with original expected token **1**, command `b82e741b-f82b-4652-8122-210c5c78e41c`. Recovery displayed the original completed-task receipt, ACTIVE at PARTNER, actual token **3**, history watermark **9**, separately from refreshed current work pointing to successor **task/activity 593**.
- Successor task **593** loaded its own contract and expected token **3**. Entering a 55-character comment (two leading/trailing spaces around `F012 partner rejection: 12500.00 / 9007199254740993`) enabled REJECT without reload. Command `e4762e92-f69b-4fc8-a64d-4581fda56d1e` completed the instance at REJECTED, token **5**, watermark **13**. Recovery showed that result and separate completed current state. UI character count and displayed digits were observed; byte-exact comment transport remains isolated-test evidence, not a browser network capture.
- Selecting the first buffered command again restored its original task **586**, APPROVE, null comment and expected token **1**, with the original ACTIVE/PARTNER receipt after the successor had already completed. Copy envelope was invoked. Clipboard contents, download and subsequent restore/retry were **not verified**.

Computer Use stopped when F5 was attempted: **“could not determine the current browser URL on Windows with enough confidence to enforce policy.”** Browser interaction ended at that point; no browser error or rendering regression was established. The reload action's outcome is unknown. This blocker prevents completing AC02–07/08 browser acceptance and leaves **T06 open**.

Remaining required browser observations: full reload/restore/retry and download fidelity; exact request/start/variable JSON and null/omission checks; optional-stage skipping and post-completion graph/history links; post-change administrator catalog/definition editing, validation/conflict/unsaved-input checks; failed/delayed/denied reads and unknown-route behavior; event/inbox/audit/capability screens; narrow-screen controls and logout clearing. Baseline administrator navigation/graph observations are not substituted for these post-change checks. Isolated tests retain coverage of existing exact-value, recovery and failure behavior but do not close the browser gaps. F011 historical evidence and its prior acceptance gap remain unchanged. APOLO library consumption, NgModule/security/theme/localization integration and ASM-01 remain outside scope and unverified.

### Cleanup, scope and final review

After browser control stopped, PID **27644** was checked through its Java command identity, signalled only through the harness's own stop file, and exited successfully. The log recorded `F012_BROWSER_FIXTURES_CLEANED` and completed graceful shutdown. A separate guarded, read-only SQL check confirmed `DB_NAME() = apolo_workflow_test`, zero F012 browser definition/catalog/request rows, zero baseline/post-run instances **596–609**, and zero held Liquibase locks. Baseline PID **28016** had already exited. No temporary server remains running and no unrelated process was stopped. The verification Chrome window may remain open; browser control ended before logout/window closure could be observed. Temporary harnesses, credentials, metadata and logs stay ignored under `.local/` or `target/`.

Final diff review covers dependency/lockfile changes, bootstrap, fourteen destruction guards and the added lifecycle regression. Active README, architecture/status guidance, feature index and roadmap reflect Angular 19 alignment while retaining pending extraction and open browser acceptance. No Java/backend/POM/schema, F011 feature record/fixture or APOLO source changed; there are still exactly two Maven modules. Final static checks: **PASS**, 240 local link targets in thirteen changed Markdown files; manifest/lock direct pins agree and all eleven Angular package entries are 19.x; **PASS**, `git diff --check`. A read-only process/port check confirmed both owned Java PIDs absent and no listener on 18112. No changes are staged, committed or pushed.

## Specification preparation (historical)

## 1. Implementation summary

2026-11-07: [revision 1](spec.md) prepared; T01 only. No feature implementation exists. Starting repository revision: 93d63f4, with a clean working tree before this documentation task. The console remains Angular 21; no install, dependency edit, source change, build, test, server startup or database access was performed.

## 2. Components and observations

Prepared spec.md, plan.md, tasks.md and this handover; added feature-index and roadmap links. No F011 file was edited.

Read-only inspection found:

- package.json: Angular framework/compiler 21.2.25, Material/CDK 21.2.14, CLI/build 21.2.29, TypeScript 5.9.3, RxJS 7.8.2, Node 22.22.0 and npm 10.9.4.
- src/main.ts uses bootstrapApplication with hash routing; no Zone.js dependency or explicit change-detection provider is present in the inspected manifest/bootstrap. The current application depends on its Angular 21 runtime behavior.
- angular.json uses the application builder, /console/ base, dist/console output and current budgets. check-build.mjs validates that generated styles work without inline script handlers.
- The Maven console-ui profile installs the same Node/npm versions, runs npm ci/build and copies dist/console/browser into static/console.
- unit/component-loader.mjs imports actual Angular modules and transpiles TypeScript with HTTP/navigation/DOM doubles. Its isolated results do not establish browser bootstrap/rendering.
- F011's handover retains an interactive-browser gap; this task neither repeats nor closes that acceptance work.

## 3. Decisions and deviations

The specification selects Angular 19.2, TypeScript 5.8.3, retained Node/npm/RxJS and conventional Zone.js-backed sandbox bootstrap. Exact 19.2 and Zone.js patch pins await T02 metadata resolution. No resolved install or runtime compatibility is claimed. No deviation from the agreed architecture is introduced; extraction and APOLO consumption remain separate work.

Official references inspected on 2026-11-07: [Angular version matrix](https://angular.dev/reference/versions) and [Angular 19 zoneless guide](https://v19.angular.dev/guide/experimental/zoneless). These support the compiler-range and experimental-API distinctions in R01/R02; they do not prove the application's compatibility. Angular 19 is listed outside current support; the owner-selected target is retained without claiming production support.

## 4. Verification

Documentation preparation: PASS — inspected project governance, feature workflow, relevant frontend configuration/source and official references; reviewed requirement/task/acceptance traceability. Local Markdown links, four-document inventory and whitespace checked before handover.

| Check | Actual result |
| --- | --- |
| Dependency installation and resolved version/peer inspection (AC01) | NOT RUN — documentation only; exact patch set remains T02 work. |
| Frontend isolated tests and discovery (AC09) | NOT RUN — no implementation. |
| Frontend production build/CSP and Maven packaging (AC08) | NOT RUN — no implementation. |
| Pre/post alignment browser scenarios (AC02–08) | NOT RUN — current task grants no runtime execution. Historical F011 browser gap retained. |
| Scope/inventory review (AC10 preparation portion) | PASS — documentation only; no F011/source/dependency/build files changed. Full implementation acceptance remains pending. |
| APOLO library/runtime integration | NOT RUN — outside F012; ASM-01 remains open. |

No process was started or stopped. No database was accessed. The preparation handover was uncommitted; the subsequent owner request authorizes committing the documentation after review corrections.

Pre-commit review corrections — 2026-11-07: AC04 now separates exact request/start/variable data from task completion IDs/versions, outcomes and comments; it does not imply scalar or structured task submissions. T03 completes dependency/bootstrap configuration without requiring browser boot before T04 compatibility adaptations. T05/T06 retain compilation and browser acceptance checks after those adaptations. No implementation task was performed.

## 5. Remaining work

Await an implementation request, then execute T02–T06 in order. Record the exact selected package matrix, baseline observations, actual changes and verification here. Keep blocked browser acceptance distinct from successful compilation and isolated tests. Do not add workflow-ui/workflow-web or amend prior feature evidence as part of alignment.
