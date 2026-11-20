# F035 implementation record

Revision 1 · Implemented 2026-11-20 in the workflow-engine worktree. See the [original scope](spec.md) and [consumer examples](../../../workflow-ui/README.md#optional-workflow-web-http-adapters).

All six providers bind the existing tokens through the host's Angular `HttpClient`. The sandbox uses the standard mappings for its shared pages and composes them with business start and business history. Its interceptor attaches the current server-named CSRF header and handles 401 responses against the captured session scope. The start chooser keeps its ascending family order. The HTTP adapter source lives inside the secondary entry point's `http/src` compilation root.

## Verification

Checks used Windows and the repository's Angular 19.2 dependencies. Final builds and affected verification used the repository-pinned Node 22.22.0 and npm 10.9.4. Earlier checks used Node 22.14.0 and npm 10.9.2.

- **Package PASS.** `npm --prefix workflow-ui run build` and `npm pack --dry-run --json` from `workflow-ui/dist/workflow-ui`. Inspected the generated `./http` export, typings and FESM file. An earlier build exposed ng-packagr's secondary-entry root directory constraint, which moving the sources under `http/src` resolved.
- **NgModule build and A1–A5 PASS.** Ran `npm.cmd run build` and `npm.cmd run test:http` from `workflow-ui/verification/ngmodule-host`, then ran the tests with the pinned runtime via `../../../workflow-test-host/target/frontend/node/node.exe --test http-adapter.test.mjs`. All five scenario tests passed using the packaged entry point, real Angular DI and HttpClient, a host interceptor and a recording Angular backend. They covered independent registration, all operation mappings, exact source JSON and tokens, concealed/malformed errors, abort teardown, frozen lookup and mutation envelopes, and the definitive 503 validation case. When offline npm installation lacked Zone.js in the cache, the fixture used matching installed workspace dependencies and a copy of the built UI package.
- **NgModule browser A1 PASS.** Ran `node workflow-test-host/target/f035-ngmodule-browser.mjs` from the repository root with headless Chrome and Playwright fulfilling requests from the built fixture assets. The administration-only route rendered the Definitions family and recorded one intercepted request. Its custom viewer resolved and no runtime command bindings were present. Loopback navigation was unavailable in the sandbox, so the check used request fulfillment. The browser and the temporary loopback server from the first attempt were closed.
- **Standalone sandbox build PASS.** Ran `node workflow-test-host/frontend/scripts/sync-viewer-library.mjs` from the repository root and `npm.cmd run build` in `workflow-test-host/frontend`. The first build compiled and exceeded the 1.15 MB initial error budget by 5.15 kB. After adjusting that limit to 1.16 MB, the build and its CSP stylesheet check passed. The final build used the pinned toolchain after removing an unused session scope read from logout. The 900 kB early warning remains active. Both consumer builds emitted bundle budget warnings, and the sandbox also emitted Sass deprecations.
- **Standalone sandbox browser PASS.** Ran `node workflow-test-host/target/f035-sandbox-browser.mjs` with headless Chrome and Playwright fulfilling requests from the sandbox production bundle and controlled API responses. The Definitions table rendered a family with its full 64-bit ID. The browser made exactly the session, capabilities and descending definitions requests and reported no page errors. The browser was closed.
- **Sandbox focused tests PASS.** From `workflow-test-host/frontend`, ran the following selected suites after syncing the package. They exercised actual host interceptor CSRF behavior, late prior-session 401 handling (A5), business start and history, diagnostics and command-state preservation. The first run exposed fixture issues with Angular JIT import order and a stale completion fixture field. Both were corrected. The runner still reports duplicate Angular JIT component IDs for the pagination/collection components imported from the existing UI entry points.

  ```powershell
  node --experimental-strip-types --test src/app/data/console-api.test.mjs src/app/data/runtime-command-adapters.test.mjs src/app/data/command-recovery.test.mjs src/app/pages/tasks/task-wrapper.test.mjs src/app/pages/sandbox/requests.test.mjs unit/scalar-inputs.test.mjs
  node --experimental-strip-types --test src/app/data/console-api.test.mjs src/app/data/runtime-command-adapters.test.mjs src/app/data/administration-transport.test.mjs src/app/data/draft-adapters.test.mjs src/app/data/viewer-adapters.test.mjs src/app/data/runtime-read-adapters.test.mjs unit/command-buffer.test.mjs
  ```

  Results were 32/32 and 38/38. The commands overlap on the console API and command suites.

**Independent review follow-up.** The first review found that incomplete JSON mutation receipts could satisfy the initial write checks. The adapters now require the summary fields used by drafts, families and task types, and reject incomplete cancellation records and unknown task statuses. A5 verifies that plausible but incomplete HTTP 200 replies preserve the known HTTP status while leaving the write outcome uncertain. Rebuilt and inspected the package, rebuilt both consumers and reran A1–A5 and the two focused browser commands above with the pinned toolchain. All passed. The affected sandbox suites passed 28/28 with:

```powershell
node --experimental-strip-types --test src/app/data/administration-transport.test.mjs src/app/data/draft-adapters.test.mjs src/app/data/console-api.test.mjs src/app/data/runtime-command-adapters.test.mjs unit/command-buffer.test.mjs
```

**Verification scope.** The checks used controlled responses. The live Java host and SQL Server were outside the selected verification. APOLO source and runtime were untouched. The earlier APOLO definitions-list observation established endpoint availability only. No temporary processes remain running.

The independent recheck confirmed the updated guards accept the existing Java response shapes and resolve the review finding. Final diff, new-file whitespace and relative-link checks passed. Changes are uncommitted.
