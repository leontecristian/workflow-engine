# F013 — Shared instance viewer extraction

**Status:** IMPLEMENTED, revision 5; runtime acceptance COMPLETE — 2026-11-07. **Owner:** Workflow project owner. **Constitution:** 6.0.0. **Authorization:** owner implementation request authorizes T03�T07; no commit, push or APOLO changes. The readiness-only descriptions below are historical. T07 focused runtime acceptance is complete; see implementation.md.

The owner moved these four documents from local staging to `docs/features/F013-shared-instance-viewer/`. F012 final acceptance and its dialog follow-up are complete at `943a51f`. T02 baseline/contract reconciliation is complete; reuse that evidence for unchanged behavior. This readiness decision authorizes no implementation.

Implementation request received 2026-11-07: T03�T07 are authorized. Earlier authorization/readiness wording records the specification-preparation scope. Required behavior is unchanged. See implementation.md for current verification.

## Purpose and authority

Extract one usable read-only slice into the adopted `workflow-web` and `workflow-ui` libraries, and make the sandbox consume it. A host can embed instance metadata, ordered history and the pinned graph without importing sandbox identity, routes or business policy.

Authority: [constitution](../../constitution.md) C01/C03–05/C08–10; [constraints](../../constraints.md) CON-01–04/08–12/14; [assumptions](../../assumptions.md) ASM-01/02/03/08/09/11; [design precedence](../../design-sources.md); [integration architecture](../../architecture/workflow-ui-web-integration.md). Preserve the behavior delivered through F006/F009/F010/F011 and the [F012 contract](../F012-angular-19-frontend-alignment/spec.md); F012 acceptance must be completed before extraction.

## Scope

Included: a third Maven module (`workflow-web`), one Angular library (`workflow-ui`), explicitly activated read endpoints, host access adapters, reusable viewer/history/event/graph components and sandbox composition. Extract existing implementation and focused dependencies; retain one authoritative implementation of the extracted slice.

Excluded: instance search/list extraction, task/activity detail APIs or screens, variables, submissions, service effects, catalog/definition administration, commands/replay, business forms, designer, new engine behavior, migrations, source moves into APOLO and actual APOLO application changes. Existing sandbox links may navigate to these host-owned screens. Graph links do not authorize their destinations.

The sandbox instance wrapper retains variables and sandbox service effects alongside the shared viewer. Existing instance-list, task and activity pages remain host-owned. No duplicate production service, login, bootstrap, iframe or microfrontend is introduced.

## Requirements

### R01 — Library and host boundaries

`workflow-web` is an ordinary Java 17 library JAR depending on core; the test host depends directly on both. Core retains its public services/DTOs, configuration marker and migrations, and acquires no web/UI dependency. The new module supplies no executable application, datasource, transaction manager, security chain, migration runner, global ObjectMapper or global exception advice.

`workflow-ui` is an Angular 19.2-compatible package consumed by the existing sandbox application and usable from an NgModule host. Angular/Material/CDK are peer dependencies; no second Angular runtime is bundled. Bootstrap, Zone.js loading, animation providers, authentication, HTTP interceptors, routes, theme and translation initialization remain host-owned. No APOLO or sandbox implementation import is permitted in either library.

### R02 — Explicit read-only activation

The host explicitly imports viewer web configuration and supplies the required access adapter and a nonempty API base path. Dependency inclusion or component scanning alone must not expose viewer endpoints. No auto-configuration is introduced. Incomplete explicit activation fails startup with a useful configuration error; it never installs permissive defaults.

Exactly these operations are extracted, relative to the host-supplied prefix:

| Method/path | Input and result |
| --- | --- |
| `GET /instances/{id}` | Existing instance metadata projection. |
| `GET /instances/{id}/history` | Existing ordered summary page; optional `afterSequence` and `limit`. |
| `GET /instances/{id}/history/{sequence}` | Exact event for that instance and sequence, including `detailsJson`. |
| `GET /instances/{id}/graph` | Existing authorized graph projection and its captured instance metadata. |

Sandbox prefix remains `/console/api`; existing URLs and payload field names remain compatible. No generic command endpoint is activated by this configuration. Existing host mutation endpoints and CSRF protections remain unchanged.

IDs and history sequences are canonical positive decimal strings within signed Java long range; the history cursor permits zero and defaults to zero. Page size defaults to 50 and accepts 1–100. History and graph reject unknown/repeated query parameters as today. Do not introduce new query behavior on the two exact-detail routes as incidental cleanup.

### R03 — Trusted access and historical visibility

Every operation requires host-authenticated context and explicit authorization before returning protected data. Identity must not come from request payloads, query parameters or instance business correlation. Keep only two viewer access checks: INSTANCE permits metadata and its graph; HISTORY additionally permits complete history summaries and exact details. All four endpoints require INSTANCE, and both history endpoints also require HISTORY. There is no separate GRAPH permission or new role/permission administration system; hosts map these checks to their existing policy.

HISTORY is explicitly a full-evidence grant: it may disclose actors, comments and retained business snapshots. A host unable to grant that scope must deny history. Summary-only access, per-event permissions and redaction/filtering are deferred, not implemented by dropping rows after pagination.

Instance viewing grants topology and progress, not automatic permission for task/activity destinations. A bounded host operation reuses existing destination access checks to supply the readable subset of candidate activity/task references; absent permissions produce null link IDs. Do not infer task permission from an unrelated activity grant. These are existing host resource checks, not additional viewer permission settings. The host independently authorizes navigation destinations.

Graph construction may internally read history summaries under INSTANCE to produce the R05 projection; it does not require HISTORY or return raw history/details. HISTORY remains necessary for both public history endpoints. This permits a useful graph when full evidence is denied without bypassing that denial. Missing link grants hide links; a failed access check must not be treated as an allow result.

The sandbox adapter preserves current administrator and assigned-reviewer behavior, including access retained through completed assignments. No sandbox role names, assignment-search algorithm or permissive administrator default enter shared policy. Authorization is repeated on every request, including later history pages and exact-event downloads. There is no persistent shared cache of private responses.

Unauthenticated requests fail with 401; denied/missing resources use indistinguishable 404 responses within the viewer contract. Host security may reject requests before the controller; it must not redirect API requests to a successful HTML login response. Broader host policy remains outside the library.

### R04 — Exact wire data and bounded history

Preserve the existing `WorkflowInstanceView` and `WorkflowHistorySummary` field names and nullability. Reuse these detached core DTOs with the scoped web codec where their shapes already match; add transport DTOs only when an actual mapping requires them. Preserve `WorkflowHistoryEvent`'s `{summary, detailsJson}` shape and the history `{items, nextAfterSequence}` envelope. All Java long IDs, versions and sequences serialize as strings; bounded integer definition/schema versions retain their current numeric encoding. Instants remain ISO strings, UUIDs strings, and nullable fields explicit null where currently returned.

`detailsJson` is the exact stored string. Display/download must retain number spelling and scale, including `9007199254740993` and `12500.00`, and null/omission distinctions. Pretty or derived routing views must not replace the source evidence. No lossy JSON parse/stringify round trip is allowed for exported evidence. Unknown event types remain readable through summary and raw details; they are not treated as corrupt solely because no specialized rendering exists.

History is ascending and exclusive of `afterSequence`; null continuation means exhausted at that read. Preserve byte-aware paging: complete page envelopes fit the existing 1 MiB ceilings in both UTF-8 and UTF-16 representations, include whole rows and a continuation for the last returned row, and fail explicitly if one row cannot fit. Ordinary history pagination is a live read, not a newly promised cross-request snapshot. Later committed events may appear on later pages; refresh restarts at zero. Exact-event reads return the whole stored event or an explicit failure, never truncated evidence. Do not impose a new smaller cap on F009's accepted exact-event evidence during extraction.

For exact events, [F009 D15](../F009-json-execution-variables/spec.md) retains a maximum of 4 MiB for stored details in each of UTF-16 and UTF-8, and 8 MiB UTF-8 for the complete serialized history read response, including escaped `detailsJson` and its envelope. Verify the final web serialization against that response bound, including worst-case escaping/Unicode. The smaller 1 MiB summary-page behavior above is preserved separately; it is not an exact-event cap. A limit failure returns 422 LIMIT_EXCEEDED, never partial evidence.

### R05 — Pinned graph and safe failure

Preserve the current graph projection: `instance`, nodes (`key`, `name`, `type`, `state`, nullable `activityId`/`taskId`) and edges (`key`, `from`, `to`, nullable `outcome`, `traversed`). States remain ACTIVE, COMPLETED and PENDING. Raw workflow documents, conditions, service arguments and submissions are not graph payloads.

Authorize before accessing retained topology/evidence. Verify the pinned definition ID/key/version/hash. Capture the instance's initial history watermark and reconstruct only the complete contiguous prefix through it, even if a command commits while paging. Preserve 12,000 history-event, 200-node, 500-edge and 1 MiB graph bounds, and existing supported-state/consistency checks. Do not evaluate expressions, infer traversed edges from layout or add support for new runtime shapes.

Invalid/missing operational evidence produces an explicit unavailable graph, not guessed progress. A graph construction, size or transport failure leaves independently authorized metadata/history usable. An authentication or instance-access denial is different: clear the composite instance viewer, including previously loaded metadata/graph, and invalidate its pending reads. Successful graph data supplies the metadata displayed with that graph; never label separately fetched newer metadata as its snapshot. Preserve F011 successive/skipped-stage highlighting and historical link identities.

### R06 — Reusable presentation and lifecycle

Provide embeddable instance-viewer, history-list, exact-history-event and graph components. Inputs identify the exact instance/event; no root route registration is required. Host-supplied navigation resolves history/task/activity links; unsupported host destinations render noninteractive content. Keep current graph layout, initial 50% zoom and 25–200% zoom bounds, accessible labels and keyboard controls. Multiple viewers, including two for the same instance, must not share SVG marker IDs or mutable state.

The instance viewer loads metadata and graph only; history list and event components load only when mounted by their host. The graph component renders supplied data without its own requests. Do not prefetch history to discover permissions. Hosts may omit a history destination, while an attempted history read is still authorized by the backend. Render labels and retained evidence as text, never as trusted HTML or executable content.

All data access goes through a host-provided typed read adapter. It exposes only the four selected operations, with cancellation support. The sandbox implements it with its existing authenticated transport. A host may use Angular HttpClient/interceptors without the library bypassing them with direct fetch or registering its own root HTTP providers.

The adapter normalizes failures into CANCELLED, UNAUTHENTICATED, NOT_FOUND (including concealed denial), LIMIT_EXCEEDED or FAILED, with an optional safe server code/correlation ID. Cancellation is silent. Unauthenticated or missing/denied reads clear affected protected content; limit and other failures show an explicit read error and allow manual refresh. Malformed responses and network failures are FAILED reads. Do not show command-recovery instructions or automatically retry. Session invalidation clears all viewer content as specified below.

Preserve loading, empty, missing/denied, failure and unavailable states, refresh, history page navigation, routing-evidence presentation and exact event downloads. The UI must not distinguish concealed denial from a missing resource. On input changes or host session invalidation, clear protected content and invalidate pending reads; stale responses must not repopulate a new instance, event or logged-out view. Session invalidation includes re-authentication as the same user, not only a changed user ID. For overlapping reads of the same resource, only the latest request may update its state. Cancellation support does not replace stale-response guards. Destroying the component releases subscriptions and outstanding work. No polling, command resubmission or duplicate requests are introduced to compensate for rendering problems.

All library-authored visible labels and accessibility messages use a reactive messages input with English defaults. Hosts replace that input when locale changes and own translation loading; the library adds no translation service or locale notification framework. Use component-scoped styles and the host's Material theme; avoid global resets, root theme/provider replacement and sandbox-only fonts/assets. Preserve narrow-screen and keyboard usability without redesigning the console.

### R07 — Error and isolation contract

Viewer errors retain the envelope `{code, message, diagnostics, correlationId}`. Selected mappings are 400 INVALID_INPUT, 401 UNAUTHENTICATED, 404 NOT_FOUND, 422 LIMIT_EXCEEDED, 500 HOST_CONFIGURATION for inconsistent graph evidence/configuration and 500 HOST_FAILURE for unexpected runtime failure. Messages contain safe category text; diagnostics are empty for this slice, and correlation is a host-supplied string or null, never a private exception dump. Startup configuration failures are startup errors, not successful HTTP responses.

Scope advice and serialization to viewer controllers. A host controller throwing an unrelated error must retain its own handling. Preserve the sandbox security headers, CSP, session handling and command CSRF behavior. GETs do not mutate workflow/business state, create receipts/events or advance instance versions. No schema or migration changes are needed.

Owner correction — 2026-11-07: handled 500 responses must log the original exception with its stack trace/cause chain, error code and trusted correlation ID in server logs. The sandbox error handler follows the same rule. Client envelopes stay unchanged; expected 4xx responses do not gain error-level exception logging.

### R08 — Baseline, packaging and evidence

Implementation starts only after a separate request. F012 revision `943a51f` and its completed acceptance are reconciled under T02; at implementation start inspect HEAD/status for relevant changes since that baseline rather than repeating acceptance. Record the actual starting revision and any relevant drift. Reuse the recorded dependency matrix and selected runtime/toolchain; resolve additional library-build tooling from published metadata before dependency edits, without force/legacy-peer overrides or unrelated upgrades.

Preserve sandbox `/console/`, hash routes, opt-in Maven frontend packaging, budgets and CSP checks. Verify installation from the built UI package without TypeScript paths into library sources. Record isolated tests, package builds, Maven packaging, sandbox browser checks and NgModule-consumer checks separately. Actual APOLO compatibility and production acceptance remain open under ASM-01/02/03/09; this feature does not change APOLO sources or claim first booking integration.

Verification is limited to the extracted viewer and changed host integration. Reuse matched F012 evidence for unchanged behavior and mark it REUSED with its source and applicability. Do not repeat broad console browser acceptance, command recovery or idempotency/replay tests for this slice. Preserve their existing contracts and test sources without selecting those suites as F013 gates. Confirm excluded code remains unchanged through diff review. Fresh checks must establish new library/adapter/package behavior; previous application evidence alone cannot establish extraction success. Repeat checks only when a relevant change, failure or identified gap invalidates existing evidence, as detailed in P04.

## Acceptance scenarios

| ID | Required observation |
| --- | --- |
| AC01 | Reactor has core/web/test-host only; core builds without web; web is an ordinary JAR. UI package has no host imports or bundled second Angular runtime. |
| AC02 | Web dependency alone exposes none of the four routes. Explicit activation with complete adapters exposes only them; missing adapter/path fails startup. Existing sandbox mutations remain host-owned. |
| AC03 | Anonymous, unrelated reviewer and authorized current/historical reviewer/admin cases enforce R03 on each read/page/event. Denied INSTANCE blocks graph and history too; allowed INSTANCE with denied HISTORY permits graph construction from internal summaries but denies both public history endpoints. No raw evidence leaks through the graph. Link IDs obey existing host task/activity checks; disabled navigation does not imply backend authorization. |
| AC04 | Existing sandbox paths and payloads match the baseline; large IDs/versions, exact JSON number spelling, nulls and unknown event types survive rendering and event download. Maximum-size exact evidence and worst-case escaping satisfy F009's stored-details/8 MiB response bounds, independently of summary-page limits; oversized responses fail explicitly. |
| AC05 | History default/max/invalid limits, duplicate/unknown parameters, cursor ordering, empty/end pages and byte-driven continuation preserve complete rows without silent truncation or post-page authorization filtering. |
| AC06 | F011 two-stage and optional-stage fixtures retain pinned graph state/links. Concurrent progression does not contaminate the captured watermark. Missing/gapped evidence, hash mismatch and size limits produce explicit graph failure without losing separately authorized views. A graph access denial after successful metadata loading instead clears the composite viewer and prevents pending responses from restoring it. |
| AC07 | Sandbox uses shared viewer/history/graph with retained host-owned variable/effect panels and task/activity navigation. Verify composition and link destinations, and use diff review plus reused F012 evidence for unchanged excluded functionality. Completion/recovery code and F011 fixtures/evidence remain unchanged; no command walkthrough or idempotency test is required. |
| AC08 | Delayed/error reads, overlapping refreshes of the same resource, rapid input changes, same-user re-authentication, logout, destruction and two simultaneous viewers show no stale private data or cross-view SVG/state interference. No unmounted history requests occur. Cancellation is silent; other read failures use the defined categories without command-recovery wording or automatic retry. Untrusted labels/evidence render as text. Keyboard, narrow viewport, theme and reactive message overrides remain usable. |
| AC09 | A minimal NgModule consumer imports the built package, supplies reads/navigation/messages and host providers, and boots in a browser. There are no sandbox imports, hardcoded routes or second Angular runtime. This is not APOLO acceptance. |
| AC10 | Unit suites, UI library/package consumption, sandbox build/CSP/budgets and Maven console packaging pass with recorded discovery and versions; packaged assets serve under unchanged host CSP. |
| AC11 | Focused GET verification confirms no workflow-state mutation; it does not exercise command execution or receipt replay. Viewer advice/codec do not alter unrelated host controller behavior or host security/global configuration. |
| AC12 | Final scope review, links, whitespace, task/evidence reconciliation and owned-process cleanup pass. Required unperformed browser checks leave acceptance open. |

## Decision status and readiness

AGREED on review, 2026-11-07: graph cannot bypass instance access; history is one explicit full-evidence grant; all required F012 browser acceptance remains a prerequisite to F013 implementation. To keep permission handling simple, metadata and graph share INSTANCE rather than introducing a separate graph grant. HISTORY adds full-evidence access, and graph links reuse host destination checks. No permission-management UI or role catalog is selected.

Revision 2 records owner decisions and simplifications; revision 3 clarifies loading/failure behavior; revision 4 narrows verification to extraction, requires reuse of applicable F012 evidence and excludes idempotency/replay retesting. Revision 5 completes T02 against `943a51f` and the updated focused-delivery rules. No blocking scope, permission or behavioral ambiguity remains for this slice. READY is not an implementation request. Additional build-tool pins, exact member signatures consistent with these contracts and reproducible local package installation are bounded engineering choices in T03/T04, not new owner decision gates. Material behavior changes require spec/plan/tasks updates before dependent code.

OPEN beyond F013: APOLO's concrete access adapter, effective dependency matrix, route/theme/translation integration, production coordinates/distribution and source-move timing. No event-redaction policy, generic command orchestration or management API contract is selected here.
