# Plan — F012 Angular 19 frontend alignment

Targets [specification revision 1](spec.md). This plan authorizes no implementation. Follow [development workflow](../../specification-driven-development.md), [coding standards](../../coding-standards.md) and [package boundaries](../../package-structure.md).

## P01 — Establish the comparison baseline

R03/R06; AC03–07/10. Record the implementation-start revision, working-tree state, installed tool versions and discovered frontend suites. Review F006/F009–F011 handovers without altering them. Capture a focused browser baseline for login/navigation, exact JSON input, graph inspection, sequential completion and frozen command recovery where the authorized environment permits it. Record failures/blockers before editing dependencies; do not treat existing gaps as successful observations or demand unrelated historical acceptance work before alignment.

## P02 — Resolve and align dependencies

R01/R02/R05; AC01/02/08/09. Inspect published package versions, engines and peer dependencies, including Angular framework/compiler/compiler-cli, CLI/build, Material/CDK, Zone.js and test-loader imports. Choose mutually compatible exact 19.2 patches, keep Material/CDK paired, and record the complete before/after matrix in implementation.md before dependency edits. Framework and tooling patches need not have identical numbers if their published contracts permit the combination.

Change package.json and regenerate package-lock.json using the selected npm; verify a subsequent npm ci. Retain Node/npm and RxJS under R01; select TypeScript 5.8.3. Audit angular.json/tsconfig.json against the selected builder schema. Keep strict compilation, output paths, budgets and CSP settings. Do not run an assumed automated downgrade or accept unresolved peer conflicts.

## P03 — Adapt the existing console in place

R02–05; AC02–07. Add compatible Zone.js loading and explicit application-level zone configuration in src/main.ts/angular.json as appropriate, once only. Inspect required Angular 19 Material animation providers and configure only those actually needed by existing controls. Keep these bootstrap responsibilities in the application, not reusable component helpers.

Audit imports, standalone declarations, signals/inputs/outputs, template control flow, forms and async rendering. Check src/styles.scss and component styles against Material 19 without redesigning screens. Inspect unit/component-loader.mjs: it uses real Angular modules with mocked HTTP/navigation/DOM and does not bootstrap the application. Adapt framework-specific harness details without weakening behavior assertions or representing it as browser rendering evidence.

Affected files are bounded to workflow-test-host/frontend/package.json, package-lock.json, angular.json, tsconfig.json, src/main.ts, src/styles.scss, existing src/app/{shell,data,ui,pages} sources/templates/styles and existing unit/component tests where compatibility requires it. Existing app.routes.ts remains the route contract. scripts/check-build.mjs remains the CSP guard. workflow-test-host/pom.xml is inspected for packaging consistency; its pinned Node/npm and copy path are expected to remain unchanged. No Java package, core source, controller, schema or new Maven module is planned.

## P04 — Verify the aligned artifact

R01–06; AC01–10. Run only after implementation is requested. Record actual commands, revision, tools, suite discovery, counts and outcomes in implementation.md.

1. In the frontend directory, verify node --version and npm --version, then npm ci, npm ls for the Angular/tooling/Material/Zone.js dependency set, npm run test:unit and npm run build. The existing tests use Node's test runner/type stripping; inspect test discovery after any script adaptation. Add focused behavior tests only for compatibility changes that existing tests do not cover.
2. With a verified JDK 21, run the root wrapper with the console-ui profile and no Java test execution: `./mvnw.cmd -Pconsole-ui '-Dmaven.test.skip=true' package`. Inspect the host JAR for static/console/index.html and referenced bundles/styles. Packaging must not start a host or connect to a database.
3. Observe AC02–07 in a browser and AC08 using packaged assets under the unchanged host CSP. Exercise delayed/failed reads, dialogs/selects, keyboard focus, responsive layout and logout as well as success paths. Use F011's existing two-stage and optional-stage fixtures and existing exact-value/recovery scenarios; record fixture/version and task/instance identity without storing credentials or private runtime data in Git.
4. Use isolated mocks for database-free frontend checks. Any live database-backed browser run must use the guarded apolo_workflow_test setup where supported, or obtain task-specific authorization for the intended alternative before startup. Do not redirect the normal host or assume its default apolo_workflow_dev target is authorized. Existing F011 authorization is not blanket permission for a new development-database run. If no authorized browser environment is available, record NOT RUN/BLOCKED and keep UI acceptance open.
5. Track each temporary server's port, PID and command identity; stop only owned processes after verification. Record remaining gaps separately from passed builds. Review the final diff, changed lockfile, local documentation links and git diff --check.

## P05 — Handover and boundaries

Update tasks/implementation with AC coverage and unresolved failures. Update active README guidance to the actual frontend baseline only after it changes, preserving historical statements in prior feature records. Reconcile any architecture status text that still describes the current console as Angular 21; the web/UI extraction remains pending.

CON-01/02 and ASM-01 require honest compatibility evidence; this slice demonstrates the sandbox only. CON-03/10 keep host authorization and bootstrap ownership. CON-08/09/14 preserve pinned definitions, exact input semantics and JSON. CON-12 requires separate build, isolated-test and browser outcomes. No constitutional amendment, database migration, source move or closure of APOLO integration assumptions is planned.
