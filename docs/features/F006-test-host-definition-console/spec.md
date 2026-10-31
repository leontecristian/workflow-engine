# F006 — Test-host definition console

**Status:** READY for implementation. **Revision:** 1 — 2026-10-31. **Owner:** Workflow project owner. **Constitution:** [v3.0.0](../../constitution.md).

## Outcome and scope

Provide a small Angular application in `workflow-test-host` and host-owned HTTP endpoints for manually exercising F002 catalog administration and F004 definition management. Users can inspect persisted definitions, edit JSON, validate, publish, retire and inspect results through the real core services and local database. The owner selected this development console after the F004 review fixes. This preparation delivers specifications only; implementation and runtime results belong in [implementation.md](implementation.md).

[F005](../F005-minimal-single-workflow-execution/spec.md) now separately specifies minimal SINGLE workflow execution. F006 is an independent development-tooling feature, not a renumbering or implementation of F005. It adds no instance start, task completion, workflow execution, visual designer, production APOLO adapter or automated tests.

Authority: [F002](../F002-task-type-catalog/spec.md), [F004](../F004-definition-management-validation/spec.md), [package structure](../../package-structure.md), [source precedence](../../design-sources.md). Apply CON-01–04, CON-08, CON-10–12 and ASM-01/02/03/05/08. Existing lifecycle, canonicalization, error and transaction contracts remain authoritative. The new bounded family read below is the sole planned core API addition.

## Requirements

- **F006-R01 — Development boundary.** Keep exactly two Maven modules. Angular source and all transport/security/audit/fixture code belong to the test host. Console routes, API and capability fixtures require explicit `workflow.console.enabled=true`; they are unavailable by default. Bind the enabled console to loopback and reject incompatible bind configuration. The page identifies itself as a development console whose writes persist in the configured development database.
- **F006-R02 — Trusted host context.** Authenticate every console/API operation with a host-configured development administrator. Use server-side authentication, administrator authorization and CSRF protection; do not accept actor/time/roles from client JSON. Construct F004 context from the principal and server UTC clock, truncated to 100-nanosecond precision. Missing credentials must not enable anonymous access. No credentials are committed or stored by Angular.
- **F006-R03 — Browse actual state.** Provide bounded persisted family, family-version and task-catalog lists, exact version details and exact key/version lookup. Include retired versions and nonselectable catalog entries. Display IDs, lifecycle, lock/content revisions, hashes, audit metadata and stored validation evidence. No unbounded entity dump or browser-only substitute for the database list.
- **F006-R04 — Author without losing data.** Import/paste/upload JSON, replace only DRAFT content with the loaded lock token, and edit family metadata separately. Use a plain text editor; the browser must not parse/stringify the workflow document on save/export, because duplicate properties and exact numeric spellings must reach core intact. No automatic version allocation, save-before-publish, automatic retry, default insertion or silent overwrite. Preserve unsaved editor contents on errors/conflicts; explicit reload may discard them after confirmation.
- **F006-R05 — Inspect validation and lifecycle.** Validate, publish and retire explicitly. Show ordinary failed validation as a completed check with its diagnostics and persisted DRAFT state, not a transport failure. Publication always calls core's fresh validation. Display diagnostics by severity/code/stage/pointer, `detailsOmitted`, and the returned `published` flag. Refresh state/tokens after successful mutations; show published/retired JSON read-only. Export returns the exact stored canonical document in every lifecycle.
- **F006-R06 — Catalog and bounded capability fixtures.** Supply catalog create/update/selectability controls through F002, and the explicitly scoped validation fixtures below. No catalog seeds or application-data writes on startup. Users create `SANDBOX_REVIEW` explicitly through the page before importing the F004 example. Catalog presence alone must never grant capability support.
- **F006-R07 — Transactions and durable audit.** Each HTTP mutation invokes a host transaction facade, calls the core service and persists required management audit in that same transaction. Return HTTP success only after commit. Audit failure rolls back core changes; technical failures never become a successful check result. Persist one audit row for each `changed=true` result, including changed FAILED validation evidence; no-op results produce no audit row. No runtime history/receipt/outbox is fabricated.
- **F006-R08 — Exact, bounded HTTP contract.** Use the routes, encodings and errors below. Keep long IDs/tokens exact across JavaScript, bound requests before materialization, and return sanitized errors. Authentication and exception handling must cover commit-time failures as well as service calls. No automatic write retry or recovery query inside a failed transaction.
- **F006-R09 — Reproducible delivery.** Keep a separate locked Angular/npm build beneath the host and an opt-in packaged-console build. Ordinary Maven packaging must retain the existing tests-skipped workflow without requiring Node. The README must give exact toolchain, credentials configuration, build/start and manual-use instructions. Generated assets/caches/logs remain ignored.
- **F006-R10 — Manual evidence.** Execute the acceptance walkthrough during the subsequent implementation task against explicitly identified development data; record commands/actions, observed persisted results, failures and unperformed cases. No automated test suite, generated test scaffold, scheduled runner or self-running scenario harness is included. Build success alone cannot close behavioral acceptance.

## Host API contract

Base path: `/console/api`. All routes require the development administrator. Collection defaults: `afterId=0`, `limit=50`; limits 1–100, ascending ID, exclusive cursor, nullable `nextAfterId` only when more matching rows exist. Pages contain `items` and `nextAfterId`, with no total-count promise or snapshot consistency across concurrent changes.

All Java `long`/`Long` values, including IDs, lock versions, content revisions and cursors, are decimal **strings** on the wire; parse with checked Long bounds and keep as strings in Angular. Definition version/schemaVersion and page limits are JSON integers. Dates are UTC ISO-8601 strings; optional values are JSON null. Use host DTOs, not global Jackson changes or core entity serialization. DTO field names and nested change/report shapes follow the corresponding F002/F004 records, converting Optional absence to null and numeric long fields as stated. Detailed JSON documents and stored validation details remain strings.

For every existing-object mutation, require `X-Expected-Lock-Version` containing a nonnegative decimal Long. Use it for the core expected version; absence/malformed input returns 400. The family and version tokens are distinct. Client body fields cannot override path identity or this header; reject unknown command-envelope fields. Imports/creates have no expected-token header.

| Method and path | Request / result |
| --- | --- |
| GET `/session` | Authenticated actor and CSRF bootstrap; no secrets. |
| GET `/capabilities` | Fixture mode and immutable descriptors; visibly labelled validation fixtures with runtime execution unavailable. |
| GET `/definitions?afterId=&limit=` | New bounded family page, with `DefinitionView` items. |
| GET `/definitions/{id}` | Family view; absent ID is 404. |
| PUT `/definitions/{id}` | `{name, description}` and expected header → metadata change. |
| GET `/definitions/{id}/versions?status=&afterId=&limit=` | Existing F004 version page; omitted status includes all states. Missing family returns empty page as in core. |
| GET `/versions/lookup?key=&version=` | Exact key/version detail; URL-encoded key, no implicit latest. Missing is 404. |
| GET `/versions/{id}` | Version detail, including canonical document and stored validation details. |
| POST `/versions/import` | Raw UTF-8 `application/json` workflow document → DefinitionChange. |
| PUT `/versions/{id}/document` | Raw UTF-8 `application/json` workflow document and expected header → DefinitionChange. |
| POST `/versions/{id}/validate` | Empty body and expected header → DefinitionCheckResult. |
| POST `/versions/{id}/publish` | Empty body and expected header → DefinitionCheckResult. |
| POST `/versions/{id}/retire` | Empty body and expected header → DefinitionChange. |
| GET `/versions/{id}/export` | Raw canonical UTF-8 JSON attachment; safe filename based on numeric ID, no response wrapper. |
| GET `/task-types?filter=&afterId=&limit=` | F002 page; filter ALL (default), SELECTABLE or NONSELECTABLE. |
| POST `/task-types` | `{code, name, description, selectable}` → TaskTypeChange. |
| PUT `/task-types/{id}` | `{name, description, selectable}` and expected header → TaskTypeChange. No code rename/delete. |
| GET `/audit?afterId=&limit=` | Bounded host audit summaries; no document payloads. |
| GET `/audit/{id}` | Exact host audit record including before/after evidence; missing is 404. |

Successful JSON operations return 200, including creates, no-ops and ordinary negative validation/publication reports. This console does not use 422 for a valid check request whose document fails validation. Export is 200 with `application/json` and attachment disposition. Unknown API paths return an API 404, never the Angular page.

The API error envelope is `{code, message, diagnostics, correlationId}`. `diagnostics` is an empty array unless core supplies bounded document diagnostics; do not duplicate raw request text. Use 400 for invalid arguments/document ingestion, 401 for unauthenticated API calls, 403 for denied role/CSRF, 404 for exact missing resources, 409 for CONFLICT/INVALID_STATE/DUPLICATE_CODE/STALE_VERSION, 413 for transport size limits, and 415 for unsupported media types. HOST_CONFIGURATION, UNSUPPORTED_CANONICALIZATION, STORAGE_FAILURE and unclassified transaction/commit failures return sanitized 500 with a server-generated correlation ID; preserve internal causes in host diagnostics. Do not turn every integrity exception into 409 or expose SQL, credentials or stack traces.

Raw document bodies have a 4 MiB byte ceiling, including chunked requests, and strict UTF-8 decoding; no controller/ObjectMapper parse/reserialize before core ingestion. Other command JSON bodies are capped at 16 KiB. Core's canonical/document/diagnostic limits still apply independently. Reject compressed request bodies for this console rather than expanding an unbounded compressed payload. Authentication/CSRF failure must occur before invoking a mutation.

### Bounded core read addition

Add `WorkflowDefinitionService.listDefinitions(DefinitionQuery(afterId, limit))` returning immutable `DefinitionPage(items: List<DefinitionView>, nextAfterId: Long?)`. Validate nonnegative cursor/1–100 limit using F004 INVALID_INPUT semantics. Query ascending family ID, strict `id > afterId`, fetch limit+1, and return no definition-version payloads. Empty database returns an empty page. Reads follow core's existing read-only REQUIRED boundary; the host authorizes access. No filter, full-text search, latest-version inference or new table/index is required. Host code must not bypass core by importing its repositories/entities.

## Development authentication, capabilities and audit

Use a host-only authenticated local administrator, with username/password supplied through environment configuration and no default password. Enable the console explicitly, require loopback binding, and fail startup if its credentials are missing. Use standard server session/form login and logout with CSRF; API unauthenticated responses stay JSON 401 instead of login HTML. Angular uses same-origin session cookies and the CSRF token obtained through `/session`. No wildcard CORS or remote deployment is included. Development identity is not production APOLO authorization.

**Validation-fixture exception:** F006 deliberately substitutes a host test double for F004's deployed-capability dependency. With console enabled, `workflow.console.capabilities=sample` (default) supplies exactly resolver `sandbox.singleReviewer` and task code `SANDBOX_REVIEW` supporting outcome `COMPLETE`; `none` supplies an empty snapshot. Unknown modes fail startup. The snapshot is immutable until restart. This is a finite fixture to exercise validation/publication, not certification of an executable resolver or task handler, and must be labelled accordingly in the UI and evidence. It must never be enabled in production or treated as evidence for ASM-05/F005. There is no allow-all provider, HTTP registration route or business function invocation. The fixture does not create catalog rows. This explicit test-host exception leaves the F004 production capability requirement intact.

Add a host-owned `wf_test.management_audit` table through the test-host changelog after the core include. Store generated BIGINT ID, trusted actor/time, operation, entity kind/ID, and versioned JSON evidence containing the full returned before/after summaries (catalog snapshots for F002), changed validation outcome/context and the operation correlation ID. Omit full workflow documents and credentials. Retain records, expose bounded authenticated reads, and provide no delete/reset API. F001/F004 changesets and the core master are unchanged.

Persist audit with the same datasource/transaction manager as core. An insert/serialization/size failure aborts the entire host transaction; application logs and in-memory lists cannot substitute. An optional startup-only `workflow.console.fail-audit=true` setting, default false and allowed only with the enabled loopback console, deliberately fails after a changed core result but before audit persistence for manual rollback observation. Display the active failure mode prominently. No request-controlled fault switch or generic SQL/exception endpoint is permitted.

## Pages and interaction

- **Definitions:** persisted family page and Next control, family details/metadata editor, version page/status filter, exact key/version lookup, and Import action.
- **Version:** identity/status/revisions, JSON text editor or read-only document, explicit Save/Validate/Publish/Retire/Export actions, structured latest/stored validation diagnostics and a collapsible sanitized HTTP response. New responses must not leave an old PASSED label attached to edited content. Unsaved text requires an explicit save before validation/publication; the UI never silently saves it.
- **Catalog:** bounded list, explicit create and metadata/selectability edit with current tokens. Explain that capabilities and selectability are separate prerequisites; provide a prefilled, unsaved SANDBOX_REVIEW form for convenience.
- **Audit:** bounded summaries with expandable exact evidence, useful for observing persistence and rollback. Show no-op responses without suggesting an audit row was inserted.

Show loading/empty/error states, disable duplicate submission while a request is in flight, and retain error/conflict details. Use accessible labels and keyboard-operable controls. No component library or graphical workflow editor is required. Any sample JSON inserted into the editor is unsaved and derived from F004's normative example; users choose explicit key/version. Angular displays imported text as text, never executable HTML.

## Acceptance scenarios

| ID | Manual scenario and required result | Requirements |
| --- | --- | --- |
| AC01 | Console disabled: routes/fixtures unavailable. Enabled on loopback: missing credentials fail startup; unauthenticated API returns 401; missing/invalid CSRF on mutation returns 403 without writes. Authenticated UI opens. | R01/02/08 |
| AC02 | With no startup seeds, create SANDBOX_REVIEW explicitly. Create/disable/re-enable and stale-update behavior persists across reload; immutable code stays unchanged. A changed catalog operation has one durable audit row. | R03/06/07 |
| AC03 | Import the F004 sample under a fresh explicitly chosen key/version, browse persisted family/version pages and export. Equivalent import is unchanged; different content with the same identity conflicts. Exact key/version lookup and retired reads work. | R03/04/08 |
| AC04 | Save an identity-valid invalid graph, validate and observe FAILED diagnostics in the response and a fresh read. Correct/save: revision changes and old validation clears. Under sample fixtures and selectable catalog, validate then publish successfully. No runtime row/function execution is claimed. | R04–07 |
| AC05 | With otherwise valid drafts, omitted initialize and `{}` pass the initialization check; `[]` fails structure, and a nonempty initialization object includes WF_DEF_UNSUPPORTED_FEATURE. Verify no invalid draft publishes. | R05/10 |
| AC06 | Disable the catalog entry after PASSED validation; publication rechecks and fails while preserving DRAFT. Re-enable and publish. Restart with capabilities=none and confirm missing-support diagnostics for a new draft; restart sample mode to restore support. | R05/06 |
| AC07 | Published content replacement fails through HTTP; retire preserves exact export/hash. Repeat retirement with current token is unchanged; stale token conflicts. UI actions reflect lifecycle. | R04/05/08 |
| AC08 | Open the same draft in two tabs. Save in one, then submit the old token from the other: 409 and no lost update, with unsaved text retained. Inspect family/version tokens separately. A sequential stale check is not proof of simultaneous racing commits. | R04/07/08 |
| AC09 | With fail-audit startup mode, make a real import/edit/validation change: request fails and a separate fresh read shows no committed core change or audit row. Restart normal mode and repeat successfully. Record both database observations; never reset existing data. | R07/10 |
| AC10 | Inspect string-encoded IDs/tokens and untouched document text. Import JSON containing an exact large designer number; export preserves its value. Duplicate-key input reaches core and is rejected. Oversized/chunked requests fail within bounds; errors expose no internals. Record boundary cases actually exercised. | R04/08 |
| AC11 | Browse more records than the selected page size for families, versions, catalog and audit; traverse cursors with no skipped/duplicated rows in a static dataset. API unknown/missing reads retain the stated 404/empty-page behavior. | R03/08 |
| AC12 | Build backend and packaged Angular console; open it from the host, reload its routes, and restart without duplicated seeds/migrations. Existing core checksums remain unchanged; owned temporary processes are stopped and retained development data identified. | R01/09/10 |

The F004 null-constraint-name fix also needs a controlled provider-error observation when feasible: a null/unknown name must not cause NullPointerException, and unrecognized storage failures remain sanitized 500/HOST_CONFIGURATION with rollback. Do not corrupt schema or add a public fault-injection endpoint to manufacture this case. If no controlled manual reproduction is available, record **NOT RUN** separately; AC09 demonstrates audit rollback, not this Hibernate branch. Full F004 race, resource-bound and production compatibility guarantees remain separate evidence obligations.

## Readiness and remaining decisions

No unresolved business requirement blocks this slice. During implementation, select and pin an Angular/Node/npm combination using the official compatibility documentation; record versions and a lockfile rather than freezing a guessed latest version here. The UI is a local management console, so this does not decide the later production designer's framework version or graph library. Implementation may refine internal classes/build-plugin choices within the contracts above. A contract change must reconcile this spec, plan and tasks before dependent implementation.
