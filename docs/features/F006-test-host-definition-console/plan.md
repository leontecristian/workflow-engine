# Plan — F006 Test-host workflow console

Implements [specification revision 3](spec.md), retaining revision 2's execution scope and allowing isolated unit tests under the clarified owner policy. Track progress in [tasks.md](tasks.md) and actual evidence in [implementation.md](implementation.md). No implementation is claimed.

## 1. Baseline, dependency and toolchain (R01, R09–10)

Read repository guidance, F002/F004/F005 contracts and actual source. Preserve other-agent work. F005 owns execution; wait for its required APIs rather than inventing stub implementations in the host. Management infrastructure can progress independently, but runtime compilation/acceptance is a dependency gate. Reconcile material API differences before dependent code.

Retain two Maven modules and core's ordinary JAR. Angular lives at `workflow-test-host/frontend/` with package.json/package-lock.json. Select/pin a supported Angular/Node/npm combination from official compatibility sources during implementation; record versions/URLs. Use standalone components, hash routing and plain CSS, adding focused unit-test support where needed rather than unused generated tests. This documentation revision requires no build/database action.

## 2. Core read addition and host packages (R03, R08, R15)

F006's only core addition is DefinitionQuery/DefinitionPage in dto.definition, WorkflowDefinitionService.listDefinitions and its internal bounded repository query. Use validated ascending ID/limit+1 pagination with no version JSON or unbounded load. Execution and runtime reads use F005 public services/DTOs/exceptions; no host imports of core entities/repositories/helpers.

Under `io.github.leontecristian.workflow.testhost`, assign:

| Package | Responsibility |
| --- | --- |
| `configuration.console` | Gate, loopback/credentials, session/CSRF/roles, immutable capability/failure settings. |
| `controller.definition`, `controller.tasktype`, `controller.audit`, `controller.console` | Management/session routes and transport. |
| `controller.runtime`, `controller.sandbox`, `controller.event` | Runtime commands/reads/replay, requests and event delivery. |
| `dto.definition`, `dto.tasktype`, `dto.audit`, `dto.console`, `dto.runtime`, `dto.sandbox`, `dto.event` | Explicit wire records with decimal-string Longs. |
| `service.definition`, `service.tasktype`, `service.audit` | Management transaction facades and audit writing/reads. |
| `service.runtime` | Nontransactional coordinator, separate transactional facade, authorization and pinned task-contract projection. |
| `service.sandbox` | Request creation/query/link callback and actual assignment resolver. |
| `service.event` | Mandatory recorder, outbox/inbox reads and manual dispatcher. |
| `repository.audit`, `repository.sandbox`, `repository.event` | Host JDBC persistence on the shared transaction manager. |
| `service.console`, `exception.console` | Trusted context/capability composition and sanitized failures. |

Use concrete services and only the genuine F005 SPIs. JDBC must participate in core's effective JpaTransactionManager connection; declare imported dependencies directly, never create another manager/autocommit connection. Keep core scanning unchanged; host JDBC tables need no new JPA scan.

## 3. Security and exact transport (R01–02, R08)

Use host-only Spring Security under the existing Boot baseline. Enabled console requires distinct external admin/reviewer credentials, explicit role mapping and loopback binding. Usernames satisfy F005 actor bounds. Resolver assignment uses the configured reviewer, never a caller-selected identity. No impersonation control or automatic reassignment after configuration changes.

Use session/form login/logout and CSRF with JSON API 401/403 handlers and same-origin Angular XSRF bootstrap through /session. Only necessary login/bootstrap resources are public. Disabled mode exposes no controllers/providers and blocks packaged console static resources; account for Security auto-configuration to preserve ordinary host startup. No secrets in assets/logs/localStorage and no wildcard CORS.

Reviewer read authorization uses F005 stored assignment, including completed tasks, not resolver output/business correlation/command IDs. Admin read access does not override completion assignment. Preserve authorization on replay without prechecking lifecycle/staleness. Project the pinned outcome label/comment requirement server-side from authorized F005 task/instance views and exact F004 canonical document; do not disclose full definitions to reviewers or create another interpreter.

Preserve strict UTF-8 streamed limits and untouched raw import/replace JSON. Other envelopes have 16 KiB limits and duplicate/unknown-field rejection. Reject input/result/actor fields. Longs remain strings in browser/HTTP; UUIDs are checked. Distinguish request/family/definition-version/instance tokens. Unknown API paths never receive the Angular shell.

## 4. Host persistence and migrations (R07, R11, R14)

Use the test-host master after the unchanged core include. Revision 1 migrations are only planned at this baseline; if already applied elsewhere, preserve their identities/content and append changesets. Never reset data, rewrite applied checksums or MARK_RAN unexpected objects.

Retain `changes/001-console-management-audit.xml`, author `workflow-test-host`, with stable logical path and changesets `f006-001-create-wf-test-schema` / `f006-002-create-management-audit`. Add `changes/002-console-runtime-sandbox.xml` with `f006-003-create-sandbox-request`, `f006-004-create-workflow-event-outbox`, `f006-005-create-workflow-event-inbox`. No core master/F001/F004 edits. Host startup creates infrastructure even with console disabled but inserts no application rows; document this.

Physical contracts, all under `wf_test`:

| Table | Columns, keys and checks |
| --- | --- |
| `management_audit` | id BIGINT IDENTITY PK; occurred_at DATETIME2(7), actor_ref NVARCHAR(200), operation VARCHAR(32), entity_kind VARCHAR(32), entity_id BIGINT>0, correlation_id UNIQUEIDENTIFIER, evidence_json NVARCHAR(MAX), all NOT NULL. Kind DEFINITION/DEFINITION_VERSION/TASK_TYPE. JSON CHECK ISJSON=1 and DATALENGTH<=524288. |
| `sandbox_request` | id BIGINT IDENTITY PK; name NVARCHAR(200), created_by NVARCHAR(200), created_at DATETIME2(7), lock_version BIGINT>=0 all NOT NULL; instance_id BIGINT nullable, positive when present. Unique filtered index on non-null instance_id. Name/creator/time immutable, instance link write-once. No core FK or edit/delete API. |
| `workflow_event_outbox` | id BIGINT IDENTITY PK; event_id VARCHAR(40) BIN2 UNIQUE; instance_id BIGINT>0, history_sequence BIGINT>0, command_id UNIQUEIDENTIFIER, event_type VARCHAR(60), occurred_at DATETIME2(7), event_json NVARCHAR(MAX), delivery_count BIGINT>=0 all NOT NULL; delivered_at DATETIME2(7) nullable. Unique(instance_id,history_sequence); JSON CHECK ISJSON=1 and DATALENGTH<=262144. Check pending = null delivered_at/count 0, delivered = timestamp/count>=1. Index(delivered_at,id). |
| `workflow_event_inbox` | id BIGINT IDENTITY PK; event_id VARCHAR(40) BIN2 UNIQUE, received_at DATETIME2(7), event_json NVARCHAR(MAX), all NOT NULL; JSON CHECK ISJSON=1 and DATALENGTH<=262144. Immutable accepted payload/first-receive time. |

All core references are logical IDs; no APOLO FK/schema cycle. Enforce Unicode/byte bounds before SQL. Outbox JSON is the full versioned F005 event with Long strings, capped at 128 KiB each in UTF-8/UTF-16 bytes. Management evidence schema 1 is `{schemaVersion:1, operation, before, after}`; trusted actor/time/correlation are columns. Omit full definition JSON/credentials. Request creation has immutable creator/time; runtime actions use F005 history, not duplicate management audit.

Host lists use ascending generated ID/limit+1, omitting payloads; exact reads include bounded evidence strings. Apply a 1 MiB UTF-8/UTF-16 response cap with continuation after the last emitted item. Retain rows; no cleanup/reset endpoint. No task result column/table or engine state copies.

## 5. Real adapters and command transactions (R06–07, R11–14)

Register one capability provider and real SingleTaskAssignmentResolver in enabled sample mode. SANDBOX_REVIEW/COMPLETE descriptors match the actual task page/completion facade. sandbox.singleReviewer verifies the same-transaction request link by allocated instanceId/business reference, then returns taskKey REVIEW assigned to the configured reviewer. No external side effects. none mode removes both descriptors/resolver; recorder remains real. Do not retain the old synthetic provider.

Management facades retain F002/F004 changed-result audit behavior. Runtime uses a nontransactional HTTP coordinator and a different Spring bean with REQUIRED command transactions. Derive USER actor/time/correlation server-side. Start builds SANDBOX_REQUEST business correlation and the exact host intent in spec. Its callback performs `UPDATE ... SET instance_id=?, lock_version=lock_version+1 WHERE id=? AND instance_id IS NULL AND lock_version=?`; detect overflow/zero rows. Authorize request access before invoking core, but never reject stale/already-linked request state ahead of F005 receipt resolution. No request prelock before F005's definition lock.

Complete preserves original instance token and nullable/empty comment. Core owns assignment/lifecycle/history; host owns access/wire mapping. Return only after facade commit. On eligible concurrency failure, including the known sandbox-link conflict, catch outside the transactional proxy after rollback and invoke findReplay once in a fresh read-only transaction. A miss leaves the conflict; a mismatched receipt is COMMAND_ID_CONFLICT. No mutation retry or self-invoked transaction proxy. Translate only the known sandbox-link conflict marker through any core wrapper to 409; other callback failures stay 500.

Recorder uses MANDATORY participation and inserts the complete immutable batch before acknowledging. Duplicate event IDs from a new recording are errors, not silent upserts; receipt replay never calls it. Failure flags throw after the defined writes; post-result failure applies only to new results. All flags are startup-only, default false, visible and loopback-gated. Independent reads must establish rollback.

## 6. Explicit local delivery (R14–15)

Dispatcher requests are admin-only, separate REQUIRED host transactions after workflow commit. Select a bounded pending outbox batch ordered by id with transaction-held update locks; exact redelivery takes the same row lock. Serialize competing requests in that order; no automatic deadlock retries or concurrent unchecked inbox insert races.

For each locked event, insert inbox if absent; if present require exact identical serialized event, then report duplicate without another effect. In the same transaction set the first delivered_at and increment committed delivery_count; retain original inbox timestamp/payload. Failure rolls back inbox and marker together. Counters describe committed deliveries, not all failed attempts. Compare deterministic strings/bytes exactly, not case-insensitive SQL text equality.

Restart retains pending events for manual dispatch. There is no scheduler, network notification or broker. Document that this durable local sink does not prove distributed exactly-once delivery. Redelivery never invokes runtime commands.

## 7. Angular pages (R03–06, R09, R11–15)

Keep hash routing under /console and same origin. Retain definition/catalog/audit pages, raw textarea/file import/export and diagnostics. Add role-aware navigation, sandbox requests/start, exact instance/task/activity/history/submission views, My tasks, command results/recovery and outbox/inbox pages. All state comes from APIs; no fake seeded/browser execution state.

Start chooses an exact published version and request. Task detail gets the pinned contract and current instance token; form contains outcome/comment only. Distinguish current state from original command snapshots. Catalog disable/retired pinning rules apply to UI selection filters too. Event dispatch/redelivery is explicit.

Keep attempted UUID/payload/original token in memory. A transport failure displays unknown outcome with Find result/Retry; neither silently regenerates UUID nor refreshes the frozen token. Provide envelope copy/download and explicit paste/import into replay-only recovery for full reloads, with no secrets. Clear buffers on logout/account change. New intentional action gets a new UUID; disable duplicate in-flight submission. Refresh current state without replacing the original result display.

Use separate authenticated browser sessions for admin/reviewer; no actor selector. Safely render all text, preserve unsaved edits/errors, and do not store credentials/documents in localStorage. Failed validation, failed execution and unknown transport outcomes have distinct displays.

## 8. Packaging, verification and handover (R09–10)

Use opt-in Maven console-ui profile: npm ci, npm run build, copy generated assets into target/classes/static/console before packaging. Pin plugins/toolchain. Ordinary Maven builds require no Node and must remove stale console assets from prior profile builds. Keep generated files out of tracked src/main/resources; ignore node_modules/.angular/dist. README covers two-account settings, failure flags, native SQL Server launch and manual use.

Add focused backend/frontend unit coverage for exact Long transport conversion, rejected/unknown fields, deterministic host-start fingerprints, authorization decisions using test doubles, command UUID/token preservation after lost responses and sanitized error/replay handling. Select tests that establish behavior rather than mirror implementation; no database, migrations, running host or live HTTP dependency. Mocked transaction/recorder tests cannot establish real commit/rollback/locking guarantees.

Inspect Maven/frontend test discovery before execution, preserving the existing database-backed host context test without running it. Run explicitly selected unit-only suites and record their commands/results; do not assume test naming or skipITs excludes every database test. Embedded databases and Testcontainers remain database integration tests requiring separate authorization. No automatic database scenario harness is selected.

Verify JDK 21; run `./mvnw.cmd '-Dmaven.test.skip=true' package` and `./mvnw.cmd '-Dmaven.test.skip=true' -Pconsole-ui package` for packaging, including successive profile/default builds and JAR inspection. These packaging commands do not replace the separately executed unit suites. Broader test/package/verify or npm/ng test commands are permitted only after confirming their selected suites remain within the authorized unit-only scope.

For implementation's authorized manual verification, identify actual localhost:1433/apolo_workflow_dev target and existing data/checksums. Execute AC01–AC22 with explicitly created development records. Record safe actor refs, definition/request IDs, UUIDs, before/after state/history/receipts/outbox/inbox and final tokens. No passwords, database reset or silent overwrite of existing catalog data. Missing F005 APIs is a dependency gap, not permission to mock execution.

Use independent post-rollback reads for each failure flag; 500 alone proves nothing. Distinguish true overlapping requests from sequential stale rejection. Observe link visibility before resolver, no effects on replay, retired/terminal recovery, pending-event restart and duplicate inbox suppression. Deliver only after workflow commit. Retain and identify development rows and ignored logs.

Map observed scenarios to F005 acceptance only where supported. Null-name provider failures, malformed third-party providers, outer commit failures, larger-graph step boundaries and production broker recovery remain explicit gaps unless separately exercised. Local inbox behavior does not certify other host integrations.

Record temporary process identities, stop only owned processes unless asked to keep them open, and restore normal failure settings. Reconcile four documents/navigation with exact revisions/toolchains/migrations, results and retained data; check links/whitespace. No unrequested commit/push. This redesign itself runs no build, application or database operation.
