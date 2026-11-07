# Implementation — F012 Angular 19 frontend alignment

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
