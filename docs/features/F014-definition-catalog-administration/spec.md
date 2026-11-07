# F014 — Definition and catalog administration extraction

**Status:** IMPLEMENTED, revision 3 — 2026-11-07. **Owner:** Workflow project owner. **Constitution:** 6.0.0. **Authorization:** implement and verify F014 after T02 readiness; no commit or push.

T02 reconciled the completed F013 handover at `9a82eea`. Its shared APIs and focused acceptance are established; no active dependency edits or material readiness blockers remain.

## Purpose and scope

Move the existing task-type catalog and definition administration HTTP mapping/pages into the libraries established by [F013](../F013-shared-instance-viewer/spec.md), then consume them from the sandbox. Preserve delivered behavior; add no new authoring capabilities.

Authority: [constitution](../../constitution.md) C01/C03/C05/C07–10, [constraints](../../constraints.md) CON-01–04/08/10–12, [assumptions](../../assumptions.md) ASM-01/02/09, [architecture](../../architecture/workflow-ui-web-integration.md), [design precedence](../../design-sources.md), [F002 catalog](../F002-task-type-catalog/spec.md), [F004 definitions](../F004-definition-management-validation/spec.md) as extended through F007–F011, and [F006 console](../F006-test-host-definition-console/spec.md). Earlier two-module/host-only placement is superseded by the adopted library architecture; existing business contracts remain authoritative.

Included: catalog list/create/edit/selectability, definition-family list/metadata/version list, version lookup/import/read/export, draft JSON editing, validation/publication/retirement, existing definition graph/selected-fragment editor and Material reload confirmation. The graph is the existing authoring aid, not a new graphical designer.

Excluded: new workflow language or validation rules, capability administration, runtime lists/tasks/commands/recovery, business forms, audit browsing/storage extraction, permission management, migrations, new modules, APOLO changes and graphical designer expansion. Host login/navigation, capabilities, trusted actor/time, transaction infrastructure and management audit remain host-owned.

## Requirements

### R01 — Reuse the completed F013 libraries

Extend workflow-web and workflow-ui in place; keep one authoritative implementation of extracted controllers/pages. Reuse F013's build/package, scoped codec/error, UI navigation/messages/session and host integration conventions where applicable. Do not add a parallel framework or migrate unrelated code. Core remains independent of web/UI; use documented core services and detached DTOs, not repositories/entities. No new Maven module or application shell.

### R02 — One administration check and explicit activation

Expose one explicitly enabled administration group covering catalog and definitions. Dependency inclusion, package scanning or activation of F013's viewer alone must not expose administration endpoints. Explicit activation without its required host adapter fails configuration, with no permissive fallback.

Every administration read and write requires trusted authentication and one host check: `requireAdministrationAccess()`. This grants administration of the whole catalog/definition collection; no read/write/publish role matrix, per-row filtering or permission UI is introduced. A host needing narrower visibility leaves this group disabled until separately specified. Sandbox maps the check to its current CONSOLE_ADMIN policy; reviewers remain denied.

Host security supplies authentication and CSRF enforcement for mutations. UI visibility is advisory. Identity, clock and audit correlation come from trusted host context, never request-body fields. Preserve sandbox 401 for unauthenticated access and 403 for denied administration/CSRF; do not copy the viewer's concealed-404 policy into management. Existing viewer activation/access is unchanged.

### R03 — Preserve the existing HTTP contract

Paths below are relative to the configured host API prefix; sandbox remains `/console/api`. These are the existing operations, not new APIs:

| Method/path | Preserved input/behavior |
| --- | --- |
| GET `/task-types` | `filter`, `afterId`, `limit`; retained catalog page. |
| POST `/task-types` | `code`, `name`, nullable `description`, explicit `selectable`. |
| PUT `/task-types/{id}` | `name`, nullable `description`, `selectable`; expected-version header. |
| GET `/definitions` | `afterId`, `limit`; family page. |
| GET / PUT `/definitions/{id}` | Read family / replace name and description with expected version. |
| GET `/definitions/{id}/versions` | Optional `status`, `afterId`, `limit`; retained versions. |
| GET `/versions/lookup` | Exact `key` and positive integer `version`. |
| GET `/versions/{id}` | Exact version details, stored document and validation evidence. |
| POST `/versions/import` | Raw JSON document; existing import/reuse result. |
| PUT `/versions/{id}/document` | Raw replacement document and expected version. |
| POST `/versions/{id}/validate`, `/versions/{id}/publish`, `/versions/{id}/retire` | Empty body and expected version; existing distinct result types. |
| GET `/versions/{id}/export` | Stored export text and existing attachment/content-type headers. |

Keep the existing DTO field names, null/Optional representation, `items`/`nextAfterId` paging, filters and status semantics. Long IDs/revisions/tokens/cursors remain exact decimal strings; bounded definition/schema version numbers remain integers. Preserve `X-Expected-Lock-Version`, positive IDs, nonnegative versions/cursors, default limit 50/max 100 and existing query/body allowlists. Do not add task-type detail/delete endpoints or change existing exact-route query handling as incidental cleanup.

Raw import/replacement requests retain the 4 MiB UTF-8 transport limit and reach core as text without a controller JSON parse/reserialize round trip. Other management JSON bodies retain 16 KiB limits. Preserve strict Unicode/media/encoding validation, bounded page responses and F004's independent stored-document/diagnostic limits. Definition numbers follow existing core canonicalization, not runtime-history spelling rules: the UI must avoid lossy Number conversion but must not promise that `1.0` remains spelled `1.0` after canonicalization. Exports return the authoritative stored document.

Errors retain `{code, message, diagnostics, correlationId}` and bounded core diagnostics: 400 invalid input/ingestion, 401 unauthenticated, 403 access/CSRF, 404 missing resource, 409 duplicate/stale/conflict/invalid state, 413 request size, 415 media type and sanitized 500 failures. Preserve current category-to-status mappings in ConsoleErrors; do not introduce a blanket 422 mapping for definition limits by copying runtime error handling. Validation failure can be a normal check result with diagnostics, not necessarily a transport error. Never leak request documents, credentials, SQL or stack traces. Advice/serialization remain scoped to shared controllers.

### R04 — Keep mutation orchestration in the host

Shared controllers delegate writes to a required typed host adapter. The sandbox implementation delegates to its existing transactional ConsoleManagement service. Preserve its trusted management context, same-transaction core mutation/audit, audit only for changed results and success only after transaction commit. An audit/serialization/commit failure must not return success. Response delivery can fail after commit; do not move response serialization into a new transaction or promise rollback after such a failure. Do not move the audit table/store or install a library transaction manager/audit service.

Core still owns immutable catalog codes, metadata/selectability rules, expected-version checks, draft/publication lifecycle, canonicalization and validation. Validate may persist validation evidence and therefore stays on the mutation path. Published/retired documents remain read-only; disabling selectability must not invalidate existing published workflows. Preserve no-op and validation-result semantics, without new receipt IDs or idempotency APIs. No automatic write retry, publication, save or token substitution is introduced.

### R05 — Extract existing pages with small host seams

Provide reusable catalog, definition-list/import/lookup, family/version-list and version-editor components, plus their existing definition graph/fragment editor and reload dialog. Host wrappers supply identity inputs, routing, session lifecycle, HTTP/CSRF transport and messages/theme using F013 conventions. No hardcoded `/console` URLs, sandbox-role checks, host service imports, root login/HTTP providers or duplicate Angular runtime belong in the library. Keep the sandbox catalog's sample code default host-supplied; shared controls have no SANDBOX_REVIEW default.

Preserve exact editable text, explicit save/validate/publish actions, stale-token feedback, stored validation reports, saved-versus-unsaved graph distinction, draft-only fragment application and downloads. Failed writes preserve user input and the original token; do not claim rollback or offer blind resubmission when a transport failure leaves the result unknown. A successful write followed by a failed refresh remains a successful write with a separate refresh error. Runtime command recovery remains outside this feature.

Preserve F012's Material reload dialog: only explicit discard reloads dirty text; cancel/Escape/backdrop retain it, and focus/navigation/destruction behavior stays intact. Preserve existing dirty-editor feedback and conflict handling. Current routes have no general route-leave guard; do not introduce one or a new unsaved-change framework as part of extraction. Host/session/input changes invalidate late responses and dispose dialogs/subscriptions without resurrecting private content. Mutation cancellation must not be represented as server rollback.

Use scoped styling and F013's simple message inputs; preserve keyboard/narrow-screen usability. Render metadata/diagnostics as text. Reuse existing pure formatting/layout helpers when necessary without moving runtime command logic into the library.

### R06 — Focused verification and handover

Follow the [focused workflow](../../specification-driven-development.md#focused-verification-and-evidence-reuse). Reuse F012 administration/dialog evidence and completed F013 package/host integration evidence where unchanged. Fresh checks target the extracted routes, access/mutation adapter, new page composition and library exports. Do not repeat runtime idempotency/replay, full workflow execution, broad console acceptance or unchanged engine validation matrices. Keep existing test sources/assertions; select only relevant suites.

One focused sandbox administration browser pass and an extension of the existing minimal library-consumer fixture suffice; do not create another consumer application or replay the same detailed matrix twice. No APOLO availability is required, and sandbox evidence does not establish APOLO compatibility. Stop after relevant checks pass; follow-up changes rerun only affected checks. Required blocked checks remain explicit without reopening prior acceptance.

## Acceptance

| ID | Required result |
| --- | --- |
| AC01 | Viewer-only/dependency-only setup exposes no management routes. Explicit administration activation requires the host adapter; one admin check protects every extracted operation. Reviewer/anonymous and CSRF failures do not invoke mutation delegates. |
| AC02 | Existing paths, payloads, exact tokens, raw-document transport/export, paging and error/diagnostic contracts survive extraction. No new endpoint or validation semantics. |
| AC03 | Writes delegate to the existing host transaction/audit boundary; a representative changed write has its audit, and known failure propagates rather than returning success. No-op/audit rules and core lifecycle guarantees are retained through matched evidence and delegation checks. |
| AC04 | Shared catalog and definition pages work in the sandbox; edit/save/check/publication controls, stale feedback, graph/fragment editing and exact export remain usable. No sandbox implementation or sample defaults leak into shared components. |
| AC05 | Dirty-state integration and Material reload cancellation/discard work across the new component boundary; late responses/dialog callbacks cannot affect a replacement/destroyed view. Denied/session-invalid views clear protected state. |
| AC06 | Built package exports are consumable from the existing generic host fixture; host navigation/messages/transport are respected. Focused suites and the changed package/build/CSP checks pass without duplicate builds or unrelated test campaigns. |
| AC07 | Handover separates fresh results, REUSED evidence and gaps; owned fixtures/processes are cleaned up; final diff/links/whitespace pass. F013 and historical feature evidence are not rewritten to claim unobserved acceptance. |

## Readiness

The extraction scope and simple administration policy are selected here. F014 is READY after reconciliation with completed F013 at `9a82eea` through T02; only gaps affecting those reused boundaries block readiness, not unrelated historical acceptance debt. Do not freeze a parallel design against its changing worktree. Exact class/member names consistent with these contracts are routine implementation choices. Actual APOLO policy/distribution and later graphical designer functionality remain separate work.

## Follow-up — Newest definitions first, 2026-11-07

The sandbox Definitions table requests descending definition-family ID order so the most recently created families appear first across all pages. GET /definitions accepts optional order=ASC|DESC; omission retains ASC for existing consumers, including the separate host start chooser. The existing afterId/nextAfterId fields remain opaque continuation values: DESC starts at zero/omitted and continues with IDs strictly less than the last returned ID. Page bounds, exact decimal IDs, authorization and URLs remain unchanged. Unknown/duplicate order input is 400. Core's existing two-argument DefinitionQuery constructor retains ascending behavior. No schema or workflow execution change.

Acceptance: first DESC page contains the highest IDs; following pages contain successively older families with no skipped/duplicated boundary row, including BIGINT IDs beyond JavaScript safe precision. Existing omitted-order paging remains ascending. Focused query-service, MVC and host-adapter checks cover the changed boundary; unchanged authoring/command guarantees retain prior evidence.
