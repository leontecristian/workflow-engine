# F015 — Runtime task, command and recovery extraction

**Status:** IMPLEMENTED, revision 2 — 2026-11-07. **Owner:** Workflow project owner. **Constitution:** 6.0.0. **Authorization:** owner implementation request of 2026-11-07 supersedes preparation-only authorization.

## Purpose and scope

Extract the remaining generic runtime interaction around F013's instance/history/graph viewer: instance monitoring, variables, task/activity reads, the complete task comment/outcome UI, completion HTTP mapping, frozen-command handling and recovery UI. The sandbox consumes these through workflow-ui and workflow-web while retaining its business operations. F014 administration remains unchanged.

Revision 2 includes task execution controls and command/recovery extraction following owner clarification. Shared recovery supports both existing completion and host-originated start envelopes. Sandbox request/start forms and start HTTP parsing remain host-owned; their commands participate in shared recovery through a narrow host adapter. No new generic start API/form, engine behavior, schema, APOLO source, authoring capability, structured-result schema support or runtime operation is introduced.

Authority: [constitution](../../constitution.md) C03/C06/C09/C10; [constraints](../../constraints.md) CON-01–04/06–08/10/12/14; [assumptions](../../assumptions.md) ASM-01–04/06/09/10/11; [integration architecture](../../architecture/workflow-ui-web-integration.md). Changed completion transport/delegation and recovery need focused fresh evidence; unchanged engine algorithms and host transaction bodies do not require broad reacceptance. Follow [design-source precedence](../../design-sources.md); older scaffold descriptions do not override the completed F013/F014 code baseline.

## Complete route/component inventory at f686284

UI paths below are relative to the existing console Angular routes. Components reside beneath `workflow-test-host/frontend/src/app/pages/` unless already shared. HTTP paths below are relative to `/console/api`; hosts still choose activation/base paths.

| UI route / source component | Disposition |
| --- | --- |
| `definitions`, `definition/:id`, `version/:id`, `catalog`; `definitions/{definitions,family,version}`, `catalog/catalog` | Already shared by F014; retain thin host wrappers. |
| `instance/:id`; `runtime/instance` | F013 metadata/graph already shared. Move variable panel, paging and exact export in F015; keep sandbox service-effects panel and route composition host-owned. |
| `history/:id`, `history-event/:id/:sequence`; `runtime/{history,history-event}` | Already shared by F013; retain wrappers. |
| `instances`; `runtime/instances` | Move generic list/filter/paging into UI; host supplies authorized monitor reads and route navigation. |
| `my-tasks`, `instance-tasks/:id`; `tasks/{assigned-tasks,instance-tasks}` | Move read lists/paging/navigation into UI. Trusted identity and result visibility remain host-owned. |
| `task/:id`; `tasks/task` | Move the complete task UI: inspection, comment/outcome form, generic pinned-contract validation, command freezing/submission and recovery navigation. Host adapters own transport, identity, authorization, business validation and transactional execution. |
| `activity/:id`, `submissions/:id`; `runtime/{activity,submissions}` | Move read presentation/paging/navigation into UI. No new result forms or aggregation. |
| `requests`, `request/:id`; `sandbox/{requests,request}` | Stay host-owned business forms/start orchestration. Record their existing start envelopes in shared recovery through a host start-envelope adapter; sandbox request fields never become mandatory shared fields. |
| `commands`; `commands/commands` | Move command selection, frozen buffer, copy/export/restore, explicit retry/find-result, observations and separate current-state refresh to UI. Completion and host start commands share this UI; host supplies dispatch and start-envelope validation. |
| `events`, `event/:id`, `inbox`, `inbox-event/:id`; `events/{outbox,event,inbox,inbox-event}` | Stay host-owned: these represent the sandbox durable-handoff/delivery stores and local dispatch/redelivery demonstration. No generic operations framework selected. |
| `audit`, `audit-event/:id`; `operations/{audit,audit-event}` | Stay host-owned management audit storage/policy and presentation. Generic history remains F013. |
| `capabilities`; `operations/capabilities` | Stay host-owned sample configuration/diagnostics, not a portable capabilities API. |
| empty-path redirect, wildcard `not-found/not-found`, shell/menu/login | Stay host-owned application composition/session/security. |

| HTTP operation / current controller | Disposition and preserved input |
| --- | --- |
| GET `/instances`; ConsoleRuntimeController | Move mapping; `status` defaults ACTIVE, explicit ALL removes status filter; `afterId`, `limit`. Host-authorized complete page. |
| GET `/instances/{id}/variables` | Move mapping; `afterKey`, `limit`, optional `expectedLockVersion`; preserve snapshot token, exact values and `nextAfterKey`. |
| GET `/tasks/mine` | Move mapping; `afterId`, `limit`; actor comes from host, never request input. |
| GET `/instances/{id}/tasks` | Move mapping; `afterId`, `limit`; host supplies authorized page under R02. |
| GET `/tasks/{id}`, `/tasks/{id}/contract`, `/tasks/{id}/submission` | Move mapping; preserve exact-task authorization, pinned human-node contract and nullable absent submission. Contract still includes task/instance IDs and instance lock version. |
| GET `/activities/{id}`, `/activities/{id}/submissions` | Move mapping; submission query uses `afterTaskId`, `limit`; preserve authorized historical activity evidence. |
| GET instance metadata/graph/history/exact event | Already shared F013; no duplicate controller or parallel access framework. |
| Catalog/definition HTTP operations | Already shared F014. |
| POST `/instances/{instanceId}/tasks/{taskId}/complete`, `/commands/complete/replay` | Move mapping into a separately activated completion-command group; delegate execute/replay to required host operations, preserving payload/token/context/access behavior. |
| POST `/instances/start`, `/commands/start/replay` | Stay host-owned: SandboxStart parsing, request association, saved request-data binding and transactional/replay policy are business-specific. Shared recovery dispatches through the host adapter without interpreting business fields. |
| GET `/instances/{id}/service-effects` | Remains host-owned SandboxServiceEffectStore inspection. |
| GET/POST `/sandbox-requests`, GET `/sandbox-requests/{id}`; ConsoleSandboxController | Stay host-owned. |
| GET `/events`, `/events/{id}`, `/inbox`, `/inbox/{id}`; POST `/events/dispatch`, `/events/{id}/redeliver`; ConsoleEventController | Stay host-owned delivery mechanism/diagnostics. |
| GET `/audit`, `/audit/{id}`; ConsoleAuditController | Stay host-owned. |
| GET `/session`, `/capabilities`, console redirects/login; ConsoleSessionController and security configuration | Stay host-owned, including logout/CSRF/filter behavior. |

Supporting ownership: share DTOs, rendering/exact-text helpers, read state and generic command buffer/recovery logic. Extract completion envelope validation; keep start-specific field validation/serialization in the sandbox adapter. ConsoleSession, ConsoleRuntimeAccess policy, ConsoleRuntimeCoordinator, ConsoleRuntimeTransaction, request links, audit/outbox/inbox stores, service actions, assignment/capability registration and infrastructure remain host-owned. Reuse F013/F014 presentation/navigation; do not move entire host directories indiscriminately.

## Requirements

**R01 — Explicit read activation.** Add one explicitly imported runtime-read group using F013/F014 conventions. A dependency or broad component scan alone exposes nothing. Missing required host integration fails configuration. Preserve sandbox URLs, parameter rejection, response fields and error envelope/correlation. Viewer-only and administration-only consumers remain usable independently. No command route is activated by this group.

**R02 — Authorization and paging.** Host policy determines monitor access, current actor, exact task/activity access, variable access and submission visibility. Sandbox monitor stays administrator-only (403 denial before monitor query parsing); unauthorized/missing exact resources retain concealed 404 and historical assigned access. Task-list visibility does not grant access to all submissions or imply completion permission. Shared controllers never accept an actor identity from the client or apply sandbox roles.

Host integration returns authorized pages for the three policy-sensitive lists (monitor, assigned tasks, instance tasks); shared mapping does not fetch an unscoped page and silently filter it. Preserve sandbox behavior deliberately: its instance-task adapter retains existing bounded source-page filtering and source continuation. A page can have zero visible rows and a nonnull continuation; the UI must still allow Next. Do not refill pages, expose totals, drop continuation or reinterpret an empty page as exhaustion. Wire-size truncation resumes after the last returned row so visible rows are not skipped. This specifies the existing sandbox policy, not a production authorization-query strategy. Other hosts must supply policy-correct page selection; do not introduce tenant query engines or core repository APIs in F015.

**R03 — Existing read contracts and errors.** Preserve decimal-string BIGINT IDs/versions/sequences, nullable fields, pinned outcome labels/comment requirements, current accepted submission shape and timestamp serialization. Task contract is drawn from the instance's pinned definition/activity node, not current catalog metadata or newest definition. Structured task results remain future specification work. Preserve list bounds and bounded wire behavior from ConsoleWire/core; no new unbounded enumeration. On existing query-validating list/variable routes, invalid/unknown/duplicate query input remains 400. Exact task, contract, submission and activity routes currently do not validate query parameters; preserve that behavior rather than adding incidental rejection. Denied authentication remains host-controlled, missing/concealed reads 404, stale variable snapshot 409, exceeded bounds 422, and unexpected failures sanitized 500 with trusted correlation. Do not turn read failure into an empty successful response.

**R04 — Variables and lifecycle.** Retain initialized/uninitialized/null distinctions, scalar text and exact JSON numeric precision/scale, original-response export and lock-version-aware paging. Never reconstruct export from lossy parsed JS numbers. Components accept opaque `sessionScope`, IDs and messages using existing conventions; null scope clears private state. Changed scope/ID/filter, refresh, destruction and denial invalidate obsolete work; late responses cannot repopulate stale content or overwrite newer reads. AbortSignal supplements generation guards. Stale paging clears invalid data and offers explicit restart; no automatic command or read-retry framework.

**R05 — Complete task interaction.** Shared task UI owns existing comment/outcome controls, task/contract/submission loading, busy/feedback states and generic validation: OPEN task, outcome from pinned contract, Java-compatible blank-comment check when required and current 2000-character UI bound. Preserve comment text exactly (empty becomes null as today). Backend validation remains authoritative; read access never grants completion rights. Freeze UUID, instance/task IDs, outcome, comment and current expected instance token before submission. Retry uses the saved envelope, never a fresh token, UUID or current form state. Disable controls without a current loaded context; ID/session changes discard pending form state. A read-only consumer needs no command provider and renders no execution controls. Command adapters may target host business endpoints instead of shared generic mappings. Shared UI owns no business policy or role-based override.

**R06 — Focused delivery and composition.** No new Maven module, standalone application, permissions system, global mapper/security chain, migration, engine service behavior or authoring feature. Remove duplicate extracted read/task/recovery implementations; retain host adapters, unrelated endpoints and tests. Variable inspection composes with F013 viewer and a host service-effects sibling. Reuse navigation/messages/theme/session conventions, adding intents only for actual runtime/recovery destinations. No console URLs in shared components. Hosts control destinations and may return null; absent navigation must not erase a result. Reuse applicable evidence and verify changed boundaries under P04.

**R07 — Optional completion HTTP mapping.** Read activation remains independent. A separate explicit command-group import requires host execute and replay-only operations, with no direct shared-controller call to core execution and no permissive default. Preserve completion path/body (`commandId`, `outcomeKey`, nullable `comment`; path instance/task IDs), replay path/body (also `instanceId`, `taskId`) and `X-Expected-Lock-Version`. Reuse bounded parsing, UUID/decimal checks and unknown-body-field rejection; preserve missing/null handling and error categories. Execute returns WorkflowCommandResult; replay returns `{found, result}` with null result if absent and must never execute a missing command.

Host creates trusted actor/time/correlation and owns authorization, assignment/business checks, transactions/audit/durable handoff and receipt recovery. Sandbox adapter delegates to existing ConsoleRuntimeCoordinator.complete and ConsoleRuntimeTransaction.replayComplete, preserving replay's access.task check and transaction proxies. Do not add an exact-read precheck before normal completion: receipt-before-current-state validation and core assignment enforcement, including no administrator bypass, remain intact. The existing coordinator's post-rollback receipt lookup stays host-owned; it is not an automatic business-command retry. Preserve status/code/correlation mappings, including conflict409, validation422 and sanitized500. Security/CSRF remain host-owned.

**R08 — Shared frozen-command recovery.** Move the existing in-memory buffer and recovery state/UI into workflow-ui, scoped to a host-provided login/session identity. Entries survive route destruction, not logout/session replacement or explicit clear; no browser persistence, background retries or new queue. Built-in completion envelope validation preserves the exported shape and immutable content. Same UUID with changed kind/body/token is rejected. Copy/download/restore preserves decimal strings, nullable comments and raw input text; malformed/duplicate-field input is rejected under existing rules. Restore/select never executes a command.

Support the two current kinds, complete and start, without a generic plugin registry. For start only, the host supplies body validation/freezing/identity handling plus execute/replay dispatch. Shared shell validates envelope kind/shape and command ID; it does not hard-code requestId, expectedRequestVersion or requestData policy. Sandbox start adapter retains existing field/token rules, raw inputs and HTTP serialization. Previously exported sandbox start and completion envelopes remain importable without conversion. Without a start adapter, reject start restore before insertion/dispatch while retaining completion support. Host request form records start commands through the same shared buffer before dispatch.

Explicit retry-original submits the exact frozen request. Find-result performs replay-only lookup and honestly reports not-found. Preserve committed/replayed/rejected/unknown observations: transport failures and 5xx cannot prove rollback. A committed result stays visible if subsequent current-state refresh fails. Original evidence and current instance/open-task views are separately labelled; current views never replace saved inputs or returned evidence. No automatic retry after cancellation, timeout or network failure.

Capture session generation and command ID per operation. Logout, session replacement (including same-user reauthentication), clear, selection changes and destruction invalidate obsolete presentation work. A response for command A may be recorded against A in the same live session but cannot overwrite selected command B. Pending restore/copy/read work is also guarded. Session changes clear sensitive forms/envelopes/observations/current views; actor names alone are insufficient generation keys. Aborting a request does not imply rollback.

## Acceptance

| Scenario | Required observation |
| --- | --- |
| AC01 — Activation/composition | Read import registers only GET mappings; separate command activation requires its adapter. F013/F014 groups coexist without collisions or implicit exposure. |
| AC02 — Access and paging | Host authorization controls monitor/exact/variable/submission reads and trusted assigned identity. Concealed denial preserved; empty filtered pages retain continuation; byte truncation does not skip authorized rows. |
| AC03 — Exact inspection | Existing DTO/null/error behavior, pinned contract, exact variable display/export and stale snapshot handling survive extraction. |
| AC04 — Component lifetime | Session/ID/filter changes, denied and delayed responses cannot display another context's data; explicit loading/error/empty/next states remain usable. |
| AC05 — Sandbox composition | Complete task UI and recovery use shared components/adapters with no duplicate sandbox form/buffer/recovery logic. Business start forms and transactions stay host-owned. |
| AC06 — Consumer/build boundary | Existing NgModule fixture mounts task/recovery with host transport/messages/navigation and a command double; read-only composition needs no command provider. Public imports and packaged assets respect existing budgets/CSP. |
| AC07 — Completion boundary | Exact payload/token/context, CSRF/access and replay-only absence hold through new mapping. Host transaction/receipt behavior has focused integration evidence. |
| AC08 — Frozen recovery | Original envelopes round-trip, conflicting UUID inputs are rejected, retry reuses original values and find-result never executes. Start fields remain host-owned. |
| AC09 — Uncertainty/isolation | Unknown outcomes remain recoverable; success survives refresh failure; late responses cannot leak across sessions or replace another selected command. |

## Readiness and deferred work

T02 reconciled actual APIs/evidence and fixed typed adapter signatures before parallel edits; see plan.md. The owner implementation request authorized the delivered revision 2 scope. No material business requirement remains unresolved for this extraction.

Deferred: new generic start API/form independent of host business objects; production authorization-aware query integration; APOLO compatibility and business forms; designer and new engine capabilities. Permanently host-owned items are not extraction backlog merely because they display workflow-related information.
