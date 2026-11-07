# Plan — F014 definition and catalog administration

Targets [spec revision 3](spec.md); implementation authorized after completed T02. F013 baseline is committed at `9a82eea`. Follow the [package conventions](../../package-structure.md) and focused verification rules referenced by R06.

## P01 — Reconcile after F013

R01/R06; AC06/07. Record completed F013 revision, package/public API conventions, relevant acceptance and actual source state before implementation. Reuse F012 `943a51f` administration/dialog evidence and F013 results where applicable; no new broad baseline walkthrough. Verify source inventory against the finished baseline: ConsoleTaskTypeController, ConsoleDefinitionController, ConsoleManagement, management audit/context, catalog and definitions pages, definition topology/fragment helpers and reload dialog.

Keep this feature's work within the existing web/UI libraries and sandbox adapters. Do not update roadmap/index, shared configuration or F013 records during this preparation. During implementation handover, update active navigation/current capabilities only as needed.

## P02 — Web extraction with host-owned writes

R01–04; AC01–03. Under `io.github.leontecristian.workflow.web`, use exact layer-first packages:

| Package | Responsibility |
| --- | --- |
| `configuration` | Explicit administration activation alongside, independent of, the completed viewer configuration. |
| `controller.tasktype` / `controller.definition` | Existing catalog/definition HTTP mappings, parsing and response translation. |
| `integration.administration` | One required `WorkflowAdministrationHost` interface for the real host access/mutation boundary. |
| `service.administration` | Scoped management wire/error helpers, reusing the existing viewer codec primitives. |
| `exception.administration` | Only administration-specific errors/mapping not already covered by the scoped F013 facilities. |

Use public core services directly for reads after the host access check. The host interface supplies `requireAdministrationAccess()` and typed mutation operations corresponding to catalog create/update and definition import/replace/metadata/validate/publish/retire. Use existing core command/result types: TaskTypeChange, DefinitionChange, DefinitionMetadataChange and DefinitionCheckResult. No Object-returning generic command dispatcher. Split validate/publish/retire methods preserve their return contracts. Trusted context and audit stay behind the adapter; no actor/time supplied by the client. Correlation uses the completed F013 trusted request-context convention, adapted to the sandbox's existing audit correlation requirements.

Sandbox adapter goes in `io.github.leontecristian.workflow.testhost.service.administration`, activation in `io.github.leontecristian.workflow.testhost.configuration.console`. It checks the current administrator policy and calls the existing ConsoleManagement proxy. Give that existing service precise return types if needed; do not relocate its transaction/audit implementation. No separate shared mutation service or new audit abstraction is needed. Translate host-specific errors at the adapter boundary to shared/core error contracts without exposing sandbox dependencies.

Reuse scoped codec primitives where compatible, but keep raw document input as bounded UTF-8 text and preserve management diagnostics/403 semantics. Ensure existing viewer advice and management mappings coexist. Keep host security/CSRF and console-enabled activation; no shared security chain/global mapper. Remove only the extracted controller mappings after wiring the adapter; retain audit endpoints/storage and all runtime commands in the host.

## P03 — UI extraction and sandbox wrappers

R01/R05; AC04–06. Extend the actual F013 UI workspace/package; do not create another library, package-install strategy or application. Keep root administration transport contracts/models and shared pure helpers under `projects/workflow-ui/src/lib/administration`. Place the four components and their private graph/dialog/state helpers under `projects/workflow-ui/administration/src/lib/{tasktype,definition,support}`, exported by the concrete `@workflow-engine/ui/administration` secondary entry. Root API/tokens remain in the existing public entry point; the secondary entry preserves the established production bundle budget, as recorded below. No speculative designer entry point.

Export the four administration components (catalog, definition list, family and version editor) and typed host API contract. Existing graph, fragment editor and reload dialog may remain implementation details unless a real consumer requires them. The typed administration API mirrors R03 operations and returns existing DTO shapes; it includes writes and raw text export. Reuse F013 navigation/messages/session conventions, extending intent types only for actual family/version destinations. Keep admin denial as a distinguishable 403 state, stale/conflict as 409 feedback and validation failures as returned reports. Do not force mutation errors through a read-only failure contract that loses these distinctions.

The sandbox implements transport using existing authenticated/CSRF behavior and wraps current routes. Keep dirty state in the extracted import/version editor for its existing feedback, action availability and reload confirmation; current routes have no general route-leave guard, so no new guard/output framework is required. Supply the current sample catalog code from the sandbox wrapper. Ensure response generation guards apply to reads and writes: aborting/unmounting a UI does not cancel a committed server mutation and must not trigger an automatic retry.

Move existing UI code with necessary host-seam changes, not a redesign. Reuse F012 dialog code and exact JSON helpers; retain saved-document graph inspection and explicit fragment application. Extend the existing F013 consumer fixture with one administration mount and a small API double. No new runtime capability UI or production auth simulation.

## P04 — Verification and stopping condition

R06; AC01–07. Record outcomes in implementation.md; none of these checks runs during document preparation.

1. **Focused isolated checks:** adapt existing catalog/definition/topology/fragment/dialog tests and add only missing extraction-boundary coverage. Verify explicit activation, one host permission check, raw text/exact headers/DTO errors, mutation delegation, dirty-state integration and delayed-response disposal. Keep original assertions; do not run unrelated runtime receipt/replay suites or duplicate F002/F004 validation matrices.
2. **Focused host integration:** reuse the guarded `apolo_workflow_test` harness for one representative changed administration write through the extracted route and its audit. Cover route/access/CSRF/error forwarding in focused controller/security tests; do not repeat those cases against the database when the isolated check establishes the changed boundary. Existing transaction/audit behavior is reused if ConsoleManagement is unchanged apart from typing/delegation. A new rollback test is justified only by a real change to that transaction boundary; do not move it merely to justify extra tests. Fixture creation uses existing commands without testing runtime idempotency. No development-database access/reset or normal-host redirection.
3. **Build/package:** use the completed F013 build/consumption commands and established Node/npm/JDK baseline. Verify new exports and sandbox compilation/budgets/CSP, then the required opt-in packaged assets. Count a frontend build performed by Maven as the same check; avoid duplicate builds. No unrelated dependency upgrades.
4. **Browser:** one short packaged sandbox pass: open catalog and family/version views, edit/save one draft, inspect its saved graph and export, and check one dirty reload cancel/discard interaction. Reuse F012 evidence for unchanged catalog CRUD, validation/publication/retirement, fragment editing and detailed Escape/backdrop/focus behavior; focused automated checks cover their new adapter bindings, stale feedback and lifecycle disposal. Add a browser case only if extraction changes that interaction or exposes a concrete gap, recording why. The existing generic consumer fixture checks one administration component's host transport/navigation/messages and rendering, not the entire workflow again. No broad console tour, task completion, command recovery or idempotency tests.
5. **Handover:** label previous observations REUSED with source/revision and applicability. Check diff/links/whitespace, preserve unrelated changes, and stop only owned processes/clean test-owned fixtures. A blocked required new-boundary observation remains open, without reopening old acceptance. Stop after the focused checks pass; rerun only checks affected by a subsequent change/failure.

Evidence sources: F012 final acceptance and Material reload-dialog follow-up at `943a51f`; F006 management transport/audit observations; completed F013 library-consumption/host integration. Record the exact applicable section/revision and its limits when claiming REUSED; a source listing is not itself evidence of every operation. Each relevant result needs one suitable verification method, not repetition at every layer. No source observation, unit test or sandbox run establishes APOLO integration.

## T02 established implementation contracts

Reuse the three-module reactor and Angular 19.2 partial-compiled package. Administration uses plain explicitly imported `WorkflowAdministrationConfiguration`, required `WorkflowAdministrationOptions` (base path/trusted correlation attribute), and `WorkflowAdministrationHost` with one access check and typed writes. Preserve viewer-only activation and existing host correlation attribute. Scoped administration wire/error helpers may reuse the viewer codec without imposing viewer error categories or exact-event limits.

UI adds `WORKFLOW_ADMINISTRATION_API`, `WorkflowAdministrationApi`, typed administration DTOs and four standalone components under the secondary entry source root documented in P03. Reuse opaque nonnull `sessionScope`, AbortSignal transport and simple messages inputs. Extend the existing navigation destination union only with definition/version destinations; no second navigation framework. Sandbox wrappers supply route IDs, session generation and SANDBOX_REVIEW. Shared UI/support files are owned by the UI worker; lead serializes public entry-point/configuration changes on handoff.

Lead owns feature documents, shared configuration/manifests/lockfiles, integration fixtures and final verification. Backend worker owns administration Java extraction and focused isolated tests; UI worker owns administration components, sandbox wrappers/adapters and focused UI tests. Workers do not build or access databases without a central verification handoff. No overlapping writers.
Build-driven alignment: keep root typed administration API/tokens/models and shared helper exports, but publish the four administration components through `@workflow-engine/ui/administration`. Lazy-load the four existing sandbox wrappers through Angular Router. This concrete secondary entry avoids eagerly loading all extracted pages from the primary FESM and preserves the existing 1 MB hard initial bundle budget; no new package, module or speculative designer entry is added. Existing dialog remains lazy. Final checks compile both consumers against the built package and exercise administration navigation in the browser.

## Follow-up plan — Newest definitions first, 2026-11-07

Keep the sandbox preference in ConsoleAdministrationApi: request order=DESC on every definition-family page, without changing shared component inputs or the start chooser. Extend core dto.definition.DefinitionQuery with workflow-owned DefinitionOrder and compatible ascending constructors; repository.definition supplies the strict descending ID cursor, service.definition keeps existing bounded page/continuation projection, and web controller.definition validates the optional order. No migration or execution path changes.

Fresh verification is limited to the two query-service unit cases, existing administration MVC parsing/delegation, host adapter and existing definition-page tests, one guarded DefinitionOrderingIT for real descending SQL cursor boundaries and owned fixture cleanup, and the sandbox production build. Reuse F014/F015 evidence for unchanged rendering, authorization and command behavior. No development host/database startup, broad engine suites or repeated frontend/library build.
